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

package com.google.archivepatcher.reassembler;

import com.google.archivepatcher.JreDeflateParameters;

import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Statistics on a reassembly task.
 */
public class ReassemblyStats {
    /**
     * One entry in {@link ReassemblyStats}.
     */
    private static class Entry {

        /**
         * The number of entries that were processed.
         */
        public long numResources = 0;
  
        /**
         * The number of bytes after compression;
         */
        public long numCompressedBytes = 0;
  
        /**
         * The number of bytes before compression;
         */
        public long numUncompressedBytes = 0;

        /**
         * The time spent working. This may include time spent reading
         * the source bytes, compressing, and writing the destination bytes.
         * Since most of this is done in a streaming fashion the interaction
         * between these three processes is complex.
         */
        public long nanosElapsed = 0;
    }

    /**
     * Includes time spent reading the input files as well as writing the
     * outputs. This does not include time spent reading or parsing the
     * directives file but does include time spent reading the source
     * archive, parsing it, compressing and writing the result.
     */
    // FIXME: Our parser is unnecessarily slow, so this skews the results.
    public long totalNanosRebuilding = 0;

    /**
     * The total number of bytes in the input archive.
     */
    public long totalArchiveBytes = 0;

    /**
     * For each configuration of deflate, the results that were obtained for
     * recompression.
     */
    public Map<JreDeflateParameters, Entry>
        recompressEntriesByDeflateParameters =
        new HashMap<JreDeflateParameters, Entry>();

    /**
     * For each suffix encountered in the entries, the results that were
     * obtained for recompression.
     */
    public Map<String, Entry> recompressEntriesBySuffix =
            new HashMap<String, Entry>();

    /**
     * For each suffix encountered in the entries, the results that were
     * obtained for copying uncompressed resources.
     */
    public Map<String, Entry> noCompressionEntriesBySuffix =
            new HashMap<String, ReassemblyStats.Entry>();

    /**
     * For each suffix encountered in the entries, the results that were
     * obtained for copying resources whose deflate output could not be
     * deterministically reproduced.
     */
    public Map<String, Entry> unknownDeflateEntriesBySuffix =
            new HashMap<String, ReassemblyStats.Entry>();

    /**
     * For each suffix encountered in the entries, the results that were
     * obtained for copying resources whose technology could not be determined.
     */
    public Map<String, Entry> unknownTechEntriesBySuffix =
            new HashMap<String, ReassemblyStats.Entry>();

    /**
     * Accumulate statistics for a file that was recompressed.
     * @param fileName the file name
     * @param params the compression parameters
     * @param numCompressedBytes the number of compressed bytes
     * @param numUncompressedBytes the number of uncompressed bytes
     * @param nanosSpentCompressing time spent recompressing, in ns
     */
    public void accumulateRecompressStats(
            String fileName, JreDeflateParameters params,
            long numCompressedBytes, long numUncompressedBytes,
            long nanosSpentCompressing) {
        Entry byParams =
                recompressEntriesByDeflateParameters.get(params);
        if (byParams == null) {
            byParams = new Entry();
            recompressEntriesByDeflateParameters.put(params, byParams);
        }
        byParams.numCompressedBytes += numCompressedBytes;
        byParams.numUncompressedBytes += numUncompressedBytes;
        byParams.nanosElapsed += nanosSpentCompressing;
        byParams.numResources++;

        Entry bySuffix = recompressEntriesBySuffix.get(getSuffix(fileName));
        if (bySuffix == null) {
            bySuffix = new Entry();
            recompressEntriesBySuffix.put(getSuffix(fileName), bySuffix);
        }
        bySuffix.numCompressedBytes += numCompressedBytes;
        bySuffix.numUncompressedBytes += numUncompressedBytes;
        bySuffix.nanosElapsed += nanosSpentCompressing;
        bySuffix.numResources++;
    }

    /**
     * Return the suffix of the file or "<no suffix>".
     * @param fileName the name of the file to extract the suffix for
     * @return as described
     */
    private static String getSuffix(String fileName) {
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
        totalNanosRebuilding += other.totalNanosRebuilding;
        totalArchiveBytes += other.totalArchiveBytes;
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
            ourEntry.nanosElapsed += theirEntry.nanosElapsed;
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
     * @param nanosSpentCopying time spent copying, in ns
     */
    public void accumulateCopyStats(final String fileName,
            final ReassemblyTechnique technique,
            long numCompressedBytes, long numUncompressedBytes,
            long nanosSpentCopying) {
        Map<String, Entry> map;
        switch(technique) {
            case COPY_NO_COMPRESSION:
                map = noCompressionEntriesBySuffix;
                break;
            case COPY_UNKNOWN_DEFLATE_PARAMETERS:
                map = unknownDeflateEntriesBySuffix;
                break;
            case COPY_UNKNOWN_TECH:
                map = unknownTechEntriesBySuffix;
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
        entry.nanosElapsed += nanosSpentCopying;
        entry.numResources++;
    }

    /**
     * Sum the number of resources in every entry of the specified map and
     * return the total.
     * @param map the map to count the number of resources within
     * @return the sum of all the resource counters in the entries
     */
    private static <T> long countEntries(Map<T, Entry> map) {
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
    private long getTotalResourcesProcessed() {
        long result = 0;
        result += countEntries(recompressEntriesBySuffix);
        result += countEntries(unknownDeflateEntriesBySuffix);
        result += countEntries(unknownTechEntriesBySuffix);
        result += countEntries(noCompressionEntriesBySuffix);
        return result;
    }

    /**
     * Generate a nice human-readable report.
     */
    @Override
    public String toString() {
        final NumberFormat format =
                NumberFormat.getNumberInstance(Locale.US);
        final double totalSeconds = totalNanosRebuilding / (1000*1000*1000d);
        final long totalMillis = totalNanosRebuilding / (1000*1000);
        final long archiveBytesPerSecond;
        if (totalMillis > 0) {
            archiveBytesPerSecond = (long) (totalArchiveBytes / totalSeconds);
        } else {
            // Non-sensical to discuss bytes processed so fast we couldn't even
            // measure a meaningful amount of time passing
            archiveBytesPerSecond = 0;
        }
        StringBuilder buffer = new StringBuilder();
        buffer.append("Summary: ")
            .append(format.format(getTotalResourcesProcessed()))
            .append(" resources from archives totalling ")
            .append(format.format(totalArchiveBytes))
            .append(" bytes processed in ")
            .append(format.format(totalMillis))
            .append("ms (");
        if (archiveBytesPerSecond > 0) {
            buffer.append(format.format(archiveBytesPerSecond))
                .append(" bytes/second overall processing speed");
        } else {
            buffer.append("too fast/small to measure");
        }
        buffer.append(")\n");
        if (recompressEntriesByDeflateParameters.size() > 0) {
            buffer.append("Recompressed entries by deflate config:\n");
            append(buffer, "deflate config",
                    recompressEntriesByDeflateParameters);
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
            final double seconds = entry.nanosElapsed / (1000*1000*1000d);
            final long milliseconds = entry.nanosElapsed / (1000*1000);
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
}