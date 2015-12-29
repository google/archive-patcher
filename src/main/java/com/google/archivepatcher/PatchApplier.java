// Copyright 2014 Google Inc. All rights reserved.
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

package com.google.archivepatcher;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.archivepatcher.compression.BuiltInCompressionEngine;
import com.google.archivepatcher.compression.Uncompressor;
import com.google.archivepatcher.delta.DeltaApplier;
import com.google.archivepatcher.parts.CentralDirectoryFile;
import com.google.archivepatcher.parts.CentralDirectorySection;
import com.google.archivepatcher.parts.FileData;
import com.google.archivepatcher.parts.LocalSection;
import com.google.archivepatcher.parts.LocalSectionParts;
import com.google.archivepatcher.patcher.BeginMetadata;
import com.google.archivepatcher.patcher.NewMetadata;
import com.google.archivepatcher.patcher.PatchCommand;
import com.google.archivepatcher.patcher.PatchDirective;
import com.google.archivepatcher.patcher.PatchMetadata;
import com.google.archivepatcher.patcher.PatchParser;
import com.google.archivepatcher.patcher.RefreshMetadata;

/**
 * Applies a patch that will produce a given "new" archive when applied to
 * a given "old" archive. The patch should have been produced by a
 * {@link PatchGenerator} with a version number less than or equal to the
 * current version number.
 * @see PatchGenerator
 */
// FIXME: Stream the output, don't buffer everything in memory.
// FIXME: Don't modify the old Archive's in-memory representation.
public class PatchApplier {

    /**
     * The old archive, used as a source for copying and as a base for
     * building new resources with patch metadata.
     */
    private final Archive oldArchive;

    /**
     * The parser for reading the patch stream.
     */
    private final PatchParser parser;

    /**
     * The new archive being built as the output of the patching process.
     */
    private final Archive newArchive;

    /**
     * A mapping of all available {@link DeltaApplier}s, by ID.
     */
    private final Map<Integer, DeltaApplier> deltaAppliersById =
        new HashMap<Integer, DeltaApplier>();

    /**
     * A mapping of all available {@link Uncompressor}s, by Id.
     */
    private final Map<Integer, Uncompressor> uncompressorsById =
        new HashMap<Integer, Uncompressor>();

    /**
     * Entries in the old archive's central directory, by offset in the old
     * archive file.
     */
    private final Map<Integer, CentralDirectoryFile> oldCDFByOffset =
            new HashMap<Integer, CentralDirectoryFile>();

    /**
     * The current offset in the new archive being built.
     */
    private int currentFileOffset = 0; // How far we have written

    /**
     * Creates a patch applier that will use a given {@link PatchParser} and
     * (optional) {@link DeltaApplier} to read and apply a patch to the
     * specified "old" archive, producing a new (patched) archive at a path
     * of the caller's choosing.
     * @param oldArchive the "old" archive
     * @param parser the {@link PatchParser} to use for parsing the patch file
     * @param deltaAppliers optionally, a list of {@link DeltaApplier}s to be 
     * used for applying deltas; if unspecified or empty, no
     * {@link PatchCommand#PATCH} commands from the patch can be applied and
     * patch processing will fail if any are present 
     * @param uncompressors optionally, a list of {@link Uncompressor}s to be
     * used for uncompressing delta resources; if unspecified or empty, no
     * resources can be uncompressed; if the patch contains compressed
     * resources, patch application will fail.
     */
    public PatchApplier(final Archive oldArchive, final PatchParser parser,
        final List<DeltaApplier> deltaAppliers,
        final List<Uncompressor> uncompressors) {
        this.oldArchive = oldArchive;
        this.parser = parser;
        this.newArchive = new Archive();
        if (deltaAppliers != null) {
            for (final DeltaApplier deltaApplier : deltaAppliers) {
                deltaAppliersById.put(deltaApplier.getId(), deltaApplier);
            }
        }
        if (uncompressors != null) {
            for (final Uncompressor uncompressor : uncompressors) {
                uncompressorsById.put(uncompressor.getId(), uncompressor);
            }
        }
    }

    /**
     * Creates a patch applier that will use a default {@link PatchParser} and
     * (optional) {@link DeltaApplier} to read and apply a patch from the
     * specified patch path to the specified "old" archive, producing a new
     * (patched) archive at a path of the caller's choosing.
     * @param oldArchive the "old" archive
     * @param patchPath the path to the patch file to be parsed
     * @param deltaAppliers optionally, a list of {@link DeltaApplier}s to be
     * used for applying deltas; if unspecified, no {@link PatchCommand#PATCH}
     * commands from the patch can be applied and patch processing will fail if
     * any are present
     * @param uncompressors optionally, a list of {@link Uncompressor}s to be
     * used for uncompressing delta resources; if unspecified or empty, no
     * resources can be uncompressed; if the patch contains compressed
     * resources, patch application will fail.
     * @throws IOException if unable to open the specified patch file
     */
    public PatchApplier(final String oldArchive, final String patchPath,
        final List<DeltaApplier> deltaAppliers,
        final List<Uncompressor> uncompressors) throws IOException {
        this(Archive.fromFile(oldArchive),
            new PatchParser(new File(patchPath)), deltaAppliers, uncompressors);
    }

    /**
     * Cache the offsets of all local headers in the old archive.
     * Patch commands refer to offsets rather than paths; caching the offsets
     * allows fast lookups in the "old" archive while processing the patch.
     */
    private void cacheOffsets() {
        for (CentralDirectoryFile entry :
            oldArchive.getCentralDirectory().entries()) {
            oldCDFByOffset.put(
                (int) entry.getRelativeOffsetOfLocalHeader_32bit(), entry);
        }
    }

    /**
     * Returns the {@link CentralDirectoryFile} located at the specified offset
     * in the old archive's {@link CentralDirectorySection}.
     * @param offset the offset in the "old" archive
     * @return the entry
     */
    private CentralDirectoryFile getOldCentralDirectoryFile(final int offset) {
        return oldCDFByOffset.get(offset);
    }
    
    /**
     * Returns the {@link LocalSectionParts} located at the specified offset in
     * the "old" archive's {@link LocalSection}.
     * @param offset the offset in the "old" archive
     * @return the entry
     */
    private LocalSectionParts getOldLocalSectionParts(final int offset) {
        return oldArchive.getLocal().getByPath(
            getOldCentralDirectoryFile(offset).getFileName());
    }

    /**
     * Returns the {@link CentralDirectoryFile} from the "new" archive that
     * corresponds to the specified entry in the "old" archive, based on a
     * comparison of paths.
     * @param oldCentralDirectoryFile the entry from the "old" archive to find
     * the corresponding entry for in the "new" archive (by path)
     * @return the entry, if it exists; otherwise, null
     */
    private CentralDirectoryFile getNewCentralDirectoryFile(
        final CentralDirectoryFile oldCentralDirectoryFile) {
        return getNewCDF(oldCentralDirectoryFile.getFileName());
    }

    /**
     * Returns the {@link CentralDirectoryFile} from the "new" archive that
     * matches the specified path.
     * @param path the path to look up
     * @return the entry, if it exists; otherwise, null
     */
    private CentralDirectoryFile getNewCDF(String path) {
        return newArchive.getCentralDirectory().getByPath(path);
    }

    /**
     * Applies the patch, producing the "new" archive in-memory.
     * @return the "new" (patched) archive, ready to be written
     * @throws IOException if anything goes wrong while reading data
     */
    public Archive applyPatch() throws IOException {
        parser.init();
        cacheOffsets();
        PatchDirective directive = null;
        while ((directive = parser.read()) != null) {
            switch (directive.getCommand()) {
                case COPY:
                    applyCopy(directive.getOffset());
                    break;
                case PATCH:
                    applyPatch(directive.getOffset(), (PatchMetadata) directive.getPart());
                    break;
                case BEGIN:
                    applyBegin((BeginMetadata) directive.getPart());
                    break;
                case NEW:
                    applyNew((NewMetadata) directive.getPart());
                    break;
                case REFRESH:
                    applyRefresh(directive.getOffset(), (RefreshMetadata) directive.getPart());
                    break;
                default: throw new UnsupportedOperationException("Unsupported patch command: " + directive.getCommand());
            }
        }
        return newArchive;
    }

    /**
     * Applies a {@link PatchCommand#COPY} operation.
     * @param sourceOffset the offset in the "old" archive at which the source
     * entry exists
     */
    private void applyCopy(final int sourceOffset) {
        CentralDirectoryFile cdf = getOldCentralDirectoryFile(sourceOffset);
        LocalSectionParts alp = oldArchive.getLocal().getByPath(cdf.getFileName());
        newArchive.getLocal().append(alp);
        assert(currentFileOffset == getNewCentralDirectoryFile(cdf).getRelativeOffsetOfLocalHeader_32bit());
        currentFileOffset += alp.getStructureLength();
    }

    /**
     * Applies a {@link PatchCommand#BEGIN} operation.
     * @param metadata the "new" data for the operation
     */
    private void applyBegin(final BeginMetadata metadata) {
        newArchive.setCentralDirectry(metadata.getCd());
        newArchive.setLocal(new LocalSection());
    }

    /**
     * Applies a {@link PatchCommand#NEW} operation.
     * @param metadata the "new" data for the operation
     * @throws IOException if unable to apply the patch cleanly
     */
    private void applyNew(final NewMetadata metadata) throws IOException {
        byte[] newData = metadata.getData();
        int compressionEngineId = metadata.getPatchingCompressionEngineId();
        if (compressionEngineId != BuiltInCompressionEngine.NONE.getId()) {
            // Uncompress the data first.
            Uncompressor uncompressor = uncompressorsById.get(compressionEngineId);
            if (uncompressor == null) {
                throw new RuntimeException("No uncompressor with ID=" + compressionEngineId + " is available to apply this patch.");
            }
            ByteArrayOutputStream uncompressedData = new ByteArrayOutputStream();
            uncompressor.uncompress(new ByteArrayInputStream(newData), uncompressedData);
            // Switch to using the uncompressed data for final processing
            newData = uncompressedData.toByteArray();
        }

        LocalSectionParts alp = new LocalSectionParts();
        alp.setLocalFilePart(metadata.getLocalFilePart());
        alp.setDataDescriptorPart(metadata.getDataDescriptorPart());
        alp.setFileDataPart(new FileData(newData));
        newArchive.getLocal().append(alp);
        assert(currentFileOffset ==
                getNewCDF(alp.getLocalFilePart().getFileName())
                .getRelativeOffsetOfLocalHeader_32bit());
        currentFileOffset += alp.getStructureLength();
    }

    /**
     * Applies a {@link PatchCommand#REFRESH} operation.
     * @param sourceOffset the offset in the "old" archive at which the source
     * entry exists
     * @param metadata the "new" data for the operation
     */
    private void applyRefresh(final int sourceOffset,
        final RefreshMetadata metadata) {
        LocalSectionParts alp = new LocalSectionParts();
        alp.setLocalFilePart(metadata.getLocalFilePart());
        alp.setDataDescriptorPart(metadata.getDataDescriptorPart());
        alp.setFileDataPart(
            getOldLocalSectionParts(sourceOffset).getFileDataPart());
        newArchive.getLocal().append(alp);
        
        assert(currentFileOffset ==
                getNewCDF(alp.getLocalFilePart().getFileName())
                .getRelativeOffsetOfLocalHeader_32bit());
        currentFileOffset += alp.getStructureLength();
    }

    /**
     * Applies a {@link PatchCommand#PATCH} operation.
     * @param sourceOffset the offset in the "old" archive at which the source
     * entry exists
     * @param metadata the "new" data for the operation
     * @throws IOException if unable to apply the patch cleanly
     */
    private void applyPatch(final int sourceOffset,
        final PatchMetadata metadata) throws IOException {
        final DeltaApplier deltaApplier = deltaAppliersById.get(metadata.getDeltaGeneratorId());
        if (deltaApplier == null) {
            throw new RuntimeException("No delta applier with ID=" + metadata.getDeltaGeneratorId() + " is available to apply this patch.");
        }
        LocalSectionParts oldAlp = getOldLocalSectionParts(sourceOffset);
        ByteArrayInputStream oldDataInput = new ByteArrayInputStream(
                oldAlp.getFileDataPart().getData());
        byte[] rawPatchInput = metadata.getData();
        ByteArrayInputStream patchInput = new ByteArrayInputStream(rawPatchInput);
        int compressionEngineId = metadata.getCompressionEngineId();
        if (compressionEngineId != BuiltInCompressionEngine.NONE.getId()) {
            // Uncompress the data first.
            Uncompressor uncompressor = uncompressorsById.get(compressionEngineId);
            if (uncompressor == null) {
                throw new RuntimeException("No uncompressor with ID=" + compressionEngineId + " is available to apply this patch.");
            }
            ByteArrayOutputStream uncompressedPatchInput = new ByteArrayOutputStream();
            uncompressor.uncompress(patchInput, uncompressedPatchInput);
            // Set patch input stream to the uncompressed data
            patchInput = new ByteArrayInputStream(uncompressedPatchInput.toByteArray());
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        deltaApplier.applyDelta(oldDataInput, patchInput, buffer);
        LocalSectionParts newAlp = new LocalSectionParts();
        newAlp.setLocalFilePart(metadata.getLocalFilePart());
        newAlp.setFileDataPart(new FileData(buffer.toByteArray()));
        newAlp.setDataDescriptorPart(metadata.getDataDescriptorPart());
        newArchive.getLocal().append(newAlp);
        
        assert(currentFileOffset ==
                getNewCDF(newAlp.getLocalFilePart().getFileName())
                .getRelativeOffsetOfLocalHeader_32bit());
        currentFileOffset += newAlp.getStructureLength();
    }
}