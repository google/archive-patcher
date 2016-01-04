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

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * Collects a batch of reassembly results.
 */
public final class ReassemblyBatchResult {
    private final ReassemblyStats aggregateStats = new ReassemblyStats();
    private final ReassemblyStats successStats = new ReassemblyStats();
    private final ReassemblyStats failureStats = new ReassemblyStats();
    private final Map<String, ReassemblyResult> allResultsByInputFilePath =
        new TreeMap<String, ReassemblyResult>();
    private final Map<String, ReassemblyResult> successesByInputFilePath =
        new TreeMap<String, ReassemblyResult>();
    private final Map<String, ReassemblyResult> failuresByInputFilePath =
        new TreeMap<String, ReassemblyResult>();

    /**
     * Append a single result to the batch.
     * @param result the result to append
     */
    public void append(ReassemblyResult result) {
        allResultsByInputFilePath.put(
            result.inputFile.getAbsolutePath(), result);
        if (result.stats != null) {
            aggregateStats.accumulate(result.stats);
        }
        boolean isFailure = false;
        if (result.verificationRequested && !result.verificationSucceeded) {
            isFailure = true;
        }
        if (result.error != null) {
            isFailure = true;
        }
        if (isFailure) {
            failuresByInputFilePath.put(
                result.inputFile.getAbsolutePath(), result);
            if (result.stats != null) {
                failureStats.accumulate(result.stats);
            }
        } else {
            successesByInputFilePath.put(
                result.inputFile.getAbsolutePath(), result);
            if (result.stats != null) {
                successStats.accumulate(result.stats);
            }
        }
    }

    /**
     * Returns a live, unmodifiable view of the results for the batch.
     * Keys are the paths to the input file, values are the results for that
     * input file.
     * @return as described
     */
    public Map<String, ReassemblyResult> getAllResultsByInputFilePath() {
        return Collections.unmodifiableMap(allResultsByInputFilePath);
    }

    /**
     * Returns a live, unmodifiable view of the successful results for the
     * batch. Failures are omitted.
     * Keys are the paths to the input file, values are the results for that
     * input file.
     * @return as described
     */
    public Map<String, ReassemblyResult> getSuccessesByInputFilePath() {
        return Collections.unmodifiableMap(successesByInputFilePath);
    }

    /**
     * Returns a live, unmodifiable view of the failure results for the
     * batch. Successes are omitted.
     * Keys are the paths to the input file, values are the results for that
     * input file.
     * @return as described
     */
    public Map<String, ReassemblyResult> getFailuresByInputFilePath() {
        return Collections.unmodifiableMap(failuresByInputFilePath);
    }

    /**
     * Return a live, modifiable view of the aggregate statistics for the
     * result.
     * @return as described
     */
    public ReassemblyStats getAggregateStats() {
        return aggregateStats;
    }

    /**
     * Return a live, modifiable view of the aggregate statistics for the
     * result for successful tasks only.
     * @return as described
     */
    public ReassemblyStats getSuccessStats() {
        return successStats;
    }

    /**
     * Return a live, modifiable view of the aggregate statistics for the
     * result for failed tasks only.
     * @return as described
     */
    public ReassemblyStats getFailureStats() {
        return failureStats;
    }

    @Override
    public String toString() {
        if (allResultsByInputFilePath.size() == 0) {
            return "No results";
        }
        // Special case for 1 archive, the base case, where aggregation is
        // useless noise.
        if (allResultsByInputFilePath.size() == 1) {
            return allResultsByInputFilePath.entrySet().iterator().next()
                .getValue().toString();
        }

        StringBuilder buffer = new StringBuilder();

        // First the high-level aggregate stats.
        buffer.append(aggregateStats);

        // If successes != failures, it's worth separating them out.
        if (successesByInputFilePath.size() != failuresByInputFilePath.size()) {
            if (successesByInputFilePath.size() > 0) {
                buffer.append("\nSuccesses only:\n").append(successStats);
            }
            if (failuresByInputFilePath.size() > 0) {
                buffer.append("\nFailures only:\n").append(failureStats);
            }
        }

        if (successesByInputFilePath.size() > 0) {
            buffer.append("\n\n");
            buffer.append(
                "Detailed stats for successful archives, by input path:");
            for (Map.Entry<String, ReassemblyResult> entry :
                successesByInputFilePath.entrySet()) {
                String path = entry.getKey();
                ReassemblyResult result = entry.getValue();
                buffer.append("Path: ").append(path).append(":\n")
                    .append(result.toString()).append("\n\n");
            }
        }

        if (failuresByInputFilePath.size() > 0) {
            buffer.append("\n\n");
            buffer.append(
                "Detailed stats for failed archives, by input path:");
            for (Map.Entry<String, ReassemblyResult> entry :
                failuresByInputFilePath.entrySet()) {
                String path = entry.getKey();
                ReassemblyResult result = entry.getValue();
                buffer.append("Path: ").append(path).append(":\n")
                    .append(result.toString()).append("\n\n");
            }
        }
        return buffer.toString();
    }

    /**
     * Returns a header row for use with {@link #toSimplifiedCsv(boolean)},
     * likely useful with typical applications that process comma-separated
     * values.
     * @return as described
     */
    public static String getSimplifiedCsvHeader() {
        StringBuilder buffer = new StringBuilder();
        buffer.append("Archive Name")
            .append(",Reassembly Successful")
            .append(",Total Archive Bytes")
            .append(",Total Reassembly Time (ms)")
            .append(",Reassembly Throughput (bytes/sec)");
        return buffer.toString();
    }

    /**
     * Returns a header row for use with {@link #toDetailedCsv(boolean)},
     * likely useful with typical applications that process comma-separated
     * values.
     * @return as described
     */
    public static String getDetailedCsvHeader() {
        StringBuilder buffer = new StringBuilder(getSimplifiedCsvHeader());
        buffer.append(",Time Verifying (ms)")
            .append(",Verification Throughput (ms)")
            .append(",Num Bytes Copied")
            .append(",Percent Copied")
            .append(",Time Copying (ms)")
            .append(",Copying Throughput (bytes/sec)")
            .append(",Num Bytes Recompressed")
            .append(",Percent Recompressed")
            .append(",Time Recompressing (ms)")
            .append(",Recompression Throughput (bytes/sec)");
        for (int level=1; level <= 9; level++) {
            buffer.append(",Num Bytes Recompressed at Level ")
            .append(level)
            .append(",Percent of Archive Recompressed at Level ")
            .append(level)
            .append(",Time Recompressing at Level ")
            .append(level)
            .append(" (ms)")
            .append(",Percent of Total Recompression Time Spent at Level ")
            .append(level)
            .append(",Recompression Throughput at Level ")
            .append(level)
            .append(" (bytes/sec)");
        }
        return buffer.toString();
    }

    /**
     * Convert the result into a comma-separated-value form that is friendly to
     * analysis but less useful to humans. Each row is a tuple of the following
     * values:
     * (archive_name,success_bool,total_bytes,total_reassembly_time_ms,
     * average_reassembly_bytes_per_second)
     * @param includeHeader if true, start by outputting a header on the first
     * line.
     * @return as described
     */
    public String toSimplifiedCsv(boolean includeHeader) {
        StringBuilder buffer = new StringBuilder();
        if (includeHeader) {
            buffer.append(getSimplifiedCsvHeader());
        }
        boolean firstRow = !includeHeader;
        for (ReassemblyResult one : allResultsByInputFilePath.values()) {
            // For convenience use an empty stats if no stats are available
            if (firstRow) {
                firstRow = false;
            } else {
                // Next line
                buffer.append("\n");
            }

            ReassemblyStats stats = one.stats;
            if (stats == null) {
                stats = new ReassemblyStats();
            }

            buffer.append(one.inputFile.getName())
                .append(",").append(one.error == null)
                .append(",").append(stats.getTotalArchiveBytes())
                .append(",").append(stats.getTotalMillisReassembling())
                .append(",").append(stats.getArchiveBytesPerSecond());
        }
        return buffer.toString();
    }

    /**
     * Convert the result into a comma-separated-value form that is more
     * friendly to analysis than {@link #toSimplifiedCsv(boolean)} but even less
     * useful to humans. Each row is a tuple of the following
     * values:
     * (archive_name,success_bool,total_bytes,total_reassembly_time_ms,
     * average_reassembly_bytes_per_second, verification_time_millis,
     * bytes_copied_from_original_archive,
     * percent_copied_from_original_archive, total_copy_time_ms,
     * copy_throughput_bytes_per_sec,
     * bytes_recompressed_from_original_archive,
     * percent_compressed_from_original_archive,
     * total_recompression_time_ms, recompression_throughput_bytes_per_sec).
     * In addition there are 8 more tuples of (num_bytes, percent_bytes,
     * time_ms, percent_time, throughput_bytes_per_sec) representing the number
     * of bytes, percent of total APK bytes, amount of time spent recompressing,
     * percent of total recompression time spent recompressing and overall
     * throughput at each deflate compression level 1 through 9 (respectively).
     * @param includeHeader if true, start by outputting a header on the first
     * line.
     * @return as described
     */
    public String toDetailedCsv(boolean includeHeader) {
        final NumberFormat percentFormat =
            new DecimalFormat("00.00%");
        StringBuilder buffer = new StringBuilder();
        if (includeHeader) {
            buffer.append(getDetailedCsvHeader());
        }
        boolean firstRow = !includeHeader;
        for (ReassemblyResult one : allResultsByInputFilePath.values()) {
            // For convenience use an empty stats if no stats are available
            if (firstRow) {
                firstRow = false;
            } else {
                // Next line
                buffer.append("\n");
            }

            ReassemblyStats stats = one.stats;
            if (stats == null) {
                stats = new ReassemblyStats();
            }

            final double percentRecompressed =
                stats.getTotalRecompressedCompressedBytes() /
                (double) stats.getTotalArchiveBytes();
            long copyThroughput = 0;
            if (stats.getTotalMillisCopying() > 0) {
                copyThroughput = (long) (
                    stats.getTotalCopiedBytes() /
                    (stats.getTotalMillisCopying() / 1000d));
            }
            long verificationThroughput = 0;
            if (stats.getTotalMillisVerifyingArchiveSignature() > 0) {
                verificationThroughput = (long) (
                    (stats.getArchiveBytesPerSecond() * 2) /
                    (stats.getTotalMillisVerifyingArchiveSignature() / 1000d));
            }
            long recompressionThroughput = 0;
            if (stats.getTotalMillisRecompressing() > 0) {
                recompressionThroughput = (long) (
                    stats.getTotalRecompressedCompressedBytes() /
                    (stats.getTotalMillisRecompressing() / 1000d));
            }
            buffer.append(one.inputFile.getName())
                .append(",").append(one.error == null)
                .append(",").append(stats.getTotalArchiveBytes())
                .append(",").append(stats.getTotalMillisReassembling())
                .append(",").append(stats.getArchiveBytesPerSecond())

                // Stats for verification: time, throughput
                .append(",")
                .append(stats.getTotalMillisVerifyingArchiveSignature())
                .append(",")
                .append(verificationThroughput)

                // Stats for copying: bytes, time, throughput
                .append(",")
                .append(stats.getTotalCopiedBytes())
                .append(",")
                .append(percentFormat.format(stats.getPercentCopiedBytes()))
                .append(",")
                .append(stats.getTotalMillisCopying())
                .append(",")
                .append(copyThroughput)

                // Stats for recompression (bytes, percent, time, throughput)
                .append(",")
                .append(stats.getTotalRecompressedCompressedBytes())
                .append(",")
                .append(percentFormat.format(percentRecompressed))
                .append(",")
                .append(stats.getTotalMillisRecompressing())
                .append(",")
                .append(recompressionThroughput);

            // Stats for each compression level: num bytes, time recompressing
            // Note that there is output for levels 1 through 9 EVEN IF THEY ARE
            // EMPTY. This ensures alignment across multiple outputs so that
            // they can be trivially imported over and over as CSV.
            for (int level=1; level<=9; level++) {
                ReassemblyStats.Entry entry =
                    stats.getRecompressEntriesByCompressionLevel().get(level);
                if (entry == null) {
                    entry = new ReassemblyStats.Entry();
                }
                final double percentCompressed = entry.getNumCompressedBytes() /
                    (double) stats.getTotalArchiveBytes();
                final double percentCompressionTime = entry.getMillisElapsed() /
                    (double) stats.getTotalMillisRecompressing();
                long throughput;
                if (entry.getMillisElapsed() > 0) {
                    throughput = (long) (entry.getNumCompressedBytes() /
                        (entry.getMillisElapsed() / (double) 1000));
                } else {
                    throughput = 0; // unknowable
                }
                buffer.append(",").append(entry.getNumCompressedBytes());
                buffer.append(",").append(
                    percentFormat.format(percentCompressed));
                buffer.append(",").append(entry.getMillisElapsed());
                buffer.append(",").append(
                    percentFormat.format(percentCompressionTime));
                buffer.append(",").append(throughput);
            }
        }
        return buffer.toString();
    }
}