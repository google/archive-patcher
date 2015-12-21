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

import java.io.File;

import com.google.archivepatcher.parts.CentralDirectoryFile;
import com.google.archivepatcher.parts.EndOfCentralDirectory;

/**
 * Bits of information about an archive that are useful but not necessarily
 * required for most tools. 
 */
public class ArchiveMetadata {
    /**
     * The file that backs the archive, if any; null otherwise.
     */
    private final File backingFile;

    /**
     * The offset of the start of the {@link EndOfCentralDirectory} part, if
     * known; -1 otherwise.
     */
    private final int offsetOfEocdPart;

    /**
     * The offset of the start of the {@link CentralDirectoryFile} entries, if
     * known; -1 otherwise.
     */
    private final int offsetOfCentralDirectoryEntries;

    /**
     * Constructs a new set of metadata having the specified values
     * @param backingFile the file that backs the archive, if any
     * @param offsetOfEocdPart the offset at which the EOCD begins
     * @param offsetOfCentralDirectoryEntries the offset at which central
     * directory entries begin
     */
    public ArchiveMetadata(File backingFile, int offsetOfEocdPart,
        int offsetOfCentralDirectoryEntries) {
        this.backingFile = backingFile;
        this.offsetOfEocdPart = offsetOfEocdPart;
        this.offsetOfCentralDirectoryEntries = offsetOfCentralDirectoryEntries;
    }

    /**
     * Returns the file that backs the archive, if any; otherwise null.
     * @return as described
     */
    public File getBackingFile() {
        return backingFile;
    }

    /**
     * Returns the offset of the start of the end of central directory part.
     * @return as described
     */
    public int getOffsetOfEocdPart() {
        return offsetOfEocdPart;
    }

    /**
     * Returns the offset of the start of the central directory entries.
     * @return as described
     */
    public int getOffsetOfCentralDirectoryEntries() {
        return offsetOfCentralDirectoryEntries;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((backingFile == null) ? 0 : backingFile.hashCode());
        result = prime * result + offsetOfCentralDirectoryEntries;
        result = prime * result + offsetOfEocdPart;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        ArchiveMetadata other = (ArchiveMetadata) obj;
        if (backingFile == null) {
            if (other.backingFile != null) return false;
        } else if (!backingFile.equals(other.backingFile)) return false;
        if (offsetOfCentralDirectoryEntries != other.offsetOfCentralDirectoryEntries) return false;
        if (offsetOfEocdPart != other.offsetOfEocdPart) return false;
        return true;
    }
}