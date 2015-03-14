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

package com.google.archivepatcher;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.Deflater;
import java.util.zip.ZipException;

import com.google.archivepatcher.compression.DeflateCompressor;
import com.google.archivepatcher.compression.DeflateUncompressor;
import com.google.archivepatcher.meta.CompressionMethod;
import com.google.archivepatcher.parts.LocalFile;
import com.google.archivepatcher.parts.LocalSectionParts;
import com.google.archivepatcher.util.MiscUtils;


/**
 * Tries to divine information about the compression used upon every compressed
 * entry in an archive.
 */
public class CompressionDiviner extends AbstractArchiveTool {

    /**
     * Parameters for running a deflate compressor in its default mode.
     */
    private final static JreDeflateParameters DEFAULT_JRE_DEFLATE_PARAMETERS =
        new JreDeflateParameters(6, Deflater.DEFAULT_STRATEGY, false);

    /**
     * Parameters for running a deflate compressor in best-compression mode.
     */
    private final static JreDeflateParameters
        BEST_COMPRESSION_JRE_DEFLATE_PARAMETERS =
            new JreDeflateParameters(9, Deflater.DEFAULT_STRATEGY, false);

    /**
     * Main method. For usage instructions, run with "--help".
     * 
     * @param args arguments to the program
     * @throws Exception if anything goes wrong
     */
    public static void main(String... args) throws Exception {
        new CompressionDiviner().run(args);
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
        options.option("csv-stats-only").isUnary().describedAs(
            "if set, be very quiet and only print comma-separated-value stats");
        options.option("jobs").describedAs("run up to this many jobs in " +
            "parallel. The default is 10.");
        options.option("superbrute").isUnary().describedAs(
            "disable all speedups and check every possibility exhaustively");
    }

    @Override
    protected void run(MicroOptions options) throws Exception {
        final boolean csvStatsOnly = options.has("csv-stats-only");
        final boolean superBrute = options.has("superbrute");
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

        final List<Callable<DivinedCompressionStats>> tasks =
            new ArrayList<Callable<DivinedCompressionStats>>(archives.size());
        for (File archiveFile : archives) {
            tasks.add(new DivinationTask(archiveFile, superBrute));
        }
        final int jobs = Integer.parseInt(options.getArg("jobs", "10"));
        ExecutorService executor = Executors.newFixedThreadPool(
            Math.min(archives.size(), jobs));
        final List<DivinedCompressionStats> allStats =
            new ArrayList<DivinedCompressionStats>(archives.size());
        try {
            List<Future<DivinedCompressionStats>> results =
                executor.invokeAll(tasks);
            for (Future<DivinedCompressionStats> future : results) {
                allStats.add(future.get());
            }
        } finally {
            executor.shutdownNow();
        }

        if (csvStatsOnly) {
            log(DivinedCompressionStats.getCsvHeaderRow());
        }
        for (DivinedCompressionStats stats : allStats) {
            if (csvStatsOnly) {
                log(stats.getCsvReport());
            } else {
                log(stats.getTextReport());
            }
        }
        if (allStats.size() > 1) {
            // Summary row.
            DivinedCompressionStats summary = new DivinedCompressionStats(
                "summary totals");
            for (DivinedCompressionStats stats : allStats) {
                summary.numMatchedCompressedBytes += stats.numMatchedCompressedBytes;
                summary.numMatchedCompressedEntries += stats.numMatchedCompressedEntries;
                summary.totalCompressedBytes += stats.totalCompressedBytes;
                summary.totalCompressedEntries += stats.totalCompressedEntries;
                summary.totalEntries += stats.totalEntries;
                summary.totalUncompressedBytes += stats.totalUncompressedBytes;
                summary.totalUncompressedEntries += stats.totalUncompressedEntries;
            }
            if (csvStatsOnly) {
                log(summary.getCsvReport());
            } else {
                log("\n");
                log("Summary Totals:");
                log(summary.getTextReport());
            }
        }
    }

    private class DivinationTask implements Callable<DivinedCompressionStats> {
        private final File file;
        private final boolean superBrute;
        public DivinationTask(File file, boolean superBrute) {
            this.file = file;
            this.superBrute = superBrute;
        }
        @Override
        public DivinedCompressionStats call() throws Exception {
            return divineCompressionStats(file, superBrute);
        }
        
    }
    /**
     * Process an archive and divine compression statistics from it.
     * @param archiveFile the archive to process
     * @param superBrute disable all speedup optimizations and apply every
     * possible strategy at every possible compression level with every possible
     * wrapping. Generally insane, but provided for sanity checking the
     * optimizations.
     * @return the stats
     * @throws IOException if anything goes wrong
     */
    public DivinedCompressionStats divineCompressionStats(File archiveFile,
        boolean superBrute)
        throws IOException {
        final Archive archive = Archive.fromFile(archiveFile.getAbsolutePath());
        final DivinedCompressionStats stats = new DivinedCompressionStats(
            archiveFile.getAbsolutePath());
        stats.superBrute = superBrute;
        for (LocalSectionParts lsp : archive.getLocal().entries()) {
            stats.totalEntries++;
            LocalFile lf = lsp.getLocalFilePart();
            DeflateInfo info = null;
            if (lf.getCompressionMethod() == CompressionMethod.NO_COMPRESSION) {
                stats.totalUncompressedEntries++;
                stats.totalUncompressedBytes += lf.getUncompressedSize_32bit();
            } else {
                stats.totalCompressedEntries++;
                stats.totalCompressedBytes += lf.getCompressedSize_32bit();
                if (lf.getCompressionMethod() == CompressionMethod.DEFLATED) {
                    info = divineDeflateInfo(lf.getFileName(),
                        lsp.getFileDataPart().getData(), stats);
                } else {
                    info = new DeflateInfo(false,
                        lf.getCompressionMethod().toString(),
                        "unknown");
                }
                if (info.matched) {
                    stats.numMatchedCompressedEntries++;
                    stats.numMatchedCompressedBytes +=
                        lf.getCompressedSize_32bit();
                }
            }
            if (info != null) {
                stats.infoByPath.put(lf.getFileName(), info);
            }
        }
        return stats;
    }

    /**
     * Attempts to recreate an exact match for a previously deflated resource by
     * recompressing the data and finding an identical SHA256.
     * @param fileName the name of the file, used to optimize strategies
     * @param compressedData the data to attempt to reproduce deterministically
     * @param stats used to optimize guesses
     * @return the result. If the the implementation name is
     * {@link DeflateInfo#JRE_DEFLATE}, then the reverse engineering attempt
     * was successful; otherwise, not way to reproduce the data was found and
     * the name is set to {@link DeflateInfo#UNKNOWN_DEFLATE}.
     * @throws IOException if anything goes wrong
     */
    private final DeflateInfo divineDeflateInfo(String fileName,
        byte[] compressedData, DivinedCompressionStats stats)
            throws IOException {
        byte[] sha256OfCompressedData;
        try {
            sha256OfCompressedData = MessageDigest.getInstance("SHA-256")
                .digest(compressedData);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }

        ByteArrayOutputStream uncompressedDataBuffer =
            new ByteArrayOutputStream();
        boolean guessedNowrap = stats.likelyNowrap == null ?
            true : stats.likelyNowrap;
        DeflateUncompressor uncompressor = new DeflateUncompressor();
        uncompressor.setNowrap(guessedNowrap);
        try {
            uncompressor.uncompress(new ByteArrayInputStream(compressedData),
                uncompressedDataBuffer);
            if (stats.likelyNowrap == null) {
                logVerbose("determined likely nowrap=" + guessedNowrap);
            }
            stats.likelyNowrap = guessedNowrap;
        } catch (ZipException likelyWrappingProblem) {
            // Retry with nowrap = true
            uncompressedDataBuffer = new ByteArrayOutputStream();
            uncompressor.setNowrap(!guessedNowrap);
            try {
                uncompressor.uncompress(new ByteArrayInputStream(
                    compressedData), uncompressedDataBuffer);
                if (stats.likelyNowrap == null) {
                    logVerbose("determined likely nowrap=" + !guessedNowrap);
                }
                stats.likelyNowrap = !guessedNowrap;
            } catch (ZipException somethingElse) {
                // Cannot be recovered
                return new DeflateInfo(false,
                    DeflateInfo.UNKNOWN_DEFLATE, "unknown");
            }
        }  // stats.likelyNowrap is now definitely set!

        JreDeflateParameters guess = null;
        final int indexOfExtension = fileName.lastIndexOf('.');
        String normalizedExtension = null;
        if (indexOfExtension >= 0 && indexOfExtension < fileName.length() - 1) {
            // Has an extension of at least one character in length, get it.
            // TODO: Support exotic locales
            normalizedExtension = fileName.substring(
                indexOfExtension + 1).toLowerCase();
            guess = stats.bestGuessDeflateParametersByExtension.get(
                normalizedExtension);
        }
        final byte[] uncompressedData = uncompressedDataBuffer.toByteArray();

        // First try the best-guess for this extension, if it exists.
        // Then fall back to default compression, then best compression.
        Set<JreDeflateParameters> parametersToTry =
            new LinkedHashSet<JreDeflateParameters>();
        if (guess != null) {
            logVerbose("Best guess for " + normalizedExtension + ": " + guess);
            parametersToTry.add(guess);
        }
        JreDeflateParameters defaults = new JreDeflateParameters(
            DEFAULT_JRE_DEFLATE_PARAMETERS.level,
            DEFAULT_JRE_DEFLATE_PARAMETERS.strategy,
            stats.likelyNowrap);
        JreDeflateParameters bestCompression = new JreDeflateParameters(
            BEST_COMPRESSION_JRE_DEFLATE_PARAMETERS.level,
            BEST_COMPRESSION_JRE_DEFLATE_PARAMETERS.strategy,
            stats.likelyNowrap);

        parametersToTry.add(defaults);
        parametersToTry.add(bestCompression);

        // This holds the size computed when the data is recompressed. If the
        // size ever falls below the original compressed size, assume that
        // every level above the current one will fail since the size of the
        // original has already been passed.
        int[] sizeResult = new int[] { -1 };
        int bestCompressionSizeResult = -1;
        for (JreDeflateParameters parameters : parametersToTry) {
            logVerbose("Quick scan: " + fileName + ": " + parameters);
            if (matches(uncompressedData, compressedData.length, parameters,
                sha256OfCompressedData, sizeResult)) {
                logVerbose("Matched in quick scan: " + fileName + ": " +
                    parameters);
                if (normalizedExtension != null) {
                    stats.bestGuessDeflateParametersByExtension.put(
                        normalizedExtension, parameters);
                }
                return new DeflateInfo(true, DeflateInfo.JRE_DEFLATE,
                    parameters.toString());
            }
            if (parameters == bestCompression) {
                bestCompressionSizeResult = sizeResult[0];
            }
        }

        // Still no match? Brute force time.
        // TODO: The first match will likely tell us the strategy and the wrap
        // style used throughout the archive. Wrapping is kind of taken care of
        // below, but we could be smarter about strategies.
        final List<Integer> strategies = new ArrayList<Integer>();
        strategies.add(Deflater.DEFAULT_STRATEGY);
        strategies.add(Deflater.FILTERED);
        strategies.add(Deflater.HUFFMAN_ONLY);
        if (bestCompressionSizeResult > compressedData.length) {
            if (!stats.superBrute) {
                // Skip this entire strategy
                strategies.remove(Integer.valueOf(bestCompression.strategy));
                logVerbose("Abandoning strategy " + bestCompression.strategy +
                    " for " + fileName + " after best size " +
                    bestCompressionSizeResult + " at level 9 (original " +
                    "compressed size " + compressedData.length + ")");
            }
        }

        final List<Boolean> nowraps = new ArrayList<Boolean>();
        nowraps.add(stats.likelyNowrap.booleanValue());
        if (stats.superBrute) {
            nowraps.add(!stats.likelyNowrap);
        }

        for (final boolean nowrap : nowraps) {
            for (Integer strategy : strategies) {
                // Optimization: Always start at max compression. The default
                // strategies cover the most common cases. Abort if level 9
                // isn't as small as the original (i.e., a more powerful
                // deflater has been used to compress the original data).
                for (int level=9; level>=1; level--) {
                    final JreDeflateParameters parameters =
                        new JreDeflateParameters(level, strategy, nowrap);
                    if (parameters.equals(defaults) ||
                        parameters.equals(bestCompression)) {
                        continue;  // Already done.
                    }
                    if (parameters.strategy == Deflater.HUFFMAN_ONLY) {
                        // The concept of level is irrelevant to this strategy,
                        // so only one iteration is required.
                        level = 1;
                    }
    
                    if (matches(uncompressedData, compressedData.length,
                            parameters, sha256OfCompressedData, sizeResult)) {
                        logVerbose("Matched in brute force scan: " + fileName +
                            ": " + parameters);
                        if (normalizedExtension != null) {
                            stats.bestGuessDeflateParametersByExtension.put(
                                normalizedExtension, parameters);
                        }
                        return new DeflateInfo(true, DeflateInfo.JRE_DEFLATE,
                            parameters.toString());
                    }
                    final int recompressedSize = sizeResult[0];
                    final String reason;
                    if (recompressedSize != compressedData.length) {
                        reason = "recompressed size " + recompressedSize +
                            " != original compressed size " +
                            compressedData.length;
                    } else {
                        reason = "sha256 mismatch";
                    }
                    logVerbose("Brute force scan: " + fileName + ": " +
                        parameters + ": failure (" + reason + ")");
    
                    if (stats.superBrute) {
                        // Make no attempt to short circuit, try EVERYTHING.
                        continue;
                    }
    
                    if (recompressedSize > compressedData.length) {
                        // Continuing on will be a waste of time as the length
                        // of the recompressed data is still larger than what
                        // was given originally. Abandon hope.
                        logVerbose("Abandoning strategy " + strategy +
                            " for " + fileName + " after best size " +
                            recompressedSize + " at level " + level +
                            " (original compressed size " +
                            compressedData.length + ")");
                        level = 0; // Abort
                    }
                }  // compression levels loop
            }  // stategies loop
        }  // nowraps loop
        // Still no match? Likely not something this implementation can handle.
        return new DeflateInfo(false, DeflateInfo.UNKNOWN_DEFLATE, "unknown");
    }

    /**
     * Check if the specified uncompressed data recompresses into a form whose
     * sha256 matches the specified sha256, using the specified parameters.
     * @param uncompressedData the uncompressed data to be recompressed
     * @param compressedLength the length of the compressed form of the data;
     * if this is not achieved the sha256 need not be calculated.
     * @param parameters the parameters to use for recompression
     * @param sha256OfOriginalCompressedData the sha256 to attempt to match
     * after recompression
     * @param outSize an array of size one into which the size of the compressed
     * data will be placed
     * @return true if the sha256 of the recompressed form of the data matched
     * the specified sha256, otherwise false
     * @throws IOException if anything goes wrong
     */
    private boolean matches(byte[] uncompressedData, final int compressedLength,
        JreDeflateParameters parameters, byte[] sha256OfOriginalCompressedData,
        int[] outSize)
            throws IOException {
        DeflateCompressor compressor = new DeflateCompressor();
        compressor.setCompressionLevel(parameters.level);
        compressor.setStrategy(parameters.strategy);
        compressor.setNowrap(parameters.nowrap);
        ByteArrayOutputStream recompressedBuffer = new ByteArrayOutputStream();
        compressor.compress(new ByteArrayInputStream(uncompressedData),
            recompressedBuffer);
        outSize[0] = recompressedBuffer.size();
        if (recompressedBuffer.size() != compressedLength) {
            // Length doesn't match, so no point in computing sha256
            // Do this even in superbrute, as sha256 failure is highly reliable.
            return false;
        }
        byte[] sha256OfCompressedData;
        try {
            sha256OfCompressedData = MessageDigest.getInstance("SHA-256")
                .digest(recompressedBuffer.toByteArray());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        return Arrays.equals(sha256OfCompressedData,
            sha256OfOriginalCompressedData);
    }
}