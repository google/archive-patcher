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

import com.google.archivepatcher.meta.Flag;
import com.google.archivepatcher.parts.DataDescriptor;
import com.google.archivepatcher.parts.LocalFile;
import com.google.archivepatcher.parts.Part;

/**
 * The manifestation of a {@link PatchCommand#REFRESH} in a patch file,
 * consisting of a {@link LocalFile} and optional {@link DataDescriptor}.
 * When this part is written, it first writes the {@link LocalFile}, then
 * the {@link DataDescriptor} (if present):
 * <br>[Local File Record]
 * <br>[Data Descriptor Record (if present)]
 * <p>
 * The data descriptor record will only be read (or written, when writing) if
 * the {@link LocalFile} part has the
 * {@link Flag#USE_DATA_DESCRIPTOR_FOR_SIZES_AND_CRC32} bit set.
 */
public class RefreshMetadata implements Part {
    /**
     * The {@link LocalFile} part that is read or written by this object.
     */
    private LocalFile localFilePart;

    /**
     * The {@link DataDescriptor} part that is read or written by this object.
     */
    private DataDescriptor dataDescriptorPart;

    /**
     * Creates an empty object with no parts, suitable for reading.
     */
    public RefreshMetadata() {
        this(null,null);
    }

    /**
     * Creates a fully populated object, suitable for writing.
     * 
     * @param localFilePart the part to set
     * @param dataDescriptorPart the part to set (optional)
     */
    public RefreshMetadata(final LocalFile localFilePart,
            final DataDescriptor dataDescriptorPart) {
        super();
        this.localFilePart = localFilePart;
        this.dataDescriptorPart = dataDescriptorPart;
    }

    /**
     * Returns true if and only if the {@link LocalFile} part has the
     * {@link Flag#USE_DATA_DESCRIPTOR_FOR_SIZES_AND_CRC32} bit set.
     * @return as described
     */
    public boolean hasDataDescriptor() {
        return Flag.has(Flag.USE_DATA_DESCRIPTOR_FOR_SIZES_AND_CRC32,
                (short) localFilePart.getGeneralPurposeBitFlag_16bit());
    }

    /**
     * Returns the length of the data as it exists in its raw form within the
     * archive. This is referred to in ZIP documentation as the "compressed"
     * length, but really represents the length of the data's binary data at
     * rest in the archive itself, regardless of whether it is compressed or
     * not.
     * @return the length, coming from the {@link DataDescriptor} if the
     * {@link Flag#USE_DATA_DESCRIPTOR_FOR_SIZES_AND_CRC32} bit is set in the
     * {@link LocalFile} part (or from the {@link LocalFile} part if it is not)
     * @see LocalFile#getCompressedSize_32bit()
     * @see DataDescriptor#getCompressedSize_32bit()
     */
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

    /**
     * Returns the {@link LocalFile}.
     * @return the {@link LocalFile}
     */
    public LocalFile getLocalFilePart() {
        return localFilePart;
    }

    /**
     * Sets the {@link LocalFile}.
     * @param localFilePart the part to set
     */
    public void setLocalFilePart(LocalFile localFilePart) {
        this.localFilePart = localFilePart;
    }

    /**
     * Returns the {@link DataDescriptor}.
     * @return the {@link DataDescriptor}
     */
    public DataDescriptor getDataDescriptorPart() {
        return dataDescriptorPart;
    }

    /**
     * Sets the {@link DataDescriptor}.
     * @param dataDescriptorPart the part to set
     */
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
        return "RefreshMetadata [" +
            "localFilePart=" + localFilePart +
            ", dataDescriptorPart=" + dataDescriptorPart + "]";
    }
}