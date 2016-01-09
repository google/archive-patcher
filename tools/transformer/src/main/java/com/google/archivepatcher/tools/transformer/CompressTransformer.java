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
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import com.google.archivepatcher.Archive;
import com.google.archivepatcher.compression.JreDeflateParameters;
import com.google.archivepatcher.meta.CompressionMethod;
import com.google.archivepatcher.parts.CentralDirectoryFile;
import com.google.archivepatcher.parts.LocalSectionParts;
import com.google.archivepatcher.util.MiscUtils;

/**
 * Given an archive and a {@link TransformationRecord}, performs the inverse
 * operation of {@link UncompressTransformer} to restore an archive to its
 * original form.
 */
public class CompressTransformer {
    /**
     * Transform the specified archive using the specified
     * {@link TransformationRecord} file.
     * @param inputArchiveFile the archive to transform
     * @param recordFile the {@link TransformationRecord} file to use for
     * recompressing data in the input archive
     * @param outputArchiveFile where to write the transformed archive
     * @throws IOException if anything goes wrong reading or writing
     */
    public void transform(File inputArchiveFile, File recordFile,
        File outputArchiveFile)
        throws IOException {
        final Archive archive = Archive.fromFile(
            inputArchiveFile.getAbsolutePath());
        final TransformationRecord record = new TransformationRecord();
        FileInputStream recordFileIn = null;
        try {
            recordFileIn = new FileInputStream(recordFile);
            record.read(new DataInputStream(recordFileIn));
        } finally {
            try {
                recordFileIn.close();
            } catch (Exception ignored) {
                // Nothing
            }
        }

        final byte[] inputSHA256 = MiscUtils.sha256(inputArchiveFile);
        if (!Arrays.equals(record.getNewSHA256(), inputSHA256)) {
            // Sanity check: Never process an input that isn't the correct one.
            throw new IllegalStateException(
                "sha256 of input archive doesn't match transformation record!");
        }

        DataOutputStream outputStream = new DataOutputStream(
            new FileOutputStream(outputArchiveFile));
        try {
            transform(archive, record, outputStream);
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

        final byte[] outputSHA256 = MiscUtils.sha256(outputArchiveFile);
        if (!Arrays.equals(record.getOriginalSHA256(), outputSHA256)) {
            // Sanity check: Warn if output is corrupt
            throw new IllegalStateException(
                "sha256 of output archive doesn't match transformation " +
                "record!");
        }
    }

    @SuppressWarnings("resource")
    private void transform(Archive inputArchive,
        TransformationRecord transformationRecord,
        DataOutputStream outputArchiveStream) throws IOException {
        // Entries in the transformation record will be in the same order as
        // they are in the archive, so we can safely iterate on them. There will
        // be exactly one more operation than there are local section parts, and
        // that corresponds to the relocation of the central directory, which
        // will always be the final entry.
        List<LocalSectionParts> localParts = inputArchive.getLocal().entries();
        List<TransformationRecord.Operation> operations =
            transformationRecord.getOperations();
        if (localParts.size() != operations.size() - 1) {
            throw new IllegalStateException("transformation record corrupt");
        }
        Iterator<LocalSectionParts> localPartsIterator = localParts.iterator();
        Iterator<TransformationRecord.Operation> operationsIterator =
            operations.iterator();
        CountingDataOutput countingOutputArchiveStream =
            new CountingDataOutput(outputArchiveStream);
        final byte[] buffer = new byte[32768];
        while (localPartsIterator.hasNext()) {
            final LocalSectionParts lsp = localPartsIterator.next();
            final TransformationRecord.Operation operation =
                operationsIterator.next();
            final String entryPath = lsp.getLocalFilePart().getFileName();
            final CentralDirectoryFile cdf =
                inputArchive.getCentralDirectory().getByPath(entryPath);
            if (operation.getId() == TransformationRecord.COPY) {
                // Just update the offset and clone the entry.
                TransformationRecord.Copy copy =
                    (TransformationRecord.Copy) operation;
                if (!entryPath.equals(copy.getPath())) {
                    throw new IllegalStateException("path misalignment");
                }
                if (countingOutputArchiveStream.getBytesWrittenCount() !=
                    copy.getOriginalOffset()) {
                    // Sanity check
                    throw new IllegalStateException("copy misalignment for "
                        + entryPath + "; expected " + copy.getOriginalOffset()
                        + ", actual " +
                        countingOutputArchiveStream.getBytesWrittenCount());
                }

                cdf.setRelativeOffsetOfLocalHeader_32bit(
                    copy.getOriginalOffset());
                lsp.write(countingOutputArchiveStream);
            } else if (operation.getId() == TransformationRecord.UNCOMPRESS) {
                // Update the offset, compression method, and compressed size;
                // Then recompress and write it all out.
                TransformationRecord.Uncompress uncompress =
                    (TransformationRecord.Uncompress) operation;
                if (!entryPath.equals(uncompress.getPath())) {
                    // Sanity check
                    throw new IllegalStateException("path misalignment");
                }
                if (countingOutputArchiveStream.getBytesWrittenCount() !=
                    uncompress.getOriginalOffset()) {
                    // Sanity check
                    throw new IllegalStateException("uncompress misalignment "
                        + "for " + entryPath + "; expected " +
                        uncompress.getOriginalOffset() + ", actual " +
                        countingOutputArchiveStream.getBytesWrittenCount());
                }

                cdf.setRelativeOffsetOfLocalHeader_32bit(
                    uncompress.getOriginalOffset());
                cdf.setCompressionMethod_16bit(
                    CompressionMethod.DEFLATED.value);
                cdf.setCompressedSize_32bit(
                    uncompress.getOriginalCompressedSize());
                lsp.getLocalFilePart().setCompressionMethod_16bit(
                    CompressionMethod.DEFLATED.value);
                lsp.getLocalFilePart().setCompressedSize_32bit(
                    uncompress.getOriginalCompressedSize());
                if (lsp.hasDataDescriptor()) {
                    lsp.getDataDescriptorPart().setCompressedSize_32bit(
                        uncompress.getOriginalCompressedSize());
                }
                lsp.getLocalFilePart().write(countingOutputArchiveStream);
                Deflater deflater = null;
                DeflaterOutputStream deflaterOut = null;
                JreDeflateParameters params = uncompress.getDeflateParameters();
                final long offsetAtDataStart =
                    countingOutputArchiveStream.getBytesWrittenCount();
                try {
                    deflater = new Deflater(params.level, params.nowrap);
                    deflater.setStrategy(params.strategy);
                    // DO NOT close this stream, as it closes the underlying
                    // stream as well, which is NOT desirable here.
                    deflaterOut = new DeflaterOutputStream(
                        countingOutputArchiveStream, deflater, 16384);
                    InputStream uncompressedIn = new ByteArrayInputStream(
                        lsp.getFileDataPart().getData());
                    int numRead = 0;
                    while ((numRead = uncompressedIn.read(buffer)) >= 0) {
                        deflaterOut.write(buffer, 0, numRead);
                    }
                    deflaterOut.finish();
                } finally {
                    try {
                        deflater.end();
                    } catch (Exception ignored) {
                        // Nothing
                    }
                }
                final long offsetAtDataEnd =
                    countingOutputArchiveStream.getBytesWrittenCount();
                final long compressedSize =
                    offsetAtDataEnd - offsetAtDataStart;
                if (compressedSize !=
                    lsp.getLocalFilePart().getCompressedSize_32bit()) {
                    // Sanity check
                    throw new IllegalStateException(
                        "Incorrect compressed size in " + entryPath +
                        ", expected " +
                        lsp.getLocalFilePart().getCompressedSize_32bit() +
                        ", but was " + compressedSize);
                }

                if (lsp.hasDataDescriptor()) {
                    lsp.getDataDescriptorPart().write(
                        countingOutputArchiveStream);
                }
            } // end of operation processing if-statement
        } // end of while loop over local section parts

        // Central directory file entries have all been updated, so write them
        // now.
        for (CentralDirectoryFile cdf :
            inputArchive.getCentralDirectory().entries()) {
            cdf.write(countingOutputArchiveStream);
        }
        // Now fix up and write the end-of-central-directory. The final
        // operation that remains should be the one needed to do this.
        TransformationRecord.RelocateCentralDirectory relocate =
            (TransformationRecord.RelocateCentralDirectory)
                operationsIterator.next();
        inputArchive.getCentralDirectory().getEocd()
            .setOffsetOfStartOfCentralDirectoryRelativeToDisk_32bit(
                relocate.getOriginalOffset());
        inputArchive.getCentralDirectory().getEocd().write(
            countingOutputArchiveStream);

        // The archive is now completely written in its original form.
    }
}
