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
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import com.google.archivepatcher.delta.DeltaUtils;
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
        options.option("archive-list").isRequired().describedAs(
            "path to a file containing a list of archives to be processed, " +
            "in ascending order by version OR to a directory whose files " +
            "are already lexically sorted in the desired order. When using " +
            "a list file, paths should be either absolute or relative, in " +
            "which case they are relative to the directory in which the " +
            "archive list file resides. In directory mode, all files ending " +
            "in '.patch' or '.patch.deflated' are ignored.");
        options.option("output-directory").describedAs(
            "sets the path to the directory where patch files should be " +
            "written; defaults to the directory that is archive-list or " +
            "the directory containing the archive-list file.");
        options.option("generate-leapfrog-patches").isUnary().describedAs(
            "generate 'leapfrog' patches in addition to the normal sequence");
        options.option("csv").isUnary().describedAs(
            "write csv output to stdout consisting of tuples of the " +
            "following values: [old archive path], [new archive path], " +
            "[patch path], [compressed patch path], [old archive size], " +
            "[new archive size], [patch size], [compressed patch size], " +
            "[bytes saved, uncompressed patch versus new archive], " +
            "[bytes saved, compressed patch versus new archive]");
        options.option("no-compress").isUnary().describedAs(
            "in addition to the regular patch files, normally generates " +
            "patches using DEFLATE compression appending the suffix " +
            "'.deflated' to the generated files. This is useful for " +
            "simulating the effects of HTTP compression on patch transfer, " +
            "or simply for generating patches that are smaller on disk. " +
            "Setting this option disables this feature.");
    }

    private File baseDirectory;
    private final List<String> archivePaths = new ArrayList<String>();
    private File outputDirectory = null;
    private boolean generateLeapfrogs = false;
    private boolean compress = false;

    @Override
    protected void run(MicroOptions options) throws Exception {
        // First read all the paths from the list file.
        String archiveListPath = options.getArg("archive-list");
        File archiveListFile = new File(archiveListPath);
        if (archiveListFile.isFile()) {
            archivePaths.addAll(MiscUtils.readLines(
                MiscUtils.getReadableFile(archiveListPath), '#'));
            baseDirectory = archiveListFile.getParentFile();
            outputDirectory = baseDirectory;
        } else if (archiveListFile.isDirectory()) {
            final SortedSet<String> sorted = new TreeSet<String>();
            archiveListFile.listFiles(new FileFilter() {
                @Override
                public boolean accept(File pathname) {
                    if (!pathname.isFile()) return false;
                    if (pathname.getName().endsWith(".patch")) return false;
                    if (pathname.getName().endsWith(".patch.deflated")) return false;
                    sorted.add(pathname.getName());
                    return true;
                }
            });
            archivePaths.addAll(sorted);
            baseDirectory = archiveListFile;
            outputDirectory = baseDirectory;
        }
        if (options.has("output-directory")) {
            outputDirectory = new File(options.getArg("output-directory"));
        }
        generateLeapfrogs = options.has("generate-leapfrog-patches");
        compress = !options.has("no-compress");
        generateAll(options.has("csv"));
    }

    /**
     * Helper class that records statistics for each patch that is generated.
     */
    private static class PatchStats {
        String oldPath;
        String newPath;
        String uncompressedPatchPath;
        String compressedPatchPath;
        long oldSizeBytes;
        long newSizeBytes;
        long uncompressedPatchSizeBytes;
        long compressedPatchSizeBytes;

        @Override
        public String toString() {
            final long uncompressedPatchSavings = newSizeBytes - uncompressedPatchSizeBytes;
            final long compressedPatchSavings = newSizeBytes - compressedPatchSizeBytes;
            StringBuilder buffer = new StringBuilder();
            buffer.append(oldPath);
            buffer.append(',').append(newPath);
            buffer.append(',').append(uncompressedPatchPath);
            if (compressedPatchPath != null) {
                buffer.append(',').append(compressedPatchPath);
            }
            buffer.append(',').append(oldSizeBytes);
            buffer.append(',').append(newSizeBytes);
            buffer.append(',').append(uncompressedPatchSizeBytes);
            if (compressedPatchPath != null) {
                buffer.append(',').append(compressedPatchSizeBytes);
            }
            buffer.append(',').append(uncompressedPatchSavings);
            if (compressedPatchPath != null) {
                buffer.append(',').append(compressedPatchSavings);
            }
            return buffer.toString();
        }
    }

    /**
     * Generate all patches.
     * @param outputCsv whether or not to output CSV output to stdout as
     * progress is made.
     * @return the list of statistics gathered
     * @throws IOException if anything goes awry
     */
    private List<PatchStats> generateAll(boolean outputCsv) throws IOException {
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
            baseDirectory, archivePaths, generateLeapfrogs);

        final List<PatchStats> statsList = new ArrayList<PatchStats>();

        // Generate each patch, gathering statistics.
        logVerbose("Generating " + patchPairs.size() + " patches...");
        for (final Pair<File> pair : patchPairs) {
            final File patchFile =
                generateOnePatch(outputDirectory, pair, isVerbose());
            PatchStats stats = new PatchStats();
            statsList.add(stats);
            stats.oldPath = pair.value1.getName();
            stats.newPath = pair.value2.getName();
            stats.uncompressedPatchPath = patchFile.getName();
            stats.oldSizeBytes = pair.value1.length();
            stats.newSizeBytes = pair.value2.length();
            stats.uncompressedPatchSizeBytes = patchFile.length();

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
                stats.compressedPatchPath = compressedFile.getName();
                stats.compressedPatchSizeBytes = compressedFile.length();
            }

            if (outputCsv) {
                log(stats.toString());
            }
        }

        return statsList;
    }

    /**
     * Generates one patch.
     * @param outputDirectory the directory in which to generate the patch
     * @param pair the pair of archives to generate a patch for
     * @param verbose whether or not to be verbose
     * @return the patch file generated
     * @throws IOException if there is a problem reading or writing
     */
    final static File generateOnePatch(final File outputDirectory,
        final Pair<File> pair, boolean verbose)throws IOException {
        final String patchName = generatePatchName(pair);
        final File patchFile = new File(outputDirectory, patchName);
        final ArchivePatcher patcher = new ArchivePatcher();
        patcher.setVerbose(verbose);
        patcher.makePatch(pair.value1.getAbsolutePath(),
            pair.value2.getAbsolutePath(),
            patchFile.getAbsolutePath(),
            DeltaUtils.getBuiltInDeltaGenerators(),
            DeltaUtils.getBuiltInCompressors());
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