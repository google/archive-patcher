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

package com.google.archivepatcher.util;

import java.io.PrintStream;

import com.google.archivepatcher.Archive;
import com.google.archivepatcher.parts.DataDescriptor;
import com.google.archivepatcher.parts.FileData;
import com.google.archivepatcher.parts.LocalFile;
import com.google.archivepatcher.parts.LocalSectionParts;


/**
 * A helper class that provides human-readable descriptions of the differences
 * between various archive-related objects. Difference information is printed to
 * a configurable {@link PrintStream}, which defaults to stdout.
 * <p>
 * Note that this class is <em>not</em> designed for performance! For better
 * performance or for bulk operations, the usual {@link Object#equals(Object)}
 * implementations should be used instead.
 * <p>
 * This class is designed to assist in analysis of structural and logical
 * differences in archives; its primary purpose is to make debugging this
 * library easier, but it can be used to deeply inspect any archives.
 * <p>
 * The methods are designed to expose as much information as possible; they do
 * not stop at the first difference found, but rather continue on to find all
 * differences that exist. The sole exception to this behavior is
 * {@link #compareBytes(String, byte[], byte[])}, which <em>does</em> stop at
 * the first byte that is difference; this is because binary data is unlikely
 * to be usefully comparable beyond a simple answer identifying the offset at
 * which the difference occurs.
 */
public class DescriptiveComparator {

    /**
     * Where to write the human-readable output produced by this object.
     */
    private PrintStream logStream = System.out;

    /**
     * Whether or not to log values that are <em>not</em> different.
     */
    private boolean logWhenNotDifferent = false;

    /**
     * Sets the log stream used by this object; by default it is stdout.
     * @param stream the stream to use
     */
    public void setLogStream(final PrintStream stream) {
        logStream = stream;
    }

    /**
     * Sets whether or not to log messages when objects are not different;
     * defaults to false.
     * @param log whether or not to log non-differences
     */
    public void setLogWhenNotDifferent(boolean log) {
        logWhenNotDifferent = log;
    }

    /**
     * Compares two entries in two {@link Archive} objects, outputting verbose
     * descriptions of any differences found.
     * @param archive1 the first archive
     * @param path1 the path in the first archive of the entry to compare
     * @param archive2 the second archive
     * @param path2 the path in the second archive of the entry to compare
     * @return true if the entries are equal, otherwise false
     */
    public boolean compare(final Archive archive1, final String path1,
        final Archive archive2, final String path2) {
        final LocalSectionParts lsp1 = archive1.getLocal().getByPath(path1);
        if (lsp1 == null) {
            throw new RuntimeException(
                "No such entry in archive #1: " + path1);
        }
        final LocalSectionParts lsp2 = archive2.getLocal().getByPath(path2);
        if (lsp2 == null) {
            throw new RuntimeException(
                "No such entry in archive #2: " + path2);
        }
        return compare(lsp1, lsp2);
    }

    /**
     * Compares two {@link LocalSectionParts} objects, outputting verbose
     * descriptions of any differences found.
     * @param lsp1 the first {@link LocalSectionParts}
     * @param lsp2 the second {@link LocalSectionParts}
     * @return true if the parts are equal, otherwise false
     */
    public boolean compare(final LocalSectionParts lsp1,
        final LocalSectionParts lsp2) {
        if (lsp1 == null && lsp2 == null) return true;
        if (lsp1 == null && lsp2 != null) {
            logdiff("(local section parts)", "[null]", "[object]");
            return false;
        }
        if (lsp1 != null && lsp2 == null) {
            logdiff("(local section parts)", "[object]", "[null]");
            return false;
        }

        boolean same = true;
        same &= compare(lsp1.getLocalFilePart(), lsp2.getLocalFilePart());
        same &= compare(lsp1.getDataDescriptorPart(),
            lsp2.getDataDescriptorPart());
        same &= compare(lsp1.getFileDataPart(), lsp2.getFileDataPart());
        if (!same) return false;
        return logSame("(local section parts)", lsp1, lsp2);
    }

    /**
     * Compares two {@link FileData} objects, outputting verbose
     * descriptions of any differences found.
     * @param fd1 the first {@link FileData}
     * @param fd2 the second {@link FileData}
     * @return true if the parts are equal, otherwise false
     */
    public boolean compare(final FileData fd1, final FileData fd2) {
        return compareBytes("(file data)", fd1.getData(), fd2.getData());
    }

    /**
     * Compares two {@link DataDescriptor} objects, outputting verbose
     * descriptions of any differences found.
     * @param dd1 the first {@link DataDescriptor}
     * @param dd2 the second {@link DataDescriptor}
     * @return true if the parts are equal, otherwise false
     */
    public boolean compare(
        final DataDescriptor dd1, final DataDescriptor dd2) {
        if (dd1 == null && dd2 == null) return true;
        if (dd1 == null && dd2 != null) {
            logdiff("(data descriptor)", "[null]", "[object]");
            return false;
        }
        if (dd1 != null && dd2 == null) {
            logdiff("(data descriptor)", "[object]", "[null]");
            return false;
        }
        boolean same = true;
        same &= compareLong("(data descriptor) compressed size",
            dd1.getCompressedSize_32bit(), dd2.getCompressedSize_32bit());
        same &= compareLong("(data descriptor) crc32",
            dd1.getCrc32_32bit(), dd2.getCrc32_32bit());
        same &= compareLong("(data descriptor) uncompressed size",
            dd1.getUncompressedSize_32bit(), dd2.getUncompressedSize_32bit());
        if (!same) return false;
        return logSame("(data descriptor)", dd1, dd2);
    }

    /**
     * Compares two {@link LocalFile} objects, outputting verbose
     * descriptions of any differences found.
     * @param lf1 the first {@link LocalFile}
     * @param lf2 the second {@link LocalFile}
     * @return true if the parts are equal, otherwise false
     */
    public boolean compare(final LocalFile lf1, final LocalFile lf2) {
        boolean same = true;
        same &= compareLong("(local file) compressed size",
            lf1.getCompressedSize_32bit(), lf2.getCompressedSize_32bit());
        same &= compareObj("(local file) compression method",
            lf1.getCompressionMethod(), lf2.getCompressionMethod());
        same &= compareLong("(local file) crc32",
            lf1.getCrc32_32bit(), lf2.getCrc32_32bit());
        same &= compareBytes("(local file) extras",
            lf1.getExtraField(), lf2.getExtraField());
        same &= compareLong("(local file) extras length",
            lf1.getExtraFieldLength_16bit(), lf2.getExtraFieldLength_16bit());
        same &= compareLong("(local file) flags",
            lf1.getGeneralPurposeBitFlag_16bit(),
            lf2.getGeneralPurposeBitFlag_16bit());
        same &= compareObj("(local file) last modified date",
            lf1.getLastModifiedFileDate(), lf2.getLastModifiedFileDate());
        same &= compareObj("(local file) last modified time",
            lf1.getLastModifiedFileTime(), lf2.getLastModifiedFileTime());
        same &= compareLong("(local file) uncompressed size",
            lf1.getUncompressedSize_32bit(), lf2.getUncompressedSize_32bit());
        same &= compareLong("(local file) version needed to extract",
            lf1.getVersionNeededToExtract_16bit(),
            lf2.getVersionNeededToExtract_16bit());
        return same;
    }

    /**
     * Compare two arrays of bytes having the specified human-readable title.
     * @param title the title to output when describing any differences
     * @param value1 the first array of bytes
     * @param value2 the second array of bytes
     * @return true if the arrays are identical, otherwise false
     */
    public boolean compareBytes(
        final String title, final byte[] value1, final byte[] value2) {
        if (value1 == null && value2 == null) {
            return logSame(title, "[null]", "[null]");
        }
        if (value1 == null) {
            logdiff(title, "[null]", "[binary content, length=" +
                value2.length + "]");
            return false;
        }
        if (value2 == null) {
            logdiff(title, "[binary content, length=" + value1.length + "]",
                "[null]");
            return false;
        }
        if (value1.length != value2.length) {
            logdiff(title, "[binary content, length=" + value1.length + "]",
                "[binary content, length=" + value2.length + "]");
            return false;
        }
        for (int x=0; x<value1.length; x++) { // value1.length == value2.length
            if (value1[x] != value2[x]) {
                logdiff(title,
                    "[byte at offset " + x + ", decimal=" + (value1[x] & 0xff),
                    "[byte at offset " + x + ", decimal=" + (value2[x] & 0xff));
                return false;
            }
        }
        return logSame(title, "[binary content, length=" + value1.length + "]",
            "[binary content, length=" + value2.length + "]");
    }

    /**
     * Compare two arbitrary objects having the specified human-readable title.
     * @param title the title to output when describing any differences
     * @param value1 the first object
     * @param value2 the second object
     * @return true if the objects are equal according to
     * {@link Object#equals(Object)}, otherwise false
     */
    public boolean compareObj(
        final String title, final Object value1, final Object value2) {
        if (value1 == null && value2 == null) {
            return logSame(title, null, null);
        }
        if (value1 != null && value2 != null && value1.equals(value2)) {
            return logSame(title, value1, value2);
        }
        logdiff(title, value1, value2);
        return false;
    }

    /**
     * Compare two long integers having the specified human-readable title.
     * @param title the title to output when describing any differences
     * @param value1 the first value
     * @param value2 the second value
     * @return true if the values are equal, otherwise false
     */
    public boolean compareLong(
        final String title, final long value1, final long value2) {
        if (value1 == value2) return logSame(title, value1, value2);
        logdiff(title, value1, value2);
        return false;
    }

    /**
     * Log a message (with trailing newline) to the configured
     * {@link PrintStream}.
     * @param message the message to log
     */
    private void log(final String message) {
        logStream.println(message);
    }

    /**
     * Logs a difference with the specified human-friendly title.
     * @param title the title to output
     * @param value1 the first value
     * @param value2 the second value
     */
    private void logdiff(
        final String title, final Object value1, final Object value2) {
        log("Difference found in field '" + title + "': " +
            value1 + " != " + value2);
    }

    /**
     * If verbose mode is configured, log a message indicating that the
     * specified information is not different, using the specified
     * human-friendly title.
     * This method returns true in all cases for convenience.
     * @param title the title to output
     * @param value1 the first value
     * @param value2 the second value
     * @return true
     */
    private boolean logSame(
        final String title, final Object value1, final Object value2) {
        if (!logWhenNotDifferent) return true;
        log("No difference in field '" + title + "': " +
            value1 + " == " + value2);
        return true;
    }
}