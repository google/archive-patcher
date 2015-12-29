// Copyright 2015 Google Inc. All rights reserved.
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

package com.google.archivepatcher.tools.reassembler;

import com.google.archivepatcher.AbstractArchiveTool;
import com.google.archivepatcher.Archive;
import com.google.archivepatcher.MicroOptions;
import com.google.archivepatcher.compat.Implementation;
import com.google.archivepatcher.compression.JreDeflateParameters;
import com.google.archivepatcher.meta.CompressionMethod;
import com.google.archivepatcher.parts.CentralDirectoryFile;
import com.google.archivepatcher.parts.CentralDirectorySection;
import com.google.archivepatcher.parts.LocalFile;
import com.google.archivepatcher.parts.LocalSectionParts;
import com.google.archivepatcher.tools.diviner.CompressionDiviner;
import com.google.archivepatcher.tools.diviner.DeflateInfo;
import com.google.archivepatcher.util.MiscUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.ZipFile;

/**
 * Reassemble the contents of an archive based on data from the
 * {@link CompressionDiviner}.
 */
// TODO: Fix up all the various little holes here so that the exact same archive
// will be produced even if there are, e.g., holes between entries in the
// archive or extraneous data at the end.
public class Reassembler extends AbstractArchiveTool {
    /**
     * The suffix used for output files.
     */
    private final static String REASSEMBLED_SUFFIX = ".reassembled";

    /**
     * Main method. For usage instructions, run with "--help".
     * 
     * @param args arguments to the program
     * @throws Exception if anything goes wrong
     */
    public static void main(String... args) throws Exception {
        new Reassembler().run(args);
    }

    @Override
    public void configureOptions(MicroOptions options) {
        super.configureOptions(options);
        options.option("archive").describedAs(
            "one archive to dump information for");
        options.option("archive-list").describedAs(
            "path to a file containing a list of archives to be processed. " +
            "When using a list file, paths should be either absolute or " +
            "relative, in which case they are relative to the directory in " +
            "which the archive list file resides.");
        options.option("jobs").describedAs("run up to this many jobs in " +
            "parallel. The default is 1.");
        options.option("output-dir").isRequired().describedAs(
             "Write reassembled archives to the specified output directory " +
             "with the suffix '.reassembled'.");
        options.option("directives-in-dir").describedAs(
            "Read directives file for each archive processed from the " +
            "specified directory. The directives files should have the " +
            "same names as the archives, but should be suffixed with the " +
            "string '.directives'. Defaults to whatever directory the " +
            "archives are in.");
    }

    @Override
    protected void run(MicroOptions options) throws Exception {
        if (!options.has("archive") && !options.has("archive-list")) {
            throw new IllegalArgumentException("specify one of --archive " +
                "or --archive-list");
        }
        if (options.has("archive") && options.has("archive-list")) {
            throw new IllegalArgumentException("specify one of --archive " +
                "or --archive-list, but not both");
        }
        final List<File> archives;
        if (options.has("archive")) {
            archives = Collections.singletonList(
                new File(options.getArg("archive")));
        } else {
            archives = MiscUtils.getFileList(options.getArg("archive-list"));
        }

        // Prepare output directory.
        final File outputDir = new File(options.getArg("output-dir"));
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
        if (!outputDir.exists()) {
            throw new RuntimeException("Cannot create output direcotry: "
                    + options.getArg("output-dir"));
        }

        // Find the directives directory if it was specified
        final File directivesDir;
        if (options.has("directives-in-dir")) {
            directivesDir = new File(options.getArg("directives-in-dir"));
        } else {
            directivesDir = null;
        }

        // Generate tasks for each archive
        final List<Callable<ReassemblyStats>> tasks =
                new ArrayList<Callable<ReassemblyStats>>(archives.size());
        for (File archiveFile : archives) {
            // Figure out where the directives are for this archive file
            final File directivesFile;
            if (directivesDir == null) {
                directivesFile = new File(archiveFile.getParent(),
                        archiveFile.getName() + ".directives");
            } else {
                directivesFile = new File(directivesDir,
                        archiveFile.getName() + ".directives");
            }
            // If directives don't exist, fail now.
            if (!directivesFile.exists()) {
                throw new RuntimeException("No directives found for " +
                        archiveFile.getAbsolutePath() + " at " +
                        directivesFile.getAbsolutePath());
            }
            // Figure out where to store the output
            final File outputFile = new File(outputDir,
                    archiveFile.getName() + REASSEMBLED_SUFFIX);
            // Finally, generate the task.
            tasks.add(new ReassembleTask(
                    archiveFile, directivesFile, outputFile));
        }

        // Submit tasks to the pool and wait for completion
        final int jobs = Integer.parseInt(options.getArg("jobs", "1"));
        ExecutorService executor = Executors.newFixedThreadPool(
            Math.min(archives.size(), jobs));
        final List<ReassemblyStats> allStats =
            new ArrayList<ReassemblyStats>(archives.size());
        try {
            List<Future<ReassemblyStats>> results =
                executor.invokeAll(tasks);
            for (Future<ReassemblyStats> future : results) {
                allStats.add(future.get());
            }
        } finally {
            executor.shutdownNow();
        }

        // Merge all stats
        ReassemblyStats finalStats = new ReassemblyStats();
        for (ReassemblyStats oneResult : allStats) {
            finalStats.accumulate(oneResult);
        }

        // Print report for the user
        log(finalStats.toString());
    }

    /**
     * Parse a directives file and return a map whose keys are file paths
     * within an archive and whose values are the parameters necessary to
     * reproduce the compressed artifact at that path.
     * @param directivesFile the path to the directives file to read
     * @return the mapping described above
     * @throws IOException if unable to parse the file
     */
    private static Map<String, JreDeflateParameters> parseDirectives(
            File directivesFile) throws IOException {
        final Map<String, JreDeflateParameters> deflateParametersByPath =
                new HashMap<String, JreDeflateParameters>();
        final List<String> directiveLines = MiscUtils.readLines(
                directivesFile, '#');
        for (final String directiveLine : directiveLines) {
            // <entry_path>,<tech>[,<level>,<strategy>,<nowrap>]
            final String[] parts = directiveLine.split(",");
            final String path = parts[0];
            final String tech = parts[1];
            if (tech.equals("unknown")) {
                // Cannot recompress this file (unknown non-deflate tech),
                // skip it. Stats will be derived during reassembly.
            } else if (tech.equals("no_compression")) {
                // Not to be compressed, skip it.
                // Stats will be derived during reassembly.
            } else if (tech.equals(DeflateInfo.UNKNOWN_DEFLATE)) {
                // Cannot recompress this file (unknown deflate
                // implementation), skip it.
                // Stats will be derived during reassembly.
            } else if (tech.equals(DeflateInfo.JRE_DEFLATE)) {
                // CAN recompress, track parameters for use later.
                final int level = Integer.parseInt(parts[2]);
                final int strategy = Integer.parseInt(parts[3]);
                final boolean nowrap = Boolean.parseBoolean(parts[4]);
                final JreDeflateParameters parameters =
                        new JreDeflateParameters(level, strategy, nowrap);
                deflateParametersByPath.put(path, parameters);
            }
        }
        return deflateParametersByPath;
    }

    /**
     * Reassemble one archive.
     * @param archiveIn the archive to reassemble
     * @param deflateParametersByPath map from {@link #parseDirectives(File)}
     * @param out the file to write the reassembled archive to
     * @return the stats obtained during reassembly
     * @throws IOException if anything goes wrong reading or writing the files
     */
    private ReassemblyStats reassemble(final File archiveIn,
            final Map<String, JreDeflateParameters> deflateParametersByPath,
            DataOutputStream out) throws IOException {
        // Check for ability to track thread CPU time
        ThreadMXBean mxbean = null;
        boolean trackCompressionTime = false;
        try {
            mxbean = ManagementFactory.getThreadMXBean();
            trackCompressionTime =
                    mxbean.isCurrentThreadCpuTimeSupported();
        } catch (Exception ignored) {
            // Nothing to be done
        }
        if (!trackCompressionTime) {
            log("warning: thread time tracking not available");
            mxbean = null;
        }

        // Parse input archive to get access to all the low-level bits that are
        // needed to reproduce the original file.
        // TODO: make this more efficient and use lazy parts for the data to
        // avoid the cheat below. Then move this code into the timed section.
        // TODO: Handle gaps and such in the zip. For now assume every bit is
        final Archive archive = Archive.fromFile(
                archiveIn.getAbsolutePath());

        // Track start time
        final ReassemblyStats result = new ReassemblyStats();
        result.totalArchiveBytes = archiveIn.length();
        long startNanos = -1;
        if (mxbean != null) {
            startNanos = mxbean.getCurrentThreadCpuTime();
        }

        // Cheat: Use a ZipFile as a proxy to get the uncompressed data, since
        // it will allow (and indeed require) streaming decompression, which is
        // what a better implementation in archive-patcher would do. This will
        // provide more accurate timing. Assuming the archive is sane, and has
        // exactly one entry in both the central directory and the local
        // section for each file, this will work fine.
        final ZipFile dataProxy = new ZipFile(archiveIn.getAbsolutePath());
        final byte[] buffer = new byte[32*1024];

        if (isVerbose()) {
            logVerbose("Writing local section");
        }
        try {
            // Iterate in the original file order, writing each part back to disk
            // in serial to produce an identical archive.
            for (final LocalSectionParts lsp : archive.getLocal().entries()) {
                final JreDeflateParameters deflateParameters =
                        deflateParametersByPath.get(
                                lsp.getLocalFilePart().getFileName());
                reassembleLocalSectionParts(
                        result, mxbean, deflateParameters, out, dataProxy,
                        buffer, lsp);
            }
        } finally {
            dataProxy.close();
        }

        if (isVerbose()) {
            logVerbose("Writing central directory");
        }
        final CentralDirectorySection cds = archive.getCentralDirectory();
        for (final CentralDirectoryFile cdf : cds.entries()) {
            cdf.write(out);
        }
        cds.getEocd().write(out);
        if (mxbean != null) {
            final long endNanos = mxbean.getCurrentThreadCpuTime();
            final long elapsedNanos = endNanos - startNanos;
            result.totalNanosRebuilding += elapsedNanos;
        }
        return result;
    }

    /**
     * Reassemble the {@link LocalSectionParts} for one entry.
     * @param stats stats object to accumulate into
     * @param mxbean the bean used for measuring time
     * @param deflateParameters deflate configuration information, if relevant
     * @param out the stream for the archive being written
     * @param dataProxy a zip file being used to access the file content lazily
     * (for more realistic timing)
     * @param buffer the buffer to use for moving bytes around
     * @param lsp the local section parts being processed
     * @throws IOException if anything goes wrong
     */
    private void reassembleLocalSectionParts(
        final ReassemblyStats stats,
        final ThreadMXBean mxbean,
        final JreDeflateParameters deflateParameters,
        final DataOutputStream out, final ZipFile dataProxy,
        final byte[] buffer, final LocalSectionParts lsp)
        throws IOException {

        // Write the header for the entry
        final LocalFile lf = lsp.getLocalFilePart();
        lf.write(out);

        // Check if the resource was deflated and, if so, recompress it.
        long dataStartNanos = 0L;
        long dataEndNanos = 0L;
        if (deflateParameters != null) {
            if (isVerbose()) {
                logVerbose("Recompressing " + lf.getFileName());
            }
            // Prepare a compressor to re-compress the data
            // TODO: Add streaming support to file part.
            final Deflater deflater = new Deflater(
                    deflateParameters.level, deflateParameters.nowrap);
            deflater.setStrategy(deflateParameters.strategy);
            final InputStream oldInput = dataProxy.getInputStream(
                    dataProxy.getEntry(lf.getFileName()));
            final BufferedInputStream bufferedOldIn = 
                    new BufferedInputStream(oldInput);
            final DeflaterOutputStream deflateOut =
                    new DeflaterOutputStream(out, deflater, 16384);
            if (mxbean != null) {
                dataStartNanos = mxbean.getCurrentThreadCpuTime();
            }
            int numRead = 0;
            while ((numRead = bufferedOldIn.read(buffer)) >= 0) {
                deflateOut.write(buffer, 0, numRead);
            }
            deflateOut.finish(); // DO NOT close the underlying stream yet!
            deflater.end();
            if (mxbean != null) {
                dataEndNanos = mxbean.getCurrentThreadCpuTime();
            }
        } else {
            // Just copy the data
            if (isVerbose()) {
                logVerbose("Copying " + lf.getFileName());
            }
            if (mxbean != null) {
                dataStartNanos = mxbean.getCurrentThreadCpuTime();
            }
            out.write(lsp.getFileDataPart().getData());
            if (mxbean != null) {
                dataEndNanos = mxbean.getCurrentThreadCpuTime();
            }
        }

        // If a data descriptor was used, write it too.
        if (lsp.hasDataDescriptor()) {
            lsp.getDataDescriptorPart().write(out);
        }

        // Gather timing data
        final long dataNanos;
        if (mxbean != null) {
            dataNanos = dataEndNanos - dataStartNanos;
        } else {
            dataNanos = 0;
        }

        // Record statistics
        if (deflateParameters != null) {
            stats.accumulateRecompressStats(
                    lf.getFileName(), deflateParameters,
                    lf.getCompressedSize_32bit(),
                    lf.getUncompressedSize_32bit(), dataNanos);
        } else {
            final CompressionMethod method = lf.getCompressionMethod();
            final ReassemblyTechnique technique;
            if (method == CompressionMethod.DEFLATED) {
                technique =
                        ReassemblyTechnique.COPY_UNKNOWN_DEFLATE_PARAMETERS;
            } else if (method == CompressionMethod.NO_COMPRESSION) {
                technique = ReassemblyTechnique.COPY_NO_COMPRESSION;
            } else {
                technique = ReassemblyTechnique.COPY_UNKNOWN_TECH;
            }
            stats.accumulateCopyStats(lf.getFileName(), technique,
                    lf.getCompressedSize_32bit(),
                    lf.getUncompressedSize_32bit(),
                    dataNanos);
        }
    }

    /**
     * Process one archive with its directives and reassemble it to the
     * specified destination.
     */
    private class ReassembleTask implements Callable<ReassemblyStats> {
        /**
         * The archive to read.
         */
        private final File archiveIn;

        /**
         * The archive to write, nominally identical to archiveIn when done.
         */
        private final File archiveOut;

        /**
         * Directives file containing deflate configuration information.
         */
        private final File directivesFile;

        /**
         * Create a new task to work on the specified inputs.
         * @param archiveIn the archive to read
         * @param directivesFile the directives file with deflate configuration
         * information for recompression
         * @param archiveOut the archive to write
         */
        public ReassembleTask(File archiveIn, File directivesFile,
                File archiveOut) {
            this.archiveIn = archiveIn;
            this.directivesFile = directivesFile;
            this.archiveOut = archiveOut;
        }

        @Implementation
        public ReassemblyStats call() throws Exception {
            // Parse directives to determine what parameters will be used for
            // each resource
            final Map<String, JreDeflateParameters> deflateParametersByPath =
                    parseDirectives(directivesFile);

            // Load the archive and prepare to walk it.
            final DataOutputStream dataOut = new DataOutputStream(
                    new BufferedOutputStream(new FileOutputStream(archiveOut)));
            try {
                return reassemble(archiveIn, deflateParametersByPath, dataOut);
            } finally {
                try {
                    dataOut.flush();
                    dataOut.close();
                } catch (Exception e) {
                    // Ignore.
                }
            }
        }
        
    }
}
