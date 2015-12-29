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

package com.google.archivepatcher.tools.policy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.google.archivepatcher.ArchiveMetadata;
import com.google.archivepatcher.parts.CentralDirectoryFile;
import com.google.archivepatcher.parts.CentralDirectorySection;
import com.google.archivepatcher.parts.LocalSectionParts;

/**
 * Disallows any gaps in an archive, ensuring that all bits from start to finish
 * are part of the required structures in the archive. An archive normally can
 * have arbitrary gaps within it that can contain any information, so long as
 * they aren't listed in the central directory; this scanner prohibits any such
 * bits.
 * <p>
 * First, the central directory is examined and sorted in the order of the
 * entries in the file. The scanner ensures that each byte from the beginning
 * of the file to the start of the central directory is part of one of the local
 * entries. The scanner then checks that the central directory begins
 * immediately after the last byte of the local entries. The scanner then checks
 * that the end-of-central-directory begins immediately after the last byte of
 * the central directory entries. Finally, the scanner ensures that the final
 * byte of the end-of-central-directory record is the final byte of the file.
 * <p>
 * It is still possible to "hide" data in the archive by (ab)using the comments
 * and extras fields, but other scanners can check for these things.
 */
public class DisallowArchiveGapsRule extends ArchiveRule {
    @Override
    protected void checkInternal() {
        final ArchiveMetadata metadata = archive.getArchiveMetadata();
        if (metadata == null || metadata.getBackingFile() == null) {
            notOk("archive",
                "archive isn't backed by a file, can't check for gaps");
        }

        // Sort all entries in the central directory according to the offset at
        // which the corresponding local part begins.
        final CentralDirectorySection cds = archive.getCentralDirectory();
        final Comparator<CentralDirectoryFile> comparator =
            new CentralDirectoryRelativeOffsetComparator();
        final List<CentralDirectoryFile> cdsFiles =
            new ArrayList<CentralDirectoryFile>(cds.entries());
        Collections.sort(cdsFiles, comparator);

        // Ensure all entries are contiguous.
        // This is a no-op for empty archives.
        long nextExpectedOffset = 0;
        for (CentralDirectoryFile cdf : cdsFiles) {
            final long startsAtOffset =
                cdf.getRelativeOffsetOfLocalHeader_32bit();
            if (startsAtOffset != nextExpectedOffset) {
                notOk("archive",
                    "gap detected at offset " + nextExpectedOffset);
            }
            final LocalSectionParts lsp =
                archive.getLocal().getByPath(cdf.getFileName());
            nextExpectedOffset = startsAtOffset + lsp.getStructureLength();
        }

        // Ensure there is no space between the final entry and the central
        // directory.
        final long offsetOfCentralDirectory =
            metadata.getOffsetOfCentralDirectoryEntries();
        if (offsetOfCentralDirectory != nextExpectedOffset) {
            notOk("archive",
                "gap detected before central directory at offset " +
                nextExpectedOffset);
        }
        nextExpectedOffset = offsetOfCentralDirectory +
            cds.getStructureLength() - cds.getEocd().getStructureLength();

        // Ensure there is no space between the central directory and the
        // end-of-central-directory record
        final long offsetOfEocd = metadata.getOffsetOfEocdPart();
        if (offsetOfEocd != nextExpectedOffset) {
            notOk("archive",
                "gap detected before end-of-central-directory at offset " +
                nextExpectedOffset);
        }
        nextExpectedOffset = offsetOfEocd + cds.getEocd().getStructureLength();

        // Finally, ensure that the last byte of the central directory is the
        // last byte of the file.
        final long backingFileLength = metadata.getBackingFile().length();
        if (backingFileLength != nextExpectedOffset) {
            notOk("archive",
                "gap detected after end-of-central-directory at offset "
                + nextExpectedOffset);
        }
    }
}
