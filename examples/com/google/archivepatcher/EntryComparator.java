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

import java.util.Arrays;

import com.google.archivepatcher.parts.DataDescriptor;
import com.google.archivepatcher.parts.FileData;
import com.google.archivepatcher.parts.LocalFile;
import com.google.archivepatcher.parts.LocalSectionParts;
import com.google.archivepatcher.util.SimpleArchive;


/**
 * A tool for comparing corresponding entries in two archives.
 */
public class EntryComparator extends AbstractArchiveTool {

    /**
     * Main method. For usage instructions, run with "--help".
     * 
     * @param args arguments to the program
     * @throws Exception if anything goes wrong
     */
    public static void main(String... args) throws Exception {
        new EntryComparator().run(args);
    }

    @Override
    public void configureOptions(MicroOptions options) {
        super.configureOptions(options);
        options.option("archive1").isRequired().describedAs(
            "the first archive to compare");
        options.option("path1").isRequired().describedAs(
            "the path in the first archive of the entry to compare");
        options.option("archive2").isRequired().describedAs(
            "the second archive to compare");
        options.option("path2").describedAs(
            "the path in the second archive of the entry to compare " +
            "(optional; if unspecified, defaults to path1)");
    }

    private Archive archive1;
    private Archive archive2;
    private LocalSectionParts lsp1;
    private LocalSectionParts lsp2;

    @Override
    protected void run(MicroOptions options) throws Exception {
        final String archive1Path = options.getArg("archive1");
        archive1 = SimpleArchive.fromFile(archive1Path);
        final String archive2Path = options.getArg("archive2");
        archive2 = SimpleArchive.fromFile(archive2Path);
        final String path1 = options.getArg("path1");
        final String path2 = options.getArg("path2", path1);
        lsp1 = archive1.getLocal().getByPath(path1);
        if (lsp1 == null) {
            throw new RuntimeException(
                "No such entry in " + archive1Path + ": " + path1);
        }
        lsp2 = archive2.getLocal().getByPath(path2);
        if (lsp2 == null) {
            throw new RuntimeException(
                "No such entry in " + archive2Path + ": " + path2);
        }
        final boolean same = compare();
        if (!same) System.exit(-1);
        log("No differences.");
    }

    private boolean compare() {
        boolean same = true;
        same &= compare(lsp1.getLocalFilePart(), lsp2.getLocalFilePart());
        same &= compare(lsp1.getDataDescriptorPart(),
            lsp2.getDataDescriptorPart());
        same &= compare(lsp1.getFileDataPart(), lsp2.getFileDataPart());
        return same;
    }

    private boolean compare(final FileData fd1, final FileData fd2) {
        return compareBytes("file data", fd1.getData(), fd2.getData());
    }

    private boolean compare(final DataDescriptor dd1, final DataDescriptor dd2) {
        if (dd1 == null && dd2 != null) {
            log("entry in first archive has data descriptor; " +
                "entry in second archive does not.");
            return false;
        }
        if (dd1 != null && dd2 == null) {
            log("entry in first archive has no data " +
                "descriptor; entry in second archive does.");
            return false;
        }
        if (dd1 == null && dd2 == null) return true;
        boolean same = true;
        same &= compareLong("(data descriptor) compressed size",
            dd1.getCompressedSize_32bit(), dd2.getCompressedSize_32bit());
        same &= compareLong("(data descriptor) crc32",
            dd1.getCrc32_32bit(), dd2.getCrc32_32bit());
        same &= compareLong("(data descriptor) uncompressed size",
            dd1.getUncompressedSize_32bit(), dd2.getUncompressedSize_32bit());
        return same;
    }

    private boolean compare(final LocalFile lf1, final LocalFile lf2) {
        boolean same = true;
        same &= compareLong("compressed size",
            lf1.getCompressedSize_32bit(), lf2.getCompressedSize_32bit());
        same &= compareObj("compression method",
            lf1.getCompressionMethod(), lf2.getCompressionMethod());
        same &= compareLong("crc32",
            lf1.getCrc32_32bit(), lf2.getCrc32_32bit());
        same &= compareBytes("extras",
            lf1.getExtraField(), lf2.getExtraField());
        same &= compareLong("extras length",
            lf1.getExtraFieldLength_16bit(), lf2.getExtraFieldLength_16bit());
        same &= compareLong("flags",
            lf1.getGeneralPurposeBitFlag_16bit(),
            lf2.getGeneralPurposeBitFlag_16bit());
        same &= compareObj("last modified date",
            lf1.getLastModifiedFileDate(), lf2.getLastModifiedFileDate());
        same &= compareObj("last modified time",
            lf1.getLastModifiedFileTime(), lf2.getLastModifiedFileTime());
        same &= compareLong("uncompressed size",
            lf1.getUncompressedSize_32bit(), lf2.getUncompressedSize_32bit());
        same &= compareLong("version needed to extract",
            lf1.getVersionNeededToExtract_16bit(),
            lf2.getVersionNeededToExtract_16bit());
        return same;
    }

    private boolean compareBytes(String title, byte[] value1, byte[] value2) {
        if (value1 == null && value2 == null) return true;
        if (value1 != null && value2 != null && Arrays.equals(value1, value2))
            return true;
        logdiff(title, "[binary content]", "[binary content]");
        return false;
    }

    private boolean compareObj(String title, Object value1, Object value2) {
        if (value1 == null && value2 == null) return true;
        if (value1 != null && value2 != null && value1.equals(value2))
            return true;
        logdiff(title, value1, value2);
        return false;
    }

    private boolean compareLong(String title, long value1, long value2) {
        if (value1 == value2) return true;
        logdiff(title, value1, value2);
        return false;
    }

    private void logdiff(String title, Object value1, Object value2) {
        log("Difference found in field '" + title + "': " +
            value1 + " != " + value2);
    }
}