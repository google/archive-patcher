// Copyright 2016 Google Inc. All rights reserved.
// 
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.archivepatcher.tools.transformer;

import java.io.ByteArrayInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import com.google.archivepatcher.Archive;
import com.google.archivepatcher.compression.JreDeflateParameters;
import com.google.archivepatcher.meta.CompressionMethod;
import com.google.archivepatcher.parts.CentralDirectoryFile;
import com.google.archivepatcher.parts.CentralDirectorySection;
import com.google.archivepatcher.parts.LocalSectionParts;
import com.google.archivepatcher.tools.diviner.CompressionDiviner;
import com.google.archivepatcher.util.MiscUtils;

// TODO: Fix class to support archives with holes in them, e.g. extra data that
// is not listed in the central directory.

/**
 * Given an archive and data from the {@link CompressionDiviner}, transforms
 * archives into a space where all successfully divined entries are stored in
 * uncompressed format. The original offsets and lengths for each entry are
 * recorded in a {@link TransformationRecord} so that the original archive can
 * be trivially reconstructed. Apart from these lengths and offsets, every
 * other byte in the original archive is left unchanged - including extras,
 * file comments, file order, and so on. The transformation can be reversed by a
 * {@link CompressTransformer}.
 */
public class UncompressTransformer {
    /**
     * Transform the specified archive using the specified directives file from
     * the {@link CompressionDiviner}.
     * @param inputArchiveFile the archive to transform
     * @param divinerFile the diviner information to use
     * @param outputArchiveFile where to write the transformed archive
     * @param outputRecordFile where to write the {@link TransformationRecord}
     * @return a record of the transformation process
     * @throws IOException if anything goes wrong reading or writing
     */
    public TransformationRecord transform(File inputArchiveFile,
        File divinerFile, File outputArchiveFile, File outputRecordFile)
        throws IOException {
        final Archive archive = Archive.fromFile(
            inputArchiveFile.getAbsolutePath());
        final Map<String, JreDeflateParameters> directivesByPath =
            CompressionDiviner.parseDirectives(divinerFile);
        DataOutputStream outputStream = new DataOutputStream(
            new FileOutputStream(outputArchiveFile));

        TransformationRecord result = null;
        try {
            result = transform(archive, directivesByPath, outputStream);
        } finally {
            try {
                outputStream.flush();
            } catch (Exception ignored) {
                // Nothing
            }
            try {
                outputStream.close();
            } catch (Exception ignored) {
                // Nothing.
            }
        }

        // Calculate SHA256 for original and transformed archive.
        // It's probably nearly as efficient to do this here as it is to do it
        // inline, at least to a first order approximation, because inline the
        // operations are done in small units wheres here there will be a large
        // read buffer and the blocks are probably already in cache anyways.
        result.recordOriginalSHA256(MiscUtils.sha256(inputArchiveFile));
        result.recordNewSHA256(MiscUtils.sha256(outputArchiveFile));

        DataOutputStream recordOut = new DataOutputStream(
            new FileOutputStream(outputRecordFile));
        try {
            result.write(recordOut);
        } finally {
            try {
                recordOut.flush();
            } catch (Exception ignored) {
                // Nothing
            }
            try {
                recordOut.close();
            } catch (Exception ignored) {
                // Nothing.
            }
        }
        return result;
    }

    /**
     * Perform the transformation.
     * @param inputArchive the archive to process
     * @param directivesByPath the directive file with instructions for
     * recompression
     * @param outputArchiveStream the stream to write the transformed archive to
     * @return the result of transformation
     * @throws IOException if anything goes wrong reading or writing
     */
    private TransformationRecord transform(Archive inputArchive,
        Map<String, JreDeflateParameters> directivesByPath,
        DataOutput outputArchiveStream) throws IOException {
        TransformationRecord result = new TransformationRecord();
        CentralDirectorySection cds = inputArchive.getCentralDirectory();
        CountingDataOutput countingOutputArchiveStream =
            new CountingDataOutput(outputArchiveStream);
        byte[] buffer = new byte[32768];

        // First, output all local section parts.
        // As this work is done the local section parts and the central
        // directory are updated as follows:
        // 1. Offsets are reset to the new archive's location for each resource
        // 2. The compression method is updated to be NO_COMPRESSION for all
        //    entries that are being uncompressed.
        // 3. The compressed length is updated to be the same as the
        //    uncompressed length for all entries that are being uncompressed.
        // All other fields remain unchanged.
        for (LocalSectionParts lsp : inputArchive.getLocal().entries()) {
            final String entryPath = lsp.getLocalFilePart().getFileName();
            final CentralDirectoryFile cdf = cds.getByPath(entryPath);
            final long originalOffset =
                cdf.getRelativeOffsetOfLocalHeader_32bit();
            JreDeflateParameters params = directivesByPath.get(entryPath);
            final long newOffset =
                countingOutputArchiveStream.getBytesWrittenCount();
            // Update the relative offset in the central directory.
            cdf.setRelativeOffsetOfLocalHeader_32bit(
                newOffset);
            long expectedOffset = -1;
            if (params == null) {
                // Record the copy with the original offset. Length will not be
                // changed.
                lsp.write(countingOutputArchiveStream);
                result.recordCopy(entryPath, originalOffset, newOffset);
                expectedOffset = newOffset + lsp.getStructureLength();
            } else {
                // Compression technique known. Uncompress.
                final long originalCompressedSize =
                    cdf.getCompressedSize_32bit();
                lsp.getLocalFilePart().setCompressionMethod_16bit(
                    CompressionMethod.NO_COMPRESSION.value);
                cdf.setCompressionMethod_16bit(
                    CompressionMethod.NO_COMPRESSION.value);
                lsp.getLocalFilePart().setCompressedSize_32bit(
                    lsp.getLocalFilePart().getUncompressedSize_32bit());
                cdf.setCompressedSize_32bit(
                    lsp.getLocalFilePart().getUncompressedSize_32bit());
                lsp.getLocalFilePart().write(countingOutputArchiveStream);
                InflaterInputStream inflaterIn = null;
                Inflater inflater = null;

                final long offsetAtDataStart =
                    countingOutputArchiveStream.getBytesWrittenCount();
                try {
                    inflater = new Inflater(params.nowrap);
                    inflaterIn = new InflaterInputStream(
                        new ByteArrayInputStream(
                            lsp.getFileDataPart().getData()),
                        inflater);
                    int numRead = 0;
                    while ((numRead = inflaterIn.read(buffer)) >= 0) {
                        countingOutputArchiveStream.write(buffer, 0, numRead);
                    }
                } finally {
                    try {
                        inflaterIn.close();
                    } catch (Exception ignored) {
                        // Nothing
                    }
                    inflater.end();
                }
                final long offsetAtDataEnd =
                    countingOutputArchiveStream.getBytesWrittenCount();
                final long uncompressedSize =
                    offsetAtDataEnd - offsetAtDataStart;
                if (uncompressedSize !=
                    lsp.getLocalFilePart().getUncompressedSize_32bit()) {
                    // Sanity check
                    throw new IllegalStateException(
                        "Incorrect uncompressed size in " + entryPath +
                        ", expected " +
                        lsp.getLocalFilePart().getUncompressedSize_32bit() +
                        ", but was " + uncompressedSize);
                }

                if (lsp.hasDataDescriptor()) {
                    lsp.getDataDescriptorPart().setCompressedSize_32bit(
                        lsp.getLocalFilePart().getUncompressedSize_32bit());
                    lsp.getDataDescriptorPart().write(
                        countingOutputArchiveStream);
                }
                result.recordUncompress(entryPath, originalCompressedSize,
                    originalOffset, newOffset, params);
                expectedOffset = newOffset +
                    lsp.getLocalFilePart().getStructureLength() +
                    lsp.getLocalFilePart().getUncompressedSize_32bit() +
                    (lsp.hasDataDescriptor() ?
                        lsp.getDataDescriptorPart().getStructureLength() : 0);
            } // end of one local section part transform
            if (expectedOffset !=
                countingOutputArchiveStream.getBytesWrittenCount()) {
                // Sanity check
                throw new IllegalStateException("misalignment on entry "
                    + entryPath + ": expected " + expectedOffset +
                    ", but was " +
                    countingOutputArchiveStream.getBytesWrittenCount());
            }

        } // End of loop around local section parts

        // Now that the local section is completely written, copy the central
        // directory. The offsets, compression methods and compressed lengths
        // have all been updated so the central directory can be written as is.
        final long newCentralDirectoryOffset =
            countingOutputArchiveStream.getBytesWrittenCount();
        for (CentralDirectoryFile cdf : cds.entries()) {
            cdf.write(countingOutputArchiveStream);
        }
        final long endOfCentralDirectoryOffset =
            countingOutputArchiveStream.getBytesWrittenCount();
        final long lengthOfCentralDirectory =
            endOfCentralDirectoryOffset - newCentralDirectoryOffset;
        if (lengthOfCentralDirectory !=
            cds.getEocd().getLengthOfCentralDirectory_32bit()) {
            // Sanity check
            throw new IllegalStateException(
                "misalignment writing central directory: expected " +
                cds.getEocd().getLengthOfCentralDirectory_32bit() +
                ", but was " + lengthOfCentralDirectory);
        }

        // Finally, update the end-of-central directory record with the new
        // central directory offset and write it as well.
        final long originalCentralDirectoryOffset = cds.getEocd()
            .getOffsetOfStartOfCentralDirectoryRelativeToDisk_32bit();
        cds.getEocd().setOffsetOfStartOfCentralDirectoryRelativeToDisk_32bit(
            newCentralDirectoryOffset);
        cds.getEocd().write(countingOutputArchiveStream);
        result.recordRelocatCentralDirectory(
            originalCentralDirectoryOffset, newCentralDirectoryOffset);

        // The archive is now completely written in the new form.
        return result;
    }
}
