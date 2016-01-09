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

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.google.archivepatcher.AbstractArchiveTool;
import com.google.archivepatcher.MicroOptions;
import com.google.archivepatcher.util.MiscUtils;

/**
 * A standalone tool to run a {@link CompressTransformer}.
 */
public class CompressTransformerTool extends AbstractArchiveTool {
    /**
     * The suffix used for transformed archives.
     */
    private final static String TRANSFORMED_ARCHIVE_SUFFIX = ".orig";

    /**
     * Main method. For usage instructions, run with "--help".
     * 
     * @param args arguments to the program
     * @throws Exception if anything goes wrong
     */
    public static void main(String... args) throws Exception {
        new CompressTransformerTool().run(args);
    }

    @Override
    public void configureOptions(MicroOptions options) {
        super.configureOptions(options);
        options.option("archive").describedAs(
            "one archive to transform");
        options.option("archive-list").describedAs(
            "path to a file containing a list of archives to be transformed. " +
            "When using a list file, paths should be either absolute or " +
            "relative, in which case they are relative to the directory in " +
            "which the archive list file resides.");
        options.option("jobs").describedAs("run up to this many jobs in " +
            "parallel. The default is 1.");
        options.option("output-dir").isRequired().describedAs(
             "Write transformed archives to the specified output directory " +
             "with the suffix '.orig'.");
        options.option("records-in-dir").describedAs(
            "Read transformation record files for each archive processed " +
            "from the specified directory. The record files should have the " +
            "same names as the archives, but should be suffixed with the " +
            "string '.rcd' instead of '.trx'. Defaults to whatever directory " +
            "the archives are in.");
    }

    @Override
    protected void run(MicroOptions options) throws Exception {
        // Parameter parsing
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

        final int jobs = Integer.parseInt(options.getArg("jobs", "1"));
        final File outputDir = new File(options.getArg("output-dir"));
        final File recordsDir;
        if (options.has("records-in-dir")) {
            recordsDir = new File(options.getArg("records-in-dir"));
        } else {
            recordsDir = null;
        }
        Map<File, Boolean> result = transform(
            archives, jobs, outputDir, recordsDir);

        // Print report for the user
        if (options.has("verbose")) {
            log(result.toString());
        }
    }

    /**
     * Simple helper class to send back multiple parameters from a transform.
     */
    private static final class Result {
        public final File archive;
        public final boolean successful;
        public Result(final File archive, final boolean successful) {
            this.archive = archive;
            this.successful = successful;
        }
    }

    /**
     * Run the transformer with the specified parameters. See command line
     * options for detailed usage instructions.
     * @param archives one or more archives to be transformed
     * @param jobs the number of jobs to run in parallel
     * @param outputDir the directory to output transformed archives to
     * @param recordsDir where to find {@link TransformationRecord} files
     * @return whether or not transformation was successful
     * @throws ExecutionException if there is an error during execution
     * @throws InterruptedException if interrupted while awaiting completion
     */
    public Map<File, Boolean> transform(final List<File> archives,
        final int jobs, final File outputDir, final File recordsDir)
            throws ExecutionException, InterruptedException {
        // Prepare output directory.
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
        if (!outputDir.exists()) {
            throw new RuntimeException("Cannot create output directory: "
                    + outputDir);
        }

        // Generate tasks for each archive
        final List<Callable<Result>> tasks =
                new ArrayList<Callable<Result>>(archives.size());
        for (File archiveFile : archives) {
            // Figure out where the directives are for this archive file
            String archiveName = archiveFile.getName();
            if (archiveName.endsWith(
                UncompressTransformerTool.TRANSFORMED_ARCHIVE_SUFFIX)) {
                archiveName = archiveName.substring(0, archiveName.length() -
                    UncompressTransformerTool.TRANSFORMED_ARCHIVE_SUFFIX
                        .length());
            } else {
                System.err.println("WARNING: archive file doesn't end with " +
                    UncompressTransformerTool.TRANSFORMED_ARCHIVE_SUFFIX +
                    " suffix, is this really a transformed archive?");
            }
            final File recordFile;
            if (recordsDir == null) {
                recordFile = new File(
                    archiveFile.getParent(), archiveName +
                    UncompressTransformerTool.TRANSFORMATION_RECORD_SUFFIX);
            } else {
                recordFile = new File(recordsDir, archiveName +
                    UncompressTransformerTool.TRANSFORMATION_RECORD_SUFFIX);
            }
            // If directives don't exist, fail now.
            if (!recordFile.exists()) {
                throw new RuntimeException("No transformation record found " +
                        "for " + archiveFile.getAbsolutePath() + " at " +
                        recordFile.getAbsolutePath());
            }
            // Figure out where to store the output
            final File archiveOutputFile = new File(outputDir,
                    archiveName + TRANSFORMED_ARCHIVE_SUFFIX);
            // Finally, generate the task.
            tasks.add(new CompressTransformTask(
                    archiveFile, recordFile, archiveOutputFile));
        }

        // Submit tasks to the pool and wait for completion
        ExecutorService executor = Executors.newFixedThreadPool(
            Math.min(archives.size(), jobs));
        Map<File, Boolean> result = new HashMap<File, Boolean>();
        try {
            List<Future<Result>> results = executor.invokeAll(tasks);
            for (Future<Result> future : results) {
                Result oneResult = null;
                try {
                    oneResult = future.get();
                } catch (Exception e) {
                    // Unhandled exception in code, fail.
                    throw new RuntimeException(e);
                }
                result.put(oneResult.archive, oneResult.successful);
            }
        } finally {
            executor.shutdownNow();
        }
        return result;
    }

    private class CompressTransformTask
    implements Callable<Result> {
        /**
         * The archive to read.
         */
        private final File archiveIn;

        /**
         * The archive to write.
         */
        private final File archiveOut;

        /**
         * The file containing the {@link TransformationRecord}.
         */
        private final File recordFile;

        /**
         * Create a new task to work on the specified inputs.
         * @param archiveIn the archive to read
         * @param recordFile the file containing the
         * {@link TransformationRecord}
         * @param archiveOut the archive to write
         */
        public CompressTransformTask(File archiveIn, File recordFile,
                File archiveOut) {
            this.archiveIn = archiveIn;
            this.recordFile = recordFile;
            this.archiveOut = archiveOut;
        }

        public Result call() throws Exception {
            CompressTransformer transformer = new CompressTransformer();
            if (isVerbose()) {
                log("Transforming and verifying archive:");
                log("  Input archive:         " + archiveIn.getAbsolutePath());
                log("  Transformation record: " + recordFile.getAbsolutePath());
            }
            try {
                transformer.transform(archiveIn, recordFile, archiveOut);
                if (isVerbose()) {
                    log("Transformation complete and verified correct:");
                    log("  Transformed archive:   " +
                    archiveOut.getAbsolutePath());
                }
                return new Result(archiveIn, true);
            } catch (Exception e) {
                log("Transformation failed!");
                e.printStackTrace();
                return new Result(archiveIn, false);
            }
        }
    }
}
