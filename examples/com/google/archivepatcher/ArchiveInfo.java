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

import com.google.archivepatcher.parts.CentralDirectoryFile;
import com.google.archivepatcher.parts.CentralDirectorySection;
import com.google.archivepatcher.parts.EndOfCentralDirectory;
import com.google.archivepatcher.parts.LocalSectionParts;


/**
 * A tool for printing archive information.
 */
public class ArchiveInfo extends AbstractArchiveTool {

    /**
     * Main method. For usage instructions, run with "--help".
     * 
     * @param args arguments to the program
     * @throws Exception if anything goes wrong
     */
    public static void main(String... args) throws Exception {
        new ArchiveInfo().run(args);
    }

    @Override
    public void configureOptions(MicroOptions options) {
        super.configureOptions(options);
        options.option("archive").isRequired().describedAs(
            "the archive to dump information for");
    }

    @Override
    protected void run(MicroOptions options) throws Exception {
        final String archivePath = options.getArg("archive");
        final Archive archive = Archive.fromFile(archivePath);
        log("Information for archive: " + archivePath);
        final CentralDirectorySection cd = archive.getCentralDirectory();
        final EndOfCentralDirectory eocd = cd.getEocd();
        log("Central directory information:");
        log("  Disk number: " + eocd.getDiskNumber_16bit());
        log("  Disk number of Central Directory start: " +
            eocd.getDiskNumberOfStartOfCentralDirectory_16bit());
        log("  Length of central directory (bytes): " +
            eocd.getLengthOfCentralDirectory_32bit());
        log("  Number of entries (total): " +
            eocd.getNumEntriesInCentralDir_16bit());
        log("  Number of entries (this disk): " +
            eocd.getNumEntriesInCentralDirThisDisk_16bit());
        log("  Offset of start of central directory: " +
            eocd.getOffsetOfStartOfCentralDirectoryRelativeToDisk_32bit());
        log("  Archive comment length (bytes): " +
            eocd.getZipFileCommentLength_16bit());
        log("  Archive comment: " + eocd.getZipFileComment());

        log("  Listing of entries in central directory:");
        int entryCounter = 0;
        for (CentralDirectoryFile cdf : cd.entries()) {
            entryCounter++;
            log("    Entry #" + entryCounter + ": " + cdf);
        }

        log("Local entries:");
        entryCounter = 0;
        for (LocalSectionParts lsp : archive.getLocal().entries()) {
            entryCounter++;
            log("  Entry #" + entryCounter + ": " + lsp);
        }
    }
}