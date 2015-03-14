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
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import com.google.archivepatcher.compression.BuiltInCompressionEngine;
import com.google.archivepatcher.compression.Compressor;
import com.google.archivepatcher.delta.BuiltInDeltaEngine;
import com.google.archivepatcher.delta.DeltaGenerator;
import com.google.archivepatcher.meta.CompressionMethod;
import com.google.archivepatcher.parts.CentralDirectoryFile;
import com.google.archivepatcher.parts.FileData;
import com.google.archivepatcher.parts.LocalSectionParts;
import com.google.archivepatcher.patcher.BeginMetadata;
import com.google.archivepatcher.patcher.NewMetadata;
import com.google.archivepatcher.patcher.PatchCommand;
import com.google.archivepatcher.patcher.PatchDirective;
import com.google.archivepatcher.patcher.PatchMagic;
import com.google.archivepatcher.patcher.PatchMetadata;
import com.google.archivepatcher.patcher.PatchWriter;
import com.google.archivepatcher.patcher.RefreshMetadata;
import com.google.archivepatcher.reporting.PatchGenerationReport;
import com.google.archivepatcher.reporting.PatchGenerationReportEntry;
import com.google.archivepatcher.reporting.Strategy;

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
     * The report for the patch generation process, used to output statistics.
     */
    private final PatchGenerationReport report;

    /**
     * Analyzes and produces deltas to be embedded into the patch for
     * transformations that can be represented more compactly with a delta than
     * a complete copy of the "new" data.
     */
    private final List<DeltaGenerator> deltaGenerators = new ArrayList<DeltaGenerator>();

    /**
     * Compresses the output of the {@link #deltaGenerators}. The compressor
     * that produces the most compact representation wins.
     */
    private final List<Compressor> compressors = new ArrayList<Compressor>();

    /**
     * Create a patch generator that will write a patch to the specified
     * destination which, when applied to the specified "old" archive, will
     * produce the specified "new" archive.
     * @param oldArchive the "old" archive
     * @param newArchive the "new" archive
     * @param patchOut the destination to which the patch should be written
     * @param deltaGenerators optionally, a list of {@link DeltaGenerator}s to
     * be used for creating deltas; if unspecified or empty, no
     * {@link PatchCommand#PATCH} commands will be produced in the resulting
     * patch 
     * @param compressors optionally, a list of {@link Compressor}s to be used
     * for compressing deltas and other parts of the patch; if unspecified or
     * empty, no compression will be applied to deltas.
     */
    public PatchGenerator(Archive oldArchive, Archive newArchive,
        DataOutput patchOut, List<DeltaGenerator> deltaGenerators,
        List<Compressor> compressors) {
        this.oldArchive = oldArchive;
        this.newArchive = newArchive;
        if (deltaGenerators != null) {
            this.deltaGenerators.addAll(deltaGenerators);
        }
        if (compressors != null) {
            this.compressors.addAll(compressors);
        }

        // TODO: Support files > 2GB
        this.report = new PatchGenerationReport(
            (int) oldArchive.getMinimumSizeBytes(),
            (int) newArchive.getMinimumSizeBytes());

        patchWriter = new PatchWriter(patchOut);
    }

    /**
     * Performs identically to
     * {@link #PatchGenerator(Archive, Archive, DataOutput, List, List)},
     * but reads the "old" and "new" archives from the specified file paths.
     * @param oldPath the path to the "old" archive
     * @param newPath the path to the "new" archive
     * @param patchOut the destination to which the patch should be written
     * @param deltaGenerators optionally, a list of {@link DeltaGenerator}s to
     * be used for creating deltas; if unspecified or empty, no
     * {@link PatchCommand#PATCH} commands will be produced in the resulting
     * patch
     * @param compressors optionally, a list of {@link Compressor}s to be used
     * for compressing deltas and other parts of the patch; if unspecified or
     * empty, no compression will be applied to deltas.
     * @throws IOException if unable to read from either path
     */
    public PatchGenerator(final String oldPath, final String newPath,
        final DataOutput patchOut, final List<DeltaGenerator> deltaGenerators,
        final List<Compressor> compressors)
            throws IOException{
        this(Archive.fromFile(oldPath), Archive.fromFile(newPath),
            patchOut, deltaGenerators, compressors);
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
     * @return a {@link PatchGenerationReport} containing detailed information
     * about the patch generation process
     * @throws IOException if anything goes wrong reading or writing
     */
    public PatchGenerationReport generateAll() throws IOException {
        // Try a total-archive delta to see if it is the best strategy.
        PatchGenerationReportEntry bestWholeArchiveEntry = new PatchGenerationReportEntry(
            null,  // oldPath
            null,  // newPath
            null,  // command
            BuiltInCompressionEngine.NONE.getId(),
            oldArchive.getStructureLength(),
            BuiltInCompressionEngine.NONE.getId(),
            newArchive.getStructureLength());
        // TODO: Use a whole-archive patch if that is the winner
        ByteArrayOutputStream bestWholeArchiveResult = null;
        Strategy bestWholeArchiveStrategy = Strategy.getInstance(
            BuiltInDeltaEngine.NONE.getId(),
            BuiltInCompressionEngine.NONE.getId());
        int bestWholeArchiveResultSize = newArchive.getStructureLength();
        for (DeltaGenerator generator : deltaGenerators) {
            InputStream oldData = null;
            InputStream newData = null;
            ByteArrayOutputStream deltaOut = null;
            try {
                oldData = oldArchive.getInputStream();
                newData = newArchive.getInputStream();
                deltaOut = new ByteArrayOutputStream();
                generator.makeDelta(oldData, newData, deltaOut);
                byte[] deltaOutBytes = deltaOut.toByteArray();

                Strategy deltaOnlyStrategy = Strategy.getInstance(
                    generator.getId(), BuiltInCompressionEngine.NONE.getId());
                bestWholeArchiveEntry.recordStrategyResult(deltaOnlyStrategy,
                    deltaOutBytes.length);
                // Without any compressors this could still win, so check.
                if (deltaOutBytes.length < bestWholeArchiveResultSize) {
                    bestWholeArchiveStrategy = deltaOnlyStrategy;
                    bestWholeArchiveResult = deltaOut;
                    bestWholeArchiveResultSize = deltaOutBytes.length;
                }

                for (Compressor compressor : compressors) {
                    ByteArrayOutputStream compressedDeltaOut = new ByteArrayOutputStream();
                    try {
                        compressor.compress(new ByteArrayInputStream(deltaOutBytes), compressedDeltaOut);
                        int size = compressedDeltaOut.size();
                        Strategy strategy = Strategy.getInstance(generator.getId(), compressor.getId());
                        bestWholeArchiveEntry.recordStrategyResult(strategy, size);
                        if (size < bestWholeArchiveResultSize) {
                            bestWholeArchiveStrategy = strategy;
                            bestWholeArchiveResult = compressedDeltaOut;
                            bestWholeArchiveResultSize = size;
                        }
                    } finally {
                        try { compressedDeltaOut.close(); } catch (Exception ignored) {}
                    }
                }
            } finally {
                try { oldData.close(); } catch (Exception ignored) {}
                try { newData.close(); } catch (Exception ignored) {}
                try { deltaOut.close(); } catch (Exception ignored) {}
            }
        }

        // Record information on the best whole-file entry.
        bestWholeArchiveEntry.setChosenDeltaTransferStrategy(bestWholeArchiveStrategy);
        bestWholeArchiveEntry.setFullEntrySizeBytes(bestWholeArchiveResultSize);
        report.setWholeArchiveEntry(bestWholeArchiveEntry);

        // Now build the best possible per-file-entry patch to compete.
        // Output the central directory.
        int numBytesWritten = 0;
        int totalNumBytesWritten = PatchMagic.getStandardHeader().getBytes("UTF-8").length;
        PatchDirective beginDirective = PatchDirective.BEGIN(
            new BeginMetadata(newArchive.getCentralDirectory()));
        numBytesWritten = patchWriter.write(beginDirective);
        report.setBeginEntry(beginDirective);
        totalNumBytesWritten += numBytesWritten;

        List<PatchGenerationReportEntry> perFileEntries = report.getPerFilePatchEntries();
        for (LocalSectionParts newLSP : newArchive.getLocal().entries()) {
            PatchDirective directive = processEntryInNewArchive(newLSP);
            numBytesWritten = patchWriter.write(directive);
            PatchGenerationReportEntry lastEntry = perFileEntries.get(perFileEntries.size() - 1);
            lastEntry.setFullEntrySizeBytes(numBytesWritten);
            totalNumBytesWritten += numBytesWritten;
        } // for-loop

        if (totalNumBytesWritten < bestWholeArchiveResultSize) {
            report.setWholeArchivePatchUsed(false);
        } else {
            report.setWholeArchivePatchUsed(true);
        }

        report.setTotalPatchSizeBytes(totalNumBytesWritten);
        return report;
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
    private PatchDirective processEntryInNewArchive(LocalSectionParts newLSP)
        throws IOException {
        // First check to see if the entry also exists in the old archive.
        // If it does, attempt to use a patch command instead of NEW.
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

        // Else, totally new resource. No patch possible, generate a "new"
        // directive and return it.
        byte[] rawNewData = newLSP.getFileDataPart().getData();
        int bestCompressedNewSize = sizeInNewArchive(newLSP.getLocalFilePart().getFileName());
        int bestCompressionEngineIdForNew = BuiltInCompressionEngine.NONE.getId();
        byte[] bestCompressedNewOut = rawNewData;
        for (Compressor compressor : compressors) {
            ByteArrayOutputStream compressedNewOut = new ByteArrayOutputStream();
            compressor.compress(new ByteArrayInputStream(rawNewData), compressedNewOut);
            if (compressedNewOut.size() < bestCompressedNewSize) {
                bestCompressedNewSize = compressedNewOut.size();
                bestCompressionEngineIdForNew = compressor.getId();
                bestCompressedNewOut = compressedNewOut.toByteArray();
            }
        }

        report.addEntry(
            null,  // oldPath
            newArchive.getCentralDirectory().getByPath(filePath).getFileName(),
            PatchCommand.NEW,
            BuiltInCompressionEngine.NONE.getId(),
            0,  // oldSizeBytes
            bestCompressionEngineIdForNew,
            bestCompressedNewSize);
        return PatchDirective.NEW(
                new NewMetadata(
                        newLSP.getLocalFilePart(),
                        newLSP.getDataDescriptorPart(),
                        bestCompressionEngineIdForNew,
                        bestCompressedNewOut));
    }

    /**
     * Return the total size, in bytes, of the entry in the new archive having
     * the specified path. This includes the size of the local parts ONLY.
     * 
     * @param path the path to look up
     * @return the size
     */
    private int sizeInNewArchive(String path) {
        return newArchive.getLocal().getByPath(path).getStructureLength();
    }

    /**
     * Return the total size, in bytes, of the entry in the old archive having
     * the specified path. This includes the size of the local parts ONLY.
     * 
     * @param path the path to look up
     * @return the size
     */
    private int sizeInOldArchive(String path) {
        return oldArchive.getLocal().getByPath(path).getStructureLength();
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
                report.addEntry(
                    oldCDF.getFileName(),
                    newCDF.getFileName(),
                    PatchCommand.COPY,
                    compressionEngineIdOf(oldLSP),
                    sizeInOldArchive(oldCDF.getFileName()),
                    compressionEngineIdOf(newLSP),
                    sizeInNewArchive(newCDF.getFileName()));
                return PatchDirective.COPY(
                        (int) oldCDF.getRelativeOffsetOfLocalHeader_32bit());
            }

            // Identical resource except for metadata: REFRESH
            report.addEntry(
                oldCDF.getFileName(),
                newCDF.getFileName(),
                PatchCommand.REFRESH,
                compressionEngineIdOf(oldLSP),
                sizeInOldArchive(oldCDF.getFileName()),
                compressionEngineIdOf(newLSP),
                sizeInNewArchive(newCDF.getFileName()));
            return PatchDirective.REFRESH(
                    (int) oldCDF.getRelativeOffsetOfLocalHeader_32bit(),
                    new RefreshMetadata(
                            newLSP.getLocalFilePart(),
                            newLSP.getDataDescriptorPart()));
        }
        
        // Else, the data has changed between old and new.
        // First, see what the size would be if we had to send the entire new
        // resource, compressed.
        final PatchGenerationReportEntry reportEntry =
            report.addEntry(
                oldCDF.getFileName(),
                newCDF.getFileName(),
                PatchCommand.PATCH,
                compressionEngineIdOf(oldLSP),
                sizeInOldArchive(oldCDF.getFileName()),
                compressionEngineIdOf(newLSP),
                sizeInNewArchive(newCDF.getFileName()));
        byte[] rawNewData = newLSP.getFileDataPart().getData();
        int bestCompressedNewSize = sizeInNewArchive(newCDF.getFileName());
        byte[] bestCompressedNewOut = rawNewData;
        Strategy bestCompressionOnlyStrategy = Strategy.getInstance(
            BuiltInDeltaEngine.NONE.getId(),
            BuiltInCompressionEngine.NONE.getId());
        for (Compressor compressor : compressors) {
            ByteArrayOutputStream compressedNewOut = new ByteArrayOutputStream();
            compressor.compress(new ByteArrayInputStream(rawNewData), compressedNewOut);
            Strategy compressionOnlyStrategy = Strategy.getInstance(
                BuiltInDeltaEngine.NONE.getId(), compressor.getId());
            reportEntry.recordStrategyResult(
                compressionOnlyStrategy, compressedNewOut.size());
            if (compressedNewOut.size() < bestCompressedNewSize) {
                bestCompressedNewSize = compressedNewOut.size();
                bestCompressedNewOut = compressedNewOut.toByteArray();
                bestCompressionOnlyStrategy = compressionOnlyStrategy;
            }
        }

        // Try to generate a more compact representation.
        if (deltaGenerators != null && !deltaGenerators.isEmpty()) {
            PatchDirective directive = generateDelta(
                oldCDF, oldLSP, newCDF, newLSP, bestCompressedNewSize, reportEntry);
            // If a patch directive is available, use it.
            if (directive != null) {
                return directive;
            }
        }

        // Else, all patches are worse than a NEW, so do a NEW instead.
        reportEntry.setCommand(PatchCommand.NEW);
        reportEntry.setChosenFullTransferCompressionEngineId(
            bestCompressionOnlyStrategy.getCompressionEngineId());
        return PatchDirective.NEW(
            new NewMetadata(
                newLSP.getLocalFilePart(),
                newLSP.getDataDescriptorPart(),
                bestCompressionOnlyStrategy.getCompressionEngineId(),
                bestCompressedNewOut));
    }

    /**
     * @param oldCDF the {@link CentralDirectoryFile} entry from the "old"
     * archive
     * @param oldLSP the {@link LocalSectionParts} entry from the "old" archive
     * @param newCDF the {@link CentralDirectoryFile} entry from the "new"
     * archive
     * @param newLSP the {@link LocalSectionParts} entry from the "new" archive
     * @param maxSizeBytes maximum size, in bytes, of the patch for the entry;
     * if the resulting patch is bigger than this, null is returned.
     * @param reportEntry the entry in the patch generation report to be updated
     * based on the delta generation attempt
     * @return a {@link PatchDirective} suitable for output, if and only if
     * a delta can be generated that produces a more compact representation
     * than embedding a copy of the new data.
     * @throws IOException if there is an I/O error encountered while
     * processing the delta
     */
    private PatchDirective generateDelta(final CentralDirectoryFile oldCDF,
        final LocalSectionParts oldLSP, CentralDirectoryFile newCDF,
        LocalSectionParts newLSP, int maxSizeBytes,
        PatchGenerationReportEntry reportEntry)
            throws IOException {

        reportEntry.setDeltaGenerationAttempted(true);
        int bestSize = maxSizeBytes;
        Strategy bestStrategy = null;
        ByteArrayOutputStream bestCompressedDelta = null;
        for (DeltaGenerator deltaGenerator : deltaGenerators) {
            if (!deltaGenerator.accept(oldCDF, oldLSP, newCDF, newLSP)) {
                continue;
            }

            // Run the delta generator on this input
            final ByteArrayOutputStream deltaBuffer = new ByteArrayOutputStream();
            final ByteArrayInputStream oldIn = new ByteArrayInputStream(
                oldLSP.getFileDataPart().getData());
            final ByteArrayInputStream newIn = new ByteArrayInputStream(
                newLSP.getFileDataPart().getData());
            deltaGenerator.makeDelta(oldIn, newIn, deltaBuffer);
    
            final byte[] deltaBytes = deltaBuffer.toByteArray();
            for (Compressor compressor : compressors) {
                final Strategy strategy = Strategy.getInstance(deltaGenerator.getId(), compressor.getId());
                final ByteArrayOutputStream compressedDeltaOut = new ByteArrayOutputStream();
                compressor.compress(new ByteArrayInputStream(deltaBytes), compressedDeltaOut);
                final int compressedDeltaSizeBytes = compressedDeltaOut.size();
                reportEntry.recordStrategyResult(strategy, compressedDeltaSizeBytes);
                if (compressedDeltaSizeBytes < bestSize) {
                    bestSize = compressedDeltaSizeBytes;
                    bestStrategy = strategy;
                    bestCompressedDelta = compressedDeltaOut;
                }
            }
        }

        if (bestSize >= maxSizeBytes) {
            // We failed to produce a more compact representation, give up.
            return null;
        }

        // Else, something was good. Use it.
        reportEntry.setChosenDeltaTransferStrategy(bestStrategy);

        // We produces a more compact delta, so generate a PATCH directive.
        return PatchDirective.PATCH(
                (int) oldCDF.getRelativeOffsetOfLocalHeader_32bit(),
                new PatchMetadata(
                        newLSP.getLocalFilePart(),
                        newLSP.getDataDescriptorPart(),
                        bestStrategy.getDeltaEngineId(),
                        bestStrategy.getCompressionEngineId(),
                        bestCompressedDelta.toByteArray()));
    }

    /**
     * Return the ID of the compression engine used for the given part.
     * @param lsp the part
     * @return the compression engine ID
     */
    private static int compressionEngineIdOf(final LocalSectionParts lsp) {
        if (lsp.getLocalFilePart().getCompressionMethod() == CompressionMethod.DEFLATED) {
            return BuiltInCompressionEngine.DEFLATE.getId();
        }
        return BuiltInCompressionEngine.NONE.getId();
    }
}