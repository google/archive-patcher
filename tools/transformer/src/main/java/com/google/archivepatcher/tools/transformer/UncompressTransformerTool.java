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
 * A standalone tool to run a {@link UncompressTransformer}.
 */
public class UncompressTransformerTool extends AbstractArchiveTool {
    /**
     * The suffix used for transformed archives.
     */
    public final static String TRANSFORMED_ARCHIVE_SUFFIX = ".trx";

    /**
     * The suffix used for storing the {@link TransformationRecord}.
     */
    public final static String TRANSFORMATION_RECORD_SUFFIX = ".rcd";

    /**
     * Main method. For usage instructions, run with "--help".
     * 
     * @param args arguments to the program
     * @throws Exception if anything goes wrong
     */
    public static void main(String... args) throws Exception {
        new UncompressTransformerTool().run(args);
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
             "with the suffix '.trx', along with transformation records " +
             "with the suffix '.rcd'.");
        options.option("directives-in-dir").describedAs(
            "Read directives file for each archive processed from the " +
            "specified directory. The directives files should have the " +
            "same names as the archives, but should be suffixed with the " +
            "string '.directives'. Defaults to whatever directory the " +
            "archives are in.");
        options.option("verify").isUnary().describedAs(
            "Verify that the transformation can be reversed to re-obtain the " +
            "original archive. This may add significant time to " +
            "transformation.");
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
        final boolean verify = options.has("verify");
        final File outputDir = new File(options.getArg("output-dir"));
        final File directivesDir;
        if (options.has("directives-in-dir")) {
            directivesDir = new File(options.getArg("directives-in-dir"));
        } else {
            directivesDir = null;
        }
        Map<File, TransformationRecord> result = transform(
            archives, jobs, outputDir, directivesDir, verify);

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
        public final TransformationRecord record;
        public Result(final File archive, final TransformationRecord record) {
            this.archive = archive;
            this.record = record;
        }
    }

    /**
     * Run the transformer with the specified parameters. See command line
     * options for detailed usage instructions.
     * @param archives one or more archives to be transformed
     * @param jobs the number of jobs to run in parallel
     * @param outputDir the directory to output transformed archives to
     * @param directivesDir where to find directives files
     * @param verify if true, verify that the transformation can be reversed to
     * re-obtain the original archive
     * @return the results of the transformation processes
     * @throws ExecutionException if there is an error during execution
     * @throws InterruptedException if interrupted while awaiting completion
     */
    public Map<File, TransformationRecord> transform(final List<File> archives,
        final int jobs, final File outputDir, final File directivesDir,
        final boolean verify) throws ExecutionException, InterruptedException {
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
            final File archiveOutputFile = new File(outputDir,
                    archiveFile.getName() + TRANSFORMED_ARCHIVE_SUFFIX);
            final File recordOutputFile = new File(outputDir,
                    archiveFile.getName() + TRANSFORMATION_RECORD_SUFFIX);
            // Finally, generate the task.
            tasks.add(new UncompressTransformTask(
                    archiveFile, directivesFile, archiveOutputFile,
                    recordOutputFile, verify));
        }

        // Submit tasks to the pool and wait for completion
        ExecutorService executor = Executors.newFixedThreadPool(
            Math.min(archives.size(), jobs));
        Map<File, TransformationRecord> result =
            new HashMap<File, TransformationRecord>();
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
                result.put(oneResult.archive, oneResult.record);
            }
        } finally {
            executor.shutdownNow();
        }
        return result;
    }

    private class UncompressTransformTask
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
         * Where to store the {@link TransformationRecord}.
         */
        private final File recordOut;

        /**
         * Directives file containing deflate configuration information.
         */
        private final File directivesFile;

        /**
         * If true, verify that the transformed file has the same SHA256 as the
         * original.
         */
        private final boolean verify;

        /**
         * Create a new task to work on the specified inputs.
         * @param archiveIn the archive to read
         * @param directivesFile the directives file with deflate configuration
         * information
         * @param archiveOut the archive to write
         * @param recordOut where to store the transformation record
         * @param verify if true, verify that the transformation can be reversed
         * to obtain the original archive.
         */
        public UncompressTransformTask(File archiveIn, File directivesFile,
                File archiveOut, File recordOut, boolean verify) {
            this.archiveIn = archiveIn;
            this.directivesFile = directivesFile;
            this.archiveOut = archiveOut;
            this.recordOut = recordOut;
            this.verify = verify;
        }

        public Result call() throws Exception {
            UncompressTransformer transformer = new UncompressTransformer();
            if (isVerbose()) {
                log("Transforming archive:");
                log("  Input archive: " + archiveIn.getAbsolutePath());
                log("  Directives:    " + directivesFile.getAbsolutePath());
                
            }
            TransformationRecord result = transformer.transform(
                archiveIn, directivesFile, archiveOut, recordOut);
            if (isVerbose()) {
                log("Transformation complete:");
                log("  Transformed archive:   " + archiveOut.getAbsolutePath());
                log("  Transformation record: " + recordOut.getAbsolutePath());
            }
            if (verify) {
                if (isVerbose()) {
                    log("Verifying that transform is invertible...");
                }
                CompressTransformer inverse = new CompressTransformer();
                File temp = new File(archiveIn.getAbsoluteFile() + ".tmp");
                try {
                    inverse.transform(archiveOut, recordOut, temp);
                    if (isVerbose()) {
                        log("Transform is invertible.");
                    }
                }
                catch(IllegalStateException e) {
                    throw new RuntimeException("Transform not invertible", e);
                } finally {
                    temp.delete();
                }
            }
            return new Result(archiveIn, result);
        }
    }
}
