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

package com.google.archivepatcher.patcher;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import com.google.archivepatcher.parts.CentralDirectoryFile;
import com.google.archivepatcher.parts.CentralDirectorySection;
import com.google.archivepatcher.parts.EndOfCentralDirectory;

/**
 * The first piece of a patch, consisting of a {@link CentralDirectorySection}.
 * When this part is written, it begins by writing an
 * {@link EndOfCentralDirectory} record, then outputs all of the individual
 * {@link CentralDirectoryFile} records from the
 * {@link CentralDirectorySection}:
 * <br>[EOCD]
 * <br>[Central Directory File Record #1]
 * <br>[Central Directory File Record #2]
 * <br>[Central Directory File Record #3]
 * <br>...
 */
public class BeginMetadata extends PatchPart {
    /**
     * The {@link CentralDirectorySection} that is read or written by this
     * object.
     */
    private CentralDirectorySection cd;

    /**
     * Creates an empty object with no central directory, suitable for reading.
     */
    public BeginMetadata() {
        this(null);
    }

    /**
     * Creates a fully populated object, suitable for writing.
     * 
     * @param cd the central directory section to set
     */
    public BeginMetadata(final CentralDirectorySection cd) {
        this.cd = cd;
    }

    /**
     * Returns the {@link CentralDirectorySection}.
     * @return the {@link CentralDirectorySection}
     */
    public CentralDirectorySection getCd() {
        return cd;
    }

    /**
     * Sets the {@link CentralDirectorySection}.
     * @param cd the central directory section to set
     */
    public void setCd(CentralDirectorySection cd) {
        this.cd = cd;
    }

    @Override
    public void read(DataInput input, ArchivePatcherVersion patchVersion)
        throws IOException {
        super.read(input, patchVersion);
        cd = new CentralDirectorySection();
        EndOfCentralDirectory eocd = new EndOfCentralDirectory();
        eocd.read(input);
        cd.setEocd(eocd);
        for (int x=0; x<eocd.getNumEntriesInCentralDir_16bit(); x++) {
            CentralDirectoryFile entry = new CentralDirectoryFile();
            entry.read(input);
            cd.append(entry);
        }
    }

    @Override
    public void write(DataOutput output) throws IOException {
        cd.getEocd().write(output);
        for (final CentralDirectoryFile entry : cd.entries()) {
            entry.write(output);
        }
    }

    @Override
    public int getStructureLength() {
        int length = cd.getEocd().getStructureLength();
        for (final CentralDirectoryFile entry : cd.entries()) {
            length += entry.getStructureLength();
        }
        return length;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((cd == null) ? 0 : cd.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        BeginMetadata other = (BeginMetadata) obj;
        if (cd == null) {
            if (other.cd != null)
                return false;
        } else if (!cd.equals(other.cd))
            return false;
        return true;
    }
    
    @Override
    public String toString() {
        return "BeginMetadata [cd=" + cd + "]";
    }    
}
