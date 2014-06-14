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

import com.google.archivepatcher.meta.Flag;
import com.google.archivepatcher.parts.DataDescriptor;
import com.google.archivepatcher.parts.LocalFile;
import com.google.archivepatcher.parts.Part;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class RefreshMetadata implements Part {
    protected LocalFile localFilePart;
    protected DataDescriptor dataDescriptorPart;

    public RefreshMetadata() {
        this(null,null);
    }

    public RefreshMetadata(LocalFile localFilePart,
            DataDescriptor dataDescriptorPart) {
        super();
        this.localFilePart = localFilePart;
        this.dataDescriptorPart = dataDescriptorPart;
    }

    public boolean hasDataDescriptor() {
        return Flag.has(Flag.USE_DATA_DESCRIPTOR_FOR_SIZES_AND_CRC32,
                (short) localFilePart.getGeneralPurposeBitFlag_16bit());
    }

    public int getCompressedLength() {
        if (hasDataDescriptor()) {
            return (int) dataDescriptorPart.getCompressedSize_32bit();
        }
        return (int) localFilePart.getCompressedSize_32bit();
    }

    @Override
    public void read(DataInput input) throws IOException {
        localFilePart = new LocalFile();
        localFilePart.read(input);
        if (!hasDataDescriptor()) return;
        dataDescriptorPart = new DataDescriptor();
        dataDescriptorPart.read(input);
    }

    @Override
    public void write(DataOutput output) throws IOException {
        localFilePart.write(output);
        if (!hasDataDescriptor()) return;
        dataDescriptorPart.write(output);
    }

    @Override
    public int getStructureLength() {
        int length = localFilePart.getStructureLength();
        if (hasDataDescriptor()) {
            length += dataDescriptorPart.getStructureLength();
        }
        return length;
    }

    public LocalFile getLocalFilePart() {
        return localFilePart;
    }

    public void setLocalFilePart(LocalFile localFilePart) {
        this.localFilePart = localFilePart;
    }

    public DataDescriptor getDataDescriptorPart() {
        return dataDescriptorPart;
    }

    public void setDataDescriptorPart(DataDescriptor dataDescriptorPart) {
        this.dataDescriptorPart = dataDescriptorPart;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result
                + ((dataDescriptorPart == null) ? 0 : dataDescriptorPart.hashCode());
        result = prime * result + ((localFilePart == null) ? 0 : localFilePart.hashCode());
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
        RefreshMetadata other = (RefreshMetadata) obj;
        if (dataDescriptorPart == null) {
            if (other.dataDescriptorPart != null)
                return false;
        } else if (!dataDescriptorPart.equals(other.dataDescriptorPart))
            return false;
        if (localFilePart == null) {
            if (other.localFilePart != null)
                return false;
        } else if (!localFilePart.equals(other.localFilePart))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "RefreshMetadata [localFilePart=" + localFilePart +
                ", dataDescriptorPart=" + dataDescriptorPart
                + "]";
    }
}