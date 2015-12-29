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

package com.google.archivepatcher.tools.diviner;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.archivepatcher.compression.JreDeflateParameters;

/**
 * Simple structure containing statistics obtained from divining compression
 * information from an archive.
 */
public class DivinedCompressionStats {
    /**
     * A simple, human-readable and unique description for the divination (such
     * as the path to the archive that the stats were gathered upon).
     */
    public final String description;

    /**
     * The total number of entries in the archive.
     */
    public int totalEntries = 0;

    /**
     * The total number of entries in the archive that were compressed in any
     * way.
     */
    public int totalCompressedEntries = 0;

    /**
     * The total number of bytes comprising the compressed data in the archive.
     */
    public long totalCompressedBytes = 0;

    /**
     * The total number of bytes comprising the uncompressed data in the
     * archive.
     */
    public long totalUncompressedBytes = 0;

    /**
     * The total number of compressed entries in the archive that were
     * successfully identified.
     */
    public int numMatchedCompressedEntries = 0;

    /**
     * The total number of bytes from all of the compressed entries in the
     * archive that were successfully identified.
     */
    public long numMatchedCompressedBytes = 0;

    /**
     * The total number of entries in the archive that were NOT compressed in
     * any way.
     */
    public int totalUncompressedEntries = 0;

    /**
     * Information on each compressed resource encountered during processing.
     */
    public final Map<String, DeflateInfo> infoByPath =
        new LinkedHashMap<String, DeflateInfo>();

    /**
     * A hint map to speed things up. The first time a given file extension is
     * encountered in an archive, it has to be brute-forced. The result of the
     * brute force is stored here, under the assumption that most other
     * resources of the same extension in the same archive will use the same
     * compression technique. If brute force guessing is unsuccessful, nothing
     * is stored. Every successful detection overwrites any previous value for
     * the extension.
     */
    public final Map<String, JreDeflateParameters>
        bestGuessDeflateParametersByExtension =
            new HashMap<String, JreDeflateParameters>();

    /**
     * Whether or not nowrap is likely. Set the first time it is figured out
     * for an entry, reused after.
     */
    public Boolean likelyNowrap = null;

    /**
     * Whether or not these stats were gathered in super-brute-force mode, with
     * all optimizations disabled.
     */
    public boolean superBrute = false;

    /**
     * Create a new stats object having the specified description
     * @param description
     */
    public DivinedCompressionStats(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return toString(false);
    }

    /**
     * Return a row suitable for the headers of csv output.
     * @return such a row
     */
    public static String getCsvHeaderRow() {
        final StringBuilder buffer = new StringBuilder()
            .append("description,")
            .append("total entries,")
            .append("total uncompressed entries,")
            .append("total compressed entries,")
            .append("num matched compressed entries,")
            .append("fraction of matched compressed entries,")
            .append("total uncompressed bytes,")
            .append("total compressed bytes,")
            .append("num matched compressed bytes,")
            .append("fraction of matched compressed bytes,")
            .append("total bytes of data,")
            .append("total bytes of potentially matchable data,")
            .append("fraction of total bytes potentially matchable");
        return buffer.toString();
    }

    /**
     * Returns a human-readble, nicely formatted report of everything that was
     * done along with a final tally of statistics.
     * @return such a report
     */
    public String getTextReport() {
        return toString(false);
    }

    /**
     * Returns a short one-line comma-separated-value listing of all the
     * final statistics for the archive, and nothing else. The values are in
     * the same order as {@link #getCsvHeaderRow()}.
     * @return such a listing
     */
    public String getCsvReport() {
        return toString(true);
    }
    /**
     * Obtain a representation of the stats that is human-readable.
     * @param csv if true, output exactly two lines of stats; a header line
     * and a value line, both as comma-separated lists.
     * @return the text
     */
    private String toString(boolean csv) {
        NumberFormat percentFormat = new DecimalFormat("#00.00%");
        NumberFormat longFormat = new DecimalFormat();
        longFormat.setGroupingUsed(true);
        longFormat.setMaximumFractionDigits(0);
        float fractionMatchedCompressedEntries = 0f;
        if (totalCompressedEntries > 0) {
            fractionMatchedCompressedEntries = numMatchedCompressedEntries /
                (float) totalCompressedEntries;
        }
        float fractionMatchedCompressedBytes = 0f;
        if (totalCompressedBytes > 0) {
            fractionMatchedCompressedBytes = numMatchedCompressedBytes /
                (float) totalCompressedBytes;
        }

        float fractionMatchableOfAllBytes = 0f;
        final long totalMatchableBytes = numMatchedCompressedBytes +
            totalUncompressedBytes;
        final long totalBytes = totalCompressedBytes +
            totalUncompressedBytes;
        if (totalCompressedBytes + totalUncompressedBytes > 0) {
            fractionMatchableOfAllBytes = totalMatchableBytes /
                (float) totalBytes; 
        }

        if (csv) {
            // See getCsvHeaderRow() for order
            final StringBuilder buffer = new StringBuilder()
                .append(description).append(",")
                .append(totalEntries).append(",")
                .append(totalUncompressedEntries).append(",")
                .append(totalCompressedEntries).append(",")
                .append(numMatchedCompressedEntries).append(",")
                .append(fractionMatchedCompressedEntries).append(",")
                .append(totalUncompressedBytes).append(",")
                .append(totalCompressedBytes).append(",")
                .append(numMatchedCompressedBytes).append(",")
                .append(fractionMatchedCompressedBytes).append(",")
                .append(totalBytes).append(",")
                .append(totalMatchableBytes).append(",")
                .append(fractionMatchableOfAllBytes);
            return buffer.toString();
        }

        final StringBuilder buffer = new StringBuilder()
            .append("Report for \"").append(description).append("\":\n")
            .append("File entries:\n");
        for (Map.Entry<String, DeflateInfo> entry : infoByPath.entrySet()) {
            buffer.append("  ").append(entry.getKey()).append(",")
                .append(entry.getValue()).append("\n");
        }
        buffer
            .append("\n")
            .append("Total entries:                              ")
            .append(longFormat.format(totalEntries)).append("\n")
            .append("Total uncompressed entries:                 ")
            .append(longFormat.format(totalUncompressedEntries))
            .append("\n")
            .append("Total compressed entries:                   ")
            .append(longFormat.format(totalCompressedEntries))
            .append("\n")
            .append("Num matched compressed entries:             ")
            .append(longFormat.format(numMatchedCompressedEntries))
            .append(" (" + percentFormat.format(
                fractionMatchedCompressedEntries) + ")\n")
            .append("Total uncompressed bytes:                   ")
            .append(longFormat.format(totalUncompressedBytes))
            .append("\n")
            .append("Total compressed bytes:                     ")
            .append(longFormat.format(totalCompressedBytes))
            .append("\n")
            .append("Num matched compressed bytes:               ")
            .append(longFormat.format(numMatchedCompressedBytes))
            .append(" (" + percentFormat.format(
                fractionMatchedCompressedBytes) + ")\n")
            .append("Total bytes of data:                        ")
            .append(longFormat.format(totalBytes)).append("\n")
            .append("Total bytes of potentially matchable data:  ")
            .append(longFormat.format(totalMatchableBytes))
            .append(" (" + percentFormat.format(
                fractionMatchableOfAllBytes) + " of data bytes)\n");
        return buffer.toString();
    }
}