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

import com.google.archivepatcher.parts.CentralDirectoryFile;
import com.google.archivepatcher.parts.CentralDirectorySection;
import com.google.archivepatcher.parts.EndOfCentralDirectory;
import com.google.archivepatcher.parts.Part;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class BeginMetadata implements Part {
    private CentralDirectorySection cd;
    public BeginMetadata() {
        this(null);
    }
    public BeginMetadata(CentralDirectorySection cd) {
        this.cd = cd;
    }

    public CentralDirectorySection getCd() {
        return cd;
    }

    public void setCd(CentralDirectorySection cd) {
        this.cd = cd;
    }

    @Override
    public void read(DataInput input) throws IOException {
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
        for (CentralDirectoryFile entry : cd.entries()) {
            entry.write(output);
        }
    }

    @Override
    public int getStructureLength() {
        int length = cd.getEocd().getStructureLength();
        for (CentralDirectoryFile entry : cd.entries()) {
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
        return "EndMetadata [cd=" + cd + "]";
    }    
}
