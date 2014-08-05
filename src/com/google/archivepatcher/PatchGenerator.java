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
import java.io.DataOutput;
import java.io.IOException;

import com.google.archivepatcher.parts.CentralDirectoryFile;
import com.google.archivepatcher.parts.FileData;
import com.google.archivepatcher.parts.LocalSectionParts;
import com.google.archivepatcher.patcher.BeginMetadata;
import com.google.archivepatcher.patcher.NewMetadata;
import com.google.archivepatcher.patcher.PatchCommand;
import com.google.archivepatcher.patcher.PatchDirective;
import com.google.archivepatcher.patcher.PatchMetadata;
import com.google.archivepatcher.patcher.PatchWriter;
import com.google.archivepatcher.patcher.RefreshMetadata;

//TODO: Support more than one DeltaGenerator (a list would be nice)
//TODO: Compress the delta artifacts on a trial basis to see how they will
//      perform compared to compressing the entirety of the new data, and only
//      use the delta if its ***compressed*** size beats the ***compressed***
//      size of the new data.
/**
 * Generates a patch that will produce a given "new" archive when applied to
 * a given "old" archive in a {@link PatchApplier}.
 * @see PatchApplier
 */
public class PatchGenerator {
    /**
     * Used to output the patch.
     */
    private final PatchWriter patchWriter;

    /**
     * The "old" archive.
     */
    private final Archive oldArchive;

    /**
     * The "new" archive.
     */
    private final Archive newArchive;

    /**
     * Analyzes and produces deltas to be embedded into the patch for
     * transformations that can be represented more compactly with a delta than
     * a complete copy of the "new" data.
     */
    private final DeltaGenerator deltaGenerator;

    /**
     * Create a patch generator that will write a patch to the specified
     * destination which, when applied to the specified "old" archive, will
     * produce the specified "new" archive.
     * @param oldArchive the "old" archive
     * @param newArchive the "new" archive
     * @param patchOut the destination to which the patch should be written
     * @param deltaGenerator optionally, a {@link DeltaGenerator} to be used
     * for creating deltas; if unspecified, no {@link PatchCommand#PATCH}
     * commands will be produced in the resulting patch 
     */
    public PatchGenerator(Archive oldArchive, Archive newArchive, DataOutput patchOut, DeltaGenerator deltaGenerator) {
        this.oldArchive = oldArchive;
        this.newArchive = newArchive;
        this.deltaGenerator = deltaGenerator;
        patchWriter = new PatchWriter(patchOut);
    }

    /**
     * Performs identically to
     * {@link #PatchGenerator(Archive, Archive, DataOutput, DeltaGenerator)},
     * but reads the "old" and "new" archives from the specified file paths.
     * @param oldPath the path to the "old" archive
     * @param newPath the path to the "new" archive
     * @param patchOut the destination to which the patch should be written
     * @param deltaGenerator optionally, a {@link DeltaGenerator} to be used
     * for creating deltas; if unspecified, no {@link PatchCommand#PATCH}
     * commands will be produced in the resulting patch 
     * @throws IOException if unable to read from either path
     */
    public PatchGenerator(final String oldPath, final String newPath,
        final DataOutput patchOut, final DeltaGenerator deltaGenerator)
            throws IOException{
        this(Archive.fromFile(oldPath), Archive.fromFile(newPath),
            patchOut, deltaGenerator);
    }

    /**
     * Initializes the patch writer, writing standard headers to the
     * destination. No other processing is performed.  Subsequent invocations
     * are no-ops.
     * @throws IOException if unable to write to the destination
     */
    public void init() throws IOException {
        patchWriter.init();
    }

    /**
     * Returns true if and only if the {@link FileData} components of the two
     * given {@link LocalSectionParts} objects are identical to one another.
     * @param lsp1 the first object to compare
     * @param lsp2 the second object to compare
     * @return as described
     * @see #nonDataSame(CentralDirectoryFile, LocalSectionParts, CentralDirectoryFile, LocalSectionParts)
     */
    public static boolean dataSame(LocalSectionParts lsp1,
        LocalSectionParts lsp2) {
        return lsp1.getFileDataPart().equals(lsp2.getFileDataPart());
    }

    /**
     * Returns true if and only if the <em>metadata</em> associated with a
     * given logically-described resources is the same.
     * <p>
     * This method basically checks all the things that
     * {@link #dataSame(LocalSectionParts, LocalSectionParts)} does not. The
     * fields in the local-section and central directory-section bits are
     * checked for equivalence without regard for the "offset" information that
     * describes where a given entry starts in its host archive. If all such
     * information is identical between both the first resource's bits and
     * the second resource's bits, this method returns true. If there are
     * <em>any</em> differences other than the offset fields, returns false.
     * @param cdf1 the {@link CentralDirectoryFile} entry for the first resource
     * @param lsp1 the {@link LocalSectionParts} entry for the first resource
     * @param cdf2 the {@link CentralDirectoryFile} entry for the second
     * resource
     * @param lsp2 the {@link LocalSectionParts} entry for the second resource
     * @return as described
     * @see #dataSame(LocalSectionParts, LocalSectionParts)
     */
    public static boolean nonDataSame(CentralDirectoryFile cdf1,
            LocalSectionParts lsp1,
            CentralDirectoryFile cdf2,
            LocalSectionParts lsp2) {
        if (!cdf1.positionIndependentEquals(cdf2)) return false;
        if (!lsp1.getLocalFilePart().equals(lsp2.getLocalFilePart())) return false;
        if (lsp1.hasDataDescriptor() != lsp2.hasDataDescriptor()) return false;
        if (!lsp1.hasDataDescriptor()) return true; // nothing left to compare
        return lsp1.getDataDescriptorPart().equals(lsp2.getDataDescriptorPart());
    }

    /**
     * Processes all of the entries in the configured archives and writes a
     * patch to the specified destination.
     * @throws IOException
     */
    public void generateAll() throws IOException {
        // Output the central directory.
        patchWriter.write(PatchDirective.BEGIN(
                new BeginMetadata(newArchive.getCentralDirectory())));

        for (LocalSectionParts newLSP : newArchive.getLocal().entries()) {
            patchWriter.write(processNewEntry(newLSP));
        } // for-loop
    }

    /**
     * Processes one entry in the "new" archive. This method is called once per
     * entry in the new archive, in the order in which the entries appears in
     * the archive stream (not the order in which they are encountered in the
     * central directory). This is critically important to producing a
     * <em>streaming</em> patch format, as the directives that are generated
     * here will be applied in the same order when writing the new archive
     * later via a {@link PatchWriter}.
     * @param newLSP the {@link LocalSectionParts} from the "new" archive
     * @return the directive to produce in the patch for the entry described
     * by newLSP
     * @throws IOException if unable to process the entry
     */
    private PatchDirective processNewEntry(LocalSectionParts newLSP) throws IOException {
        final String filePath = newLSP.getLocalFilePart().getFileName();
        final CentralDirectoryFile oldCDF =
                oldArchive.getCentralDirectory().getByPath(filePath);
        if (oldCDF != null) {
            final CentralDirectoryFile newCDF =
                newArchive.getCentralDirectory().getByPath(filePath);
            final LocalSectionParts oldLSP =
                oldArchive.getLocal().getByPath(filePath);
            return generateDirective(oldCDF, oldLSP, newCDF, newLSP);
        }
        // Else, totally new resource. Simple case.
        return PatchDirective.NEW(
                new NewMetadata(
                        newLSP.getLocalFilePart(),
                        newLSP.getFileDataPart(),
                        newLSP.getDataDescriptorPart()));
    }

    /**
     * Given a resource that is found in both the "old" and "new" archives,
     * generate a {@link PatchDirective} suitable for writing to the destination
     * that will transform the resource from its form in the "old" archive
     * to its form in the "new" archive.
     * 
     * @param oldCDF the {@link CentralDirectoryFile} entry from the "old"
     * archive
     * @param oldLSP the {@link LocalSectionParts} entry from the "old" archive
     * @param newCDF the {@link CentralDirectoryFile} entry from the "new"
     * @param newLSP the {@link LocalSectionParts} entry from the "new" archive
     * @return a {@link PatchDirective} suitable for output
     * @throws IOException if there is a problem while reading the resources
     */
    private PatchDirective generateDirective(
        final CentralDirectoryFile oldCDF, LocalSectionParts oldLSP,
        final CentralDirectoryFile newCDF, LocalSectionParts newLSP)
            throws IOException {
        if (dataSame(oldLSP, newLSP)) {
            // Data is the same. We can at least refresh, maybe pure copy.
            if (nonDataSame(oldCDF, oldLSP, newCDF, newLSP)) {
                // Identical resource: COPY
                return PatchDirective.COPY(
                        (int) oldCDF.getRelativeOffsetOfLocalHeader_32bit());
            }

            // Identical resource except for metadata: REFRESH
            return PatchDirective.REFRESH(
                    (int) oldCDF.getRelativeOffsetOfLocalHeader_32bit(),
                    new RefreshMetadata(
                            newLSP.getLocalFilePart(),
                            newLSP.getDataDescriptorPart()));
        }
        
        // Else, the data has changed between old and new.
        // Try to generate a compact representation...
        if (deltaGenerator != null && deltaGenerator.accept(
            oldCDF, oldLSP, newCDF, newLSP)) {
            PatchDirective directive = generateDelta(oldCDF, oldLSP, newLSP);
            if (directive != null) return directive;
        }

        // If we were unable to generate a compact representation, we have to
        // embed the entire "new" resource in the patch.
        return PatchDirective.NEW(
                new NewMetadata(
                        newLSP.getLocalFilePart(),
                        newLSP.getFileDataPart(),
                        newLSP.getDataDescriptorPart()));
    }

    /**
     * @param oldCDF the {@link CentralDirectoryFile} entry from the "old"
     * archive
     * @param oldLSP the {@link LocalSectionParts} entry from the "old" archive
     * @param newLSP the {@link LocalSectionParts} entry from the "new" archive
     * @return a {@link PatchDirective} suitable for output, if and only if
     * a delta can be generated that produces a more compact representation
     * than embedding a copy of the new data.
     * @throws IOException if there is an I/O error encountered while
     * processing the delta
     */
    private PatchDirective generateDelta(final CentralDirectoryFile oldCDF,
        final LocalSectionParts oldLSP, LocalSectionParts newLSP)
            throws IOException {

        // Run the delta generator on this input
        final ByteArrayOutputStream deltaBuffer = new ByteArrayOutputStream();
        final ByteArrayInputStream oldIn = new ByteArrayInputStream(
            oldLSP.getFileDataPart().getData());
        final ByteArrayInputStream newIn = new ByteArrayInputStream(
            newLSP.getFileDataPart().getData());
        deltaGenerator.makeDelta(oldIn, newIn, deltaBuffer);

        // Analyze delta results
        final int deltaSize = deltaBuffer.size();
        final int originalSize = newLSP.getFileDataPart().getData().length;
        final int savings = originalSize - deltaSize;
        if (savings <= 0) {
            // We failed to produce a more compact representation, give up.
            return null;
        }

        // We produces a more compact delta, so generate a PATCH directive.
        return PatchDirective.PATCH(
                (int) oldCDF.getRelativeOffsetOfLocalHeader_32bit(),
                new PatchMetadata(
                        newLSP.getLocalFilePart(),
                        newLSP.getDataDescriptorPart(),
                        deltaBuffer.toByteArray()));
    }
}