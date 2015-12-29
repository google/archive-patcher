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
 * Disallows the use of any extra fields that do not appear to be solely for
 * the purpose of alignment at a 32 or 64 bit word boundary or a page boundary.
 * These offsets are often so that resources within an archive can be memory
 * mapped directly from the archive without the need for unaligned access.
 */
public class DisallowNonAlignmentLocalExtrasRule extends ArchiveRule {
    @Override
    protected void checkInternal() {
        final ArchiveMetadata metadata = archive.getArchiveMetadata();
        if (metadata == null || metadata.getBackingFile() == null) {
            notOk("archive",
                "archive isn't backed by a file, can't check alignment");
        }

        // Sort all entries in the central directory according to the offset at
        // which the corresponding local part begins.
        final CentralDirectorySection cds = archive.getCentralDirectory();
        final Comparator<CentralDirectoryFile> comparator =
            new CentralDirectoryRelativeOffsetComparator();
        final List<CentralDirectoryFile> cdsFiles =
            new ArrayList<CentralDirectoryFile>(cds.entries());
        Collections.sort(cdsFiles, comparator);

        long nextExpectedOffset = 0;
        for (int x=0; x<cdsFiles.size(); x++) {
            final CentralDirectoryFile cdf = cdsFiles.get(x);
            final long startsAtOffset =
                cdf.getRelativeOffsetOfLocalHeader_32bit();
            if (startsAtOffset != nextExpectedOffset) {
                notOk("archive",
                    "gap detected at offset " + nextExpectedOffset);
            }
            final LocalSectionParts lsp =
                archive.getLocal().getByPath(cdf.getFileName());
            nextExpectedOffset = startsAtOffset + lsp.getStructureLength();
            final int extraLength =
                lsp.getLocalFilePart().getExtraFieldLength_16bit();
            if (extraLength != 0) {
                // Only allowed if the next expected offset is a multiple of
                // 4, 8, or 4096 bytes *and* this isn't the last entry in the
                // archive.
                if (x == cdsFiles.size() - 1) {
                    notOk(cdf.getFileName(), "non-empty extras in final entry");
                } else {
                    boolean wordAligned = (nextExpectedOffset % 4 == 0) ||
                        (nextExpectedOffset % 8 == 0);
                    boolean pageAligned = nextExpectedOffset % 4096 == 0;
                    if (!wordAligned&& !pageAligned) {
                        notOk(cdf.getFileName(), "non-alignment extras of "
                            + "length " + extraLength + " aligns next entry "
                            + "to offset " + nextExpectedOffset +
                            ", which is not a boundary");
                    }
                }
            }
        }
    }
}