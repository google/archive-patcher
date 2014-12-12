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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import com.google.archivepatcher.util.MiscUtils;
import com.google.archivepatcher.util.Pair;

/**
 * Given an ordered series of input archives, produces a series of patches that
 * convert each input archive to its successor.
 * <p>
 * Example:
 * Given the ordered list of archives A1, A2, A3 this tool produces patches
 * P1_2 and P2_3 which are capable of patching A1 to A2 and A2 to A3,
 * respectively. Optionally the tool can also be configured to output "leapfrog"
 * patches that will transform any input archive to any <em>later</em> archive.
 * Continuing the example, the leapfrog patch P1_3 is generated to convert A1
 * to A3 directly, skipping over A2.
 */
public class SequencePatchGenerator extends AbstractArchiveTool {

    /**
     * Main method. For usage instructions, run with "--help".
     * 
     * @param args arguments to the program
     * @throws Exception if anything goes wrong
     */
    public static void main(String... args) throws Exception {
        new SequencePatchGenerator().run(args);
    }

    @Override
    public void configureOptions(MicroOptions options) {
        super.configureOptions(options);
        options.option("archive-list-file").isRequired().describedAs(
            "path to a file containing a list of archives to be processed, " +
            "in ascending order by version. Paths should be either absolute " +
            "or relative, in which case they are relative to the directory " +
            "in which the archive list file resides.");
        options.option("output-directory").describedAs(
            "sets the path to the directory where patch files should be " +
            "written; defaults to the current working directory.");
        options.option("generate-leapfrog-patches").isUnary().describedAs(
            "generate 'leapfrog' patches in addition to the normal sequence");
        options.option("delta-class").describedAs(
            "the name of a class to use to generate deltas; must implement " +
            "the interface: " + DeltaGenerator.class.getName());
        options.option("compress").isUnary().describedAs(
            "in addition to the regular patch files, generate compressed " +
            "patches using DEFLATE compression and append the suffix " +
            "'.deflated' to the generated files. This is useful for " +
            "simulating the effects of HTTP compression on patch transfer, " +
            "or simply for generating patches that are smaller on disk.");
    }

    private String archiveListPath = null;
    private File archiveListFile = null;
    private String outputPath = null;
    private File outputDirectory = null;
    private String deltaClassName = null;
    private boolean generateLeapfrogs = false;
    private boolean compress = false;

    @Override
    protected void run(MicroOptions options) throws Exception {
        // First read all the paths from the list file.
        archiveListPath = options.getArg("archive-list-file");
        archiveListFile = MiscUtils.getReadableFile(archiveListPath);
        outputPath = options.getArg("output-directory", ".");
        outputDirectory = new File(outputPath);
        deltaClassName = options.getArg("delta-class",  null);
        generateLeapfrogs = options.has("generate-leapfrog-patches");
        compress = options.has("compress");
        generateAll();
    }

    /**
     * Generate all patches.
     * @throws IOException if anything goes awry
     */
    private void generateAll() throws IOException {
        final List<String> archivePaths = Collections.unmodifiableList(
            MiscUtils.readLines(archiveListFile, '#'));
        if (archivePaths.size() < 2) {
            throw new MicroOptions.OptionException(
                "List file must contain at least two entries!");
        }

        // Prepare the output directory.
        outputDirectory.mkdirs();
        if (!outputDirectory.exists()) {
            throw new IOException("Can't create output directory: " +
                outputDirectory.getAbsolutePath());
        }
        if (!outputDirectory.canWrite()) {
            throw new IOException("Output directory not writable: " +
                outputDirectory.getAbsolutePath());
        }

        // Now generate the list of all the patches we will create.
        final List<Pair<File>> patchPairs = generatePatchPairs(
            archiveListFile.getParentFile(), archivePaths, generateLeapfrogs);

        // Generate each patch, gathering statistics.
        logVerbose("Generating " + patchPairs.size() + " patches...");
        for (final Pair<File> pair : patchPairs) {
            final File patchFile =
                generateOnePatch(outputDirectory, deltaClassName, pair,
                    isVerbose());

            // Stats!
            logVerbose(patchFile.getName() + ": " + patchFile.length() +
                " bytes");
            if (compress) {
                // also compress the file using DEFLATE.
                final File compressedFile = new File(
                    patchFile.getAbsoluteFile() + ".deflated");
                MiscUtils.deflate(patchFile, compressedFile);
                // Stats!
                logVerbose(compressedFile.getName() + ": " +
                    compressedFile.length() + " bytes");
            }
        }
    }

    /**
     * Generates one patch.
     * @param outputDirectory the directory in which to generate the patch
     * @param deltaClassName the name of the delta class to be used
     * @param pair the pair of archives to generate a patch for
     * @param verbose whether or not to be verbose
     * @return the patch file generated
     * @throws IOException if there is a problem reading or writing
     */
    final static File generateOnePatch(final File outputDirectory,
        final String deltaClassName, final Pair<File> pair, boolean verbose)
            throws IOException {
        final String patchName = generatePatchName(pair);
        final File patchFile = new File(outputDirectory, patchName);
        final ArchivePatcher patcher = new ArchivePatcher();
        patcher.setVerbose(verbose);
        final DeltaGenerator deltaGenerator =
            MiscUtils.maybeCreateInstance(
                deltaClassName, DeltaGenerator.class);
        patcher.makePatch(pair.value1.getAbsolutePath(),
            pair.value2.getAbsolutePath(),
            patchFile.getAbsolutePath(), deltaGenerator);
        return patchFile;
    }

    /**
     * Given a pair of files, generate a file name representing a patch that
     * converts the first entry in the pair to the second.
     * @param pair the pair to generate a patch name for
     * @return the name of the patch
     */
    final static String generatePatchName(Pair<File> pair) {
        return pair.value1.getName() +
            "_to_" + pair.value2.getName() + ".patch";
    }

    /**
     * Generates the list of pairs of archives to generate patches for.
     * @param archiveListBaseDir the base directory in which the archive list
     * is located, used to generate absolute file paths for relative entries
     * @param archiveList the list of archives to process
     * @param generateLeapfrogs whether or not to generate "leapfrog" patches
     * @return the list of all pairs of archives for which patches must be
     * generated
     * @throws IOException if any file does not exist
     */
    final static List<Pair<File>> generatePatchPairs(
        final File archiveListBaseDir, final List<String> archiveList,
        final boolean generateLeapfrogs) throws IOException {
        final List<Pair<File>> result =
            new LinkedList<Pair<File>>();
        // outerSeries is guaranteed to have at least 2 entries.
        Iterator<String> outerSeries = archiveList.iterator();
        File previousArchive = getReadableFile(
            archiveListBaseDir, outerSeries.next());
        while (outerSeries.hasNext()) {
            final File nextArchive = getReadableFile(
                archiveListBaseDir, outerSeries.next());
            result.add(new Pair<File>(previousArchive, nextArchive));
            previousArchive = nextArchive;
        }

        if (generateLeapfrogs) {
            // We must also generate patches for all versions AFTER the "next"
            // version. This is a bit more complicated.
            final List<String> arrayList = new ArrayList<String>(archiveList);
            for (int x=0; x<arrayList.size(); x++) {
                previousArchive = getReadableFile(
                    archiveListBaseDir, arrayList.get(x));
                for (int y=x+2; y<arrayList.size(); y++) {
                    File nextArchive = getReadableFile(
                        archiveListBaseDir, arrayList.get(y));
                    result.add(new Pair<File>(previousArchive, nextArchive));
                }
            }
        }
        return result;
    }

    private final static File getReadableFile(
        final File baseDirForRelative, String path) throws IOException {
        File file = new File(path);
        if (!file.isAbsolute()) {
            path = baseDirForRelative.getAbsolutePath() + File.separator + path;
        }
        return MiscUtils.getReadableFile(path);
    }
}