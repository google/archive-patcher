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
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import com.google.archivepatcher.compression.JreDeflateParameters;

/**
 * Very detailed statistics for a reassembly task, breaking down the time and
 * space of the task across a number of useful dimensions. This object is
 * intended to be passed into the guts of reassembly tasks so that data can be
 * incrementally accumulated.
 * <p>
 * To accumulate more data from a single resource, call either
 * {@link #accumulateRecompressStats(String, JreDeflateParameters, long, long, long)}
 * or
 * {@link #accumulateCopyStats(String, ReassemblyTechnique, long, long, long)}
 * as appropriate for the resource.
 * <p>
 * To accumulate another stats object into this one, use
 * {@link #accumulate(ReassemblyStats)}.
 * <p>
 * Some specific values are updated at the beginning or end of an archive-wide
 * operation. For these use
 * 
 */
public class ReassemblyStats {
    /**
     * One entry in {@link ReassemblyStats}.
     */
    public static class Entry {
        /**
         * The number of entries that were processed.
         */
        private long numResources = 0;
  
        /**
         * The number of bytes after compression.
         */
        private long numCompressedBytes = 0;
  
        /**
         * The number of bytes before compression.
         */
        private long numUncompressedBytes = 0;

        /**
         * The time spent working. This may include time spent reading
         * the source bytes, compressing, and writing the destination bytes.
         * Since most of this is done in a streaming fashion the interaction
         * between these three processes is complex.
         */
        private long millisElapsed = 0;

        /**
         * Returns the number of entries that were processed.
         * @return as described
         */
        public final long getNumResources() {
            return numResources;
        }

        /**
         * Returns the number of bytes after compression.
         * @return as described
         */
        public final long getNumCompressedBytes() {
            return numCompressedBytes;
        }

        /**
         * Returns the number of bytes before compression.
         * @return as described
         */
        public final long getNumUncompressedBytes() {
            return numUncompressedBytes;
        }

        /**
         * Returns the time spent working. This may include time spent reading
         * the source bytes, compressing, and writing the destination bytes.
         * Since most of this is done in a streaming fashion the interaction
         * between these three processes is complex.
         * @return as described
         */
        public final long getMillisElapsed() {
            return millisElapsed;
        }
    }

    /**
     * Includes time spent reading the input files as well as writing the
     * outputs. Includes time spent reading or parsing the directives file but,
     * reading the source archive, parsing it, compressing and writing the
     * result, verifying (if verification was requested) and all other overhead.
     * Some finer-grained timing may be obtained from the other fields in this
     * object.
     */
    private long totalMillisReassembling = 0;

    /**
     * Time spent specifically recompressing resources, and nothing else.
     */
    private long totalMillisRecompressing = 0;

    /**
     * Time spent copying resources, and nothing else.
     */
    private long totalMillisCopying = 0;

    /**
     * Time spent verifying the signature match during reassembly, and nothing
     * else.
     */
    private long totalMillisVerifyingArchiveSignature = 0;

    /**
     * The total number of bytes in the input archive.
     */
    private long totalArchiveBytes = 0;

    /**
     * The total number of recompressed bytes that were written.
     */
    private long totalRecompressedCompressedBytes = 0;

    /**
     * The total number of raw uncompressed bytes that were recompressed.
     */
    private long totalRecompressedUncompressedBytes = 0;

    /**
     * The total number of bytes copied from entries with no compression at all.
     */
    private long totalCopiedNoCompressionBytes = 0;

    /**
     * The total number of bytes copied from entries where the deflate
     * parameters could not be determined.
     */
    private long totalCopiedUnknownDeflateBytes = 0;

    /**
     * The total number of bytes copied from entries where the compression
     * technology could not be determined.
     */
    private long totalCopiedUnknownTechBytes = 0;

    /**
     * For each configuration of deflate, the results that were obtained for
     * recompression.
     */
    private Map<JreDeflateParameters, Entry>
        recompressEntriesByDeflateParameters =
        new HashMap<JreDeflateParameters, Entry>();

    /**
     * For each suffix encountered in the entries, the results that were
     * obtained for recompression.
     */
    private Map<String, Entry> recompressEntriesBySuffix =
            new HashMap<String, Entry>();

    /**
     * For each suffix encountered in the entries, the results that were
     * obtained for copying uncompressed resources.
     */
    private Map<String, Entry> noCompressionEntriesBySuffix =
            new HashMap<String, ReassemblyStats.Entry>();

    /**
     * For each suffix encountered in the entries, the results that were
     * obtained for copying resources whose deflate output could not be
     * deterministically reproduced.
     */
    private Map<String, Entry> unknownDeflateEntriesBySuffix =
            new HashMap<String, ReassemblyStats.Entry>();

    /**
     * For each suffix encountered in the entries, the results that were
     * obtained for copying resources whose technology could not be determined.
     */
    private Map<String, Entry> unknownTechEntriesBySuffix =
            new HashMap<String, ReassemblyStats.Entry>();

    /**
     * For each compression level, the results that were obtained. Ignores
     * strategies and wrapping mode. Entries that were stored (no compression)
     * are not stored. This map only contains entries that were recompressed.
     */
    private Map<Integer, Entry> recompressEntriesByCompressionLevel =
            new HashMap<Integer, ReassemblyStats.Entry>();

    /**
     * Convenience value: {@link #totalMillisReassembling} as seconds.
     */
    private double totalSecondsReassembling = 0L;

    /**
     * Convenience value: The total overhead time, which is
     * {@link #totalMillisReassembling} minus all other recorded timings.
     */
    private long overheadMillis = 0L;

    /**
     * Convenience value: The total overhead bytes in the archive, which is
     * {@link #totalArchiveBytes} minus all other measured bytes. This is, in
     * most cases, the total number of bytes used by the central directory and
     * the headers in the local section entries for each file in the archive.
     */
    private long totalArchiveOverheadBytes = 0L;

    /**
     * Convenience value: The percent of {@link #totalArchiveBytes} that
     * comprises data that was recompressed.
     */
    private double percentRecompressibleBytes = 0d;

    /**
     * Convenience value: The percent of {@link #totalArchiveBytes} that
     * comprises data that was stored without compression. This data is simply
     * copied to the new archive.
     */
    private double percentCopiedUncompressedBytes = 0d;

    /**
     * Convenience value: The percent of {@link #totalArchiveBytes} that
     * comprises data that was compressed with an unknown configuration of
     * deflate. This data is simply copied to the new archive.
     */
    private double percentCopiedUnknownDeflateBytes = 0d;

    /**
     * Convenience value: The percent of {@link #totalArchiveBytes} that
     * comprises data that was compressed with an unknown compression algorithm.
     * This data is simply copied to the new archive.
     */
    private double percentCopiedUnknownTechBytes = 0d;

    /**
     * Convenience value:
     * {@link #totalArchiveOverheadBytes} / {@link #totalArchiveBytes}.
     */
    private double percentOverheadBytes = 0d;

    /**
     * Convenience value:
     * {@link #totalArchiveBytes} / {@link #totalSecondsReassembling}.
     */
    private long archiveBytesPerSecond = 0L;

    /**
     * Accumulate statistics for a file that was recompressed.
     * @param fileName the file name
     * @param params the compression parameters
     * @param numCompressedBytes the number of compressed bytes
     * @param numUncompressedBytes the number of uncompressed bytes
     * @param millisSpentCompressing time spent recompressing, in ms
     */
    public void accumulateRecompressStats(
            String fileName, JreDeflateParameters params,
            long numCompressedBytes, long numUncompressedBytes,
            long millisSpentCompressing) {
        Entry byParams =
                recompressEntriesByDeflateParameters.get(params);
        if (byParams == null) {
            byParams = new Entry();
            recompressEntriesByDeflateParameters.put(params, byParams);
        }
        byParams.numCompressedBytes += numCompressedBytes;
        byParams.numUncompressedBytes += numUncompressedBytes;
        byParams.millisElapsed += millisSpentCompressing;
        byParams.numResources++;

        Entry bySuffix = recompressEntriesBySuffix.get(getSuffix(fileName));
        if (bySuffix == null) {
            bySuffix = new Entry();
            recompressEntriesBySuffix.put(getSuffix(fileName), bySuffix);
        }
        bySuffix.numCompressedBytes += numCompressedBytes;
        bySuffix.numUncompressedBytes += numUncompressedBytes;
        bySuffix.millisElapsed += millisSpentCompressing;
        bySuffix.numResources++;

        Entry byCompressionLevel = recompressEntriesByCompressionLevel.get(
            params.level);
        if (byCompressionLevel == null) {
            byCompressionLevel = new Entry();
            recompressEntriesByCompressionLevel.put(
                params.level, byCompressionLevel);
        }
        byCompressionLevel.numCompressedBytes += numCompressedBytes;
        byCompressionLevel.numUncompressedBytes += numUncompressedBytes;
        byCompressionLevel.millisElapsed += millisSpentCompressing;
        byCompressionLevel.numResources++;

        totalMillisRecompressing += millisSpentCompressing;
        totalRecompressedCompressedBytes += numCompressedBytes;
        totalRecompressedUncompressedBytes += numUncompressedBytes;
        recalculateConvenienceStats();
    }

    /**
     * Return the suffix of the file or "<no suffix>".
     * @param fileName the name of the file to extract the suffix for
     * @return as described
     */
    private static final String getSuffix(String fileName) {
        int indexOfLastDot = fileName.lastIndexOf('.');
        if (indexOfLastDot >= 0 && indexOfLastDot < fileName.length() - 1) {
            return fileName.substring(indexOfLastDot + 1);
        }
        return "<no suffix>";
    }

    /**
     * Accumulate stats from the specified other stats object into this one.
     * @param other the other stats object to pull data from, left unmodified
     */
    public void accumulate(ReassemblyStats other) {
        accumulate(recompressEntriesByDeflateParameters,
                other.recompressEntriesByDeflateParameters);
        accumulate(recompressEntriesBySuffix,
                other.recompressEntriesBySuffix);
        accumulate(noCompressionEntriesBySuffix,
                other.noCompressionEntriesBySuffix);
        accumulate(unknownDeflateEntriesBySuffix,
                other.unknownDeflateEntriesBySuffix);
        accumulate(unknownTechEntriesBySuffix,
                other.unknownTechEntriesBySuffix);
        totalMillisCopying += other.totalMillisCopying;
        totalMillisReassembling += other.totalMillisReassembling;
        totalMillisRecompressing += other.totalMillisRecompressing;
        totalArchiveBytes += other.totalArchiveBytes;
        totalMillisVerifyingArchiveSignature +=
            other.totalMillisVerifyingArchiveSignature;
        totalRecompressedCompressedBytes +=
            other.totalRecompressedCompressedBytes;
        totalRecompressedUncompressedBytes +=
            other.totalRecompressedUncompressedBytes;
        totalCopiedNoCompressionBytes += other.totalCopiedNoCompressionBytes;
        totalCopiedUnknownDeflateBytes += other.totalCopiedUnknownDeflateBytes;
        totalCopiedUnknownTechBytes += other.totalCopiedUnknownTechBytes;
        recalculateConvenienceStats();
    }

    /**
     * Accumulate the other map's data into our own.
     * @param ours our map
     * @param theirs their map
     */
    private <T> void accumulate(
            final Map<T, Entry> ours,
            final Map<T, Entry> theirs) {
        for (T theirKey : theirs.keySet()) {
            final Entry theirEntry = theirs.get(theirKey);
            Entry ourEntry = ours.get(theirKey);
            if (ourEntry == null) {
                ourEntry = new Entry();
                ours.put(theirKey, ourEntry);
            }
            ourEntry.millisElapsed += theirEntry.millisElapsed;
            ourEntry.numCompressedBytes += theirEntry.numCompressedBytes;
            ourEntry.numResources += theirEntry.numResources;
            ourEntry.numUncompressedBytes += theirEntry.numUncompressedBytes;
        }
    }

    /**
     * Accumulate statistics for a file that was copied.
     * @param fileName the file name
     * @param technique the technique that was used for reassembly
     * @param numCompressedBytes the number of compressed bytes
     * @param numUncompressedBytes the number of uncompressed bytes
     * @param millisSpentCopying time spent copying, in ms
     */
    public void accumulateCopyStats(final String fileName,
            final ReassemblyTechnique technique,
            long numCompressedBytes, long numUncompressedBytes,
            long millisSpentCopying) {
        Map<String, Entry> map;
        switch(technique) {
            case COPY_NO_COMPRESSION:
                map = noCompressionEntriesBySuffix;
                totalCopiedNoCompressionBytes += numUncompressedBytes;
                break;
            case COPY_UNKNOWN_DEFLATE_PARAMETERS:
                map = unknownDeflateEntriesBySuffix;
                totalCopiedUnknownDeflateBytes += numCompressedBytes;
                break;
            case COPY_UNKNOWN_TECH:
                map = unknownTechEntriesBySuffix;
                totalCopiedUnknownTechBytes += numCompressedBytes;
                break;
            default:
                throw new IllegalArgumentException();
        }
        Entry entry = map.get(getSuffix(fileName));
        if (entry == null) {
            entry = new Entry();
            map.put(getSuffix(fileName), entry);
        }
        entry.numCompressedBytes += numCompressedBytes;
        entry.numUncompressedBytes += numUncompressedBytes;
        entry.millisElapsed += millisSpentCopying;
        entry.numResources++;
        totalMillisCopying += millisSpentCopying;
        recalculateConvenienceStats();
    }

    /**
     * Sum the number of resources in every entry of the specified map and
     * return the total.
     * @param map the map to count the number of resources within
     * @return the sum of all the resource counters in the entries
     */
    private static final <T> long countEntries(Map<T, Entry> map) {
        long result = 0;
        for (Entry entry : map.values()) {
            result += entry.numResources;
        }
        return result;
    }

    /**
     * Return the total number of resources processed.
     * @return as described
     */
    public final long getTotalResourcesProcessed() {
        long result = 0;
        result += countEntries(recompressEntriesBySuffix);
        result += countEntries(unknownDeflateEntriesBySuffix);
        result += countEntries(unknownTechEntriesBySuffix);
        result += countEntries(noCompressionEntriesBySuffix);
        return result;
    }

    /**
     * Recalculate all convenience statistics immediately.
     */
    private final void recalculateConvenienceStats() {
        totalSecondsReassembling = totalMillisReassembling / 1000d;
        overheadMillis = totalMillisReassembling -
            totalMillisCopying - totalMillisRecompressing -
            totalMillisVerifyingArchiveSignature;
        totalArchiveOverheadBytes = totalArchiveBytes -
            totalRecompressedCompressedBytes -
            totalCopiedNoCompressionBytes -
            totalCopiedUnknownDeflateBytes -
            totalCopiedUnknownTechBytes;
        percentRecompressibleBytes =
            totalRecompressedCompressedBytes / (double) totalArchiveBytes;
        percentCopiedUncompressedBytes =
            totalCopiedNoCompressionBytes / (double) totalArchiveBytes;
        percentCopiedUnknownDeflateBytes =
            totalCopiedUnknownDeflateBytes / (double) totalArchiveBytes;
        percentCopiedUnknownTechBytes =
            totalCopiedUnknownTechBytes / (double) totalArchiveBytes;
        percentOverheadBytes =
            totalArchiveOverheadBytes / (double) totalArchiveBytes;
        if (totalMillisReassembling > 0) {
            archiveBytesPerSecond = (long) (totalArchiveBytes / totalSecondsReassembling);
        } else {
            // Non-sensical to discuss bytes processed so fast we couldn't even
            // measure a meaningful amount of time passing
            archiveBytesPerSecond = 0;
        }
    }

    /**
     * Generate a nice human-readable report.
     */
    @Override
    public String toString() {
        final NumberFormat longFormat =
            NumberFormat.getNumberInstance(Locale.US);
        final NumberFormat percentFormat =
            new DecimalFormat("00.00%");
        StringBuilder buffer = new StringBuilder();
        buffer.append("Summary: ")
            .append(longFormat.format(getTotalResourcesProcessed()))
            .append(" resources from archives totalling ")
            .append(longFormat.format(totalArchiveBytes))
            .append(" bytes processed in ")
            .append(longFormat.format(totalMillisReassembling))
            .append("ms\n  ");
        if (archiveBytesPerSecond > 0) {
            buffer.append(longFormat.format(archiveBytesPerSecond))
                .append(" bytes/second overall processing speed");
        } else {
            buffer.append("too fast/small to measure");
        }
        buffer.append("\n  ")
            .append(longFormat.format(totalMillisRecompressing))
            .append("ms recompressing, ")
            .append(longFormat.format(totalMillisCopying))
            .append("ms copying, ")
            .append(longFormat.format(totalMillisVerifyingArchiveSignature))
            .append("ms verifying archive signature, ")
            .append(longFormat.format(overheadMillis))
            .append("ms general overhead\n")
            .append("Byte breakdown: of ")
            .append(longFormat.format(totalArchiveBytes))
            .append(" bytes in the original apk:\n");

        if (totalRecompressedCompressedBytes > 0) {
            buffer
                .append("  ")
                .append(longFormat.format(totalRecompressedCompressedBytes))
                .append(" bytes (")
                .append(percentFormat.format(percentRecompressibleBytes))
                .append(" of total) comprise recompressible data\n");
        }

        if (totalCopiedNoCompressionBytes > 0) {
            buffer
                .append("  ")
                .append(longFormat.format(totalCopiedNoCompressionBytes))
                .append(" bytes (")
                .append(percentFormat.format(percentCopiedUncompressedBytes))
                .append(" of total) comprise data that was stored without ")
                .append("compression\n");
        }

        if (totalCopiedUnknownDeflateBytes > 0) {
            buffer
                .append("  ")
                .append(longFormat.format(totalCopiedUnknownDeflateBytes))
                .append(" bytes (")
                .append(percentFormat.format(percentCopiedUnknownDeflateBytes))
                .append(" of total) comprise data that was compressed with ")
                .append("deflate in an unknown configuration\n");
        }

        if (totalCopiedUnknownTechBytes > 0) {
            buffer
                .append("  ")
                .append(longFormat.format(totalCopiedUnknownTechBytes))
                .append(" bytes (")
                .append(percentFormat.format(percentCopiedUnknownTechBytes))
                .append(" of total) comprise data that was compressed with ")
                .append("an unknown compression technology\n");
        }

        if (totalArchiveOverheadBytes > 0) {
            buffer
                .append("  ")
                .append(longFormat.format(totalArchiveOverheadBytes))
                .append(" bytes (")
                .append(percentFormat.format(percentOverheadBytes))
                .append(" of total) comprise archive overhead\n");
        }

        if (recompressEntriesByDeflateParameters.size() > 0) {
            buffer.append("Recompressed entries by deflate config:\n");
            append(buffer, "deflate config",
                    recompressEntriesByDeflateParameters);
        }
        if (recompressEntriesByCompressionLevel.size() > 0) {
            buffer.append("Recompressend entries by compression level:\n");
            append(buffer, "compression level",
                recompressEntriesByCompressionLevel);
        }
        if (recompressEntriesBySuffix.size() > 0) {
            buffer.append("Recompressed entries by suffix:\n");
            append(buffer, "suffix",
                    new TreeMap<String, Entry>(recompressEntriesBySuffix));
        }
        if (noCompressionEntriesBySuffix.size() > 0) {
            buffer.append("Uncompressed entries by suffix:\n");
            append(buffer, "suffix",
                    new TreeMap<String, Entry>(noCompressionEntriesBySuffix));
        }
        if (unknownDeflateEntriesBySuffix.size() > 0) {
            buffer.append("Copied entries by suffix ")
                .append("(unknown deflate configuration):\n");
            append(buffer, "suffix",
                    new TreeMap<String, Entry>(unknownDeflateEntriesBySuffix));
        }
        if (unknownTechEntriesBySuffix.size() > 0) {
            buffer.append("Copied entries by suffix ")
                .append("(unknown compression technology):\n");
            append(buffer, "suffix",
                    new TreeMap<String, Entry>(unknownTechEntriesBySuffix));
        }
        return buffer.toString();
    }

    /**
     * Append the contents of a map to the output.
     * @param buffer the buffer to write to
     * @param keyDescription a human-readable description of the key
     * @param map the map to read from
     */
    private <T> void append(StringBuilder buffer, String keyDescription,
            Map<T, Entry> map) {
        final NumberFormat format =
                NumberFormat.getNumberInstance(Locale.US);
        for (final T key : map.keySet()) {
            final Entry entry = map.get(key);
            final double seconds = entry.millisElapsed / 1000d;
            final long milliseconds = entry.millisElapsed;
            final long compressedBytesPerSecond;
            final long uncompressedBytesPerSecond;
            if (milliseconds > 0) {
                compressedBytesPerSecond =
                        (long) (entry.numCompressedBytes / seconds);
                uncompressedBytesPerSecond =
                        (long) (entry.numUncompressedBytes / seconds);
            } else {
                // Non-sensical to discuss bytes compressed so fast we couldn't
                // even measure a meaningful amount of time passing
                compressedBytesPerSecond = 0;
                uncompressedBytesPerSecond = 0;
            }

            buffer.append("  ").append(keyDescription)
                .append(" '").append(key).append("': ")
                .append(format.format(entry.numResources))
                .append(" resources processed in ")
                .append(format.format(milliseconds))
                .append( "ms; ")
                .append(format.format(entry.numCompressedBytes))
                .append(" bytes compressed (");
            if (compressedBytesPerSecond > 0) {
                buffer.append(format.format(compressedBytesPerSecond))
                    .append(" bytes/second");
            } else {
                buffer.append("too fast/small to measure");
            }
            buffer.append("), ")
                .append(format.format(entry.numUncompressedBytes))
                .append(" bytes uncompressed (");
            if (uncompressedBytesPerSecond > 0) {
                buffer.append(format.format(uncompressedBytesPerSecond))
                    .append(" bytes/second");
            } else {
                buffer.append("too fast/small to measure");
            }
            buffer.append(")\n");
        }
    }

    /**
     * Returns the total time spent reassembling, in milliseconds.
     * @return as described
     */
    public final long getTotalMillisReassembling() {
        return totalMillisReassembling;
    }

    /**
     * Returns the time spent specifically recompressing resources in
     * milliseconds.
     * @return as described
     */
    public final long getTotalMillisRecompressing() {
        return totalMillisRecompressing;
    }

    /**
     * Returns the total time spent copying resources in milliseconds.
     * @return as described
     */
    public final long getTotalMillisCopying() {
        return totalMillisCopying;
    }

    /**
     * Returns the total time spent verifying the signature match during
     * reassembly, if verification was requested (in milliseconds).
     * @return as described
     */
    public final long getTotalMillisVerifyingArchiveSignature() {
        return totalMillisVerifyingArchiveSignature;
    }

    /**
     * Returns the total number of bytes in the input archive. Assuming that
     * the output archive matches (i.e. if verification was requested and did
     * succeed), this is also the total number of bytes in the output archive.
     * @return as described
     */
    public final long getTotalArchiveBytes() {
        return totalArchiveBytes;
    }

    /**
     * Returns the total number of recompressed bytes that were written.
     * @return as described
     */
    public final long getTotalRecompressedCompressedBytes() {
        return totalRecompressedCompressedBytes;
    }

    /**
     * Returns the total number of raw uncompressed bytes that were
     * recompressed.
     * @return as described
     */
    public final long getTotalRecompressedUncompressedBytes() {
        return totalRecompressedUncompressedBytes;
    }

    /**
     * Returns the total number of bytes copied from entries that were stored
     * without compression.
     * @return as described
     */
    public final long getTotalCopiedNoCompressionBytes() {
        return totalCopiedNoCompressionBytes;
    }

    /**
     * Returns the total number of bytes copied from entries where the deflate
     * parameters could not be determined.
     * @return as described
     */
    public final long getTotalCopiedUnknownDeflateBytes() {
        return totalCopiedUnknownDeflateBytes;
    }

    /**
     * Returns the total number of bytes copied from entries where the
     * compression technology could not be determined.
     * @return as described
     */
    public final long getTotalCopiedUnknownTechBytes() {
        return totalCopiedUnknownTechBytes;
    }

    /**
     * Returns a live, read-only view of a mapping from each configuration of
     * deflate to the results that were obtained for recompression.
     * @return as described
     */
    public final Map<JreDeflateParameters, Entry>
        getRecompressEntriesByDeflateParameters() {
        return Collections.unmodifiableMap(
            recompressEntriesByDeflateParameters);
    }

    /**
     * Returns a live, read-only view of a mapping from each suffix encountered
     * in the entries to the results that were obtained for recompression.
     * @return as described
     */
    public final Map<String, Entry> getRecompressEntriesBySuffix() {
        return Collections.unmodifiableMap(recompressEntriesBySuffix);
    }

    /**
     * Returns a live, read-only view of a mapping from each suffix encountered
     * in the entries to the results that were obtained for copying uncompressed
     * resources.
     * @return as described
     */
    public final Map<String, Entry> getNoCompressionEntriesBySuffix() {
        return Collections.unmodifiableMap(noCompressionEntriesBySuffix);
    }

    /**
     * Returns a live, read-only view of a mapping from each suffix encountered
     * in the entries to the results that were obtained for copying resources
     * whose deflate output could not be deterministically reproduced.
     * @return as described
     */
    public final Map<String, Entry> getUnknownDeflateEntriesBySuffix() {
        return Collections.unmodifiableMap(unknownDeflateEntriesBySuffix);
    }

    /**
     * Returns a live, read-only view of a mapping from each suffix encountered
     * in the entries to the results that were obtained for copying resources
     * whose technology could not be determined.
     * @return as described
     */
    public final Map<String, Entry> getUnknownTechEntriesBySuffix() {
        return Collections.unmodifiableMap(unknownTechEntriesBySuffix);
    }

    /**
     * Returns a live, read-only view of a mapping from each compression level
     * to the results that were obtained. Ignores strategies and wrapping mode.
     * Entries that were stored (no compression) are not stored. This map only
     * contains entries that were recompressed.
     * @return as described
     */
    public final Map<Integer, Entry> getRecompressEntriesByCompressionLevel() {
        return Collections.unmodifiableMap(recompressEntriesByCompressionLevel);
    }

    /**
     * Convenience value: {@link #getTotalMillisReassembling()} as seconds.
     * @return as described
     */
    public final double getTotalSecondsReassembling() {
        return totalSecondsReassembling;
    }

    /**
     * Convenience value: the total overhead time, which is
     * {@link #getTotalMillisReassembling()} minus all other recorded timings.
     * @return as described
     */
    public final long getOverheadMillis() {
        return overheadMillis;
    }

    /**
     * Convenience value: the total overhead bytes in the archive, which is
     * {@link #getTotalArchiveBytes()} minus all other measured bytes. This is,
     * in most cases, the total number of bytes used by the central directory
     * and the headers in the local section entries for each file in the
     * archive.
     * @return as described
     */
    public final long getTotalArchiveOverheadBytes() {
        return totalArchiveOverheadBytes;
    }

    /**
     * Convenience value: the percent of {@link #getTotalArchiveBytes()} that
     * comprises data that was recompressed.
     * @return as described
     */
    public final double getPercentRecompressibleBytes() {
        return percentRecompressibleBytes;
    }

    /**
     * Convenience value: The percent of {@link #getTotalArchiveBytes()} that
     * comprises data that was stored without compression. This data is simply
     * copied to the new archive.
     * @return as described
     */
    public final double getPercentCopiedUncompressedBytes() {
        return percentCopiedUncompressedBytes;
    }

    /**
     * Convenience value: The percent of {@link #getTotalArchiveBytes()} that
     * comprises data that was compressed with an unknown configuration of
     * deflate. This data is simply copied to the new archive.
     * @return as described
     */
    public final double getPercentCopiedUnknownDeflateBytes() {
        return percentCopiedUnknownDeflateBytes;
    }

    /**
     * Convenience value: The percent of {@link #getTotalArchiveBytes()} that
     * comprises data that was compressed with an unknown compression algorithm.
     * This data is simply copied to the new archive.
     * @return as described
     */
    public final double getPercentCopiedUnknownTechBytes() {
        return percentCopiedUnknownTechBytes;
    }

    /**
     * Convenience value: The percent of {@link #getTotalArchiveBytes()} that
     * comprises data that was copied for any reason (the sum of the values in
     * {@link #getPercentCopiedUncompressedBytes()},
     * {@link #getPercentCopiedUnknownDeflateBytes()}, and
     * {@link #getPercentCopiedUnknownTechBytes()}).
     * @return as described
     */
    public final double getPercentCopiedBytes() {
        // For accuracy, recompute instead of adding the imprecise percentages.
        return getTotalCopiedBytes() / (double) totalArchiveBytes;
    }

    /**
     * Convenience value: {@link #getTotalArchiveOverheadBytes()} /
     * {@link #getTotalArchiveBytes()}.
     * @return as described
     */
    public final double getPercentOverheadBytes() {
        return percentOverheadBytes;
    }

    /**
     * Convenience value: {@link #getTotalArchiveBytes()} /
     * {@link #getTotalSecondsReassembling()}.
     * @return as described
     */
    public final long getArchiveBytesPerSecond() {
        return archiveBytesPerSecond;
    }

    /**
     * Convenience value: {@link #getTotalCopiedNoCompressionBytes()} +
     * {@link #getTotalCopiedUnknownDeflateBytes()} +
     * {@link #getTotalCopiedUnknownTechBytes()}.
     * @return as described
     */
    public final long getTotalCopiedBytes() {
        return totalCopiedNoCompressionBytes + totalCopiedUnknownDeflateBytes +
            totalCopiedUnknownTechBytes;
    }

    /**
     * Updates the total size of the archive, in bytes.
     * @param value the value to set
     */
    public void updateTotalArchiveBytes(final long value) {
        totalArchiveBytes = value;
        recalculateConvenienceStats();
    }

    /**
     * Updates the total time, in millis, spent reassembling the archive.
     * @param value the value to set
     */
    public void updateTotalMillisReassembling(final long value) {
        totalMillisReassembling = value;
        recalculateConvenienceStats();
    }

    /**
     * Updates the total time, in millis, spent verifying the signatures of the
     * original and reassembled archives.
     * @param value the value to set
     */
    public void updateTotalMillisVerifyingArchiveSignature(final long value) {
        totalMillisVerifyingArchiveSignature = value;
        recalculateConvenienceStats();
    }
}