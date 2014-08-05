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

import com.google.archivepatcher.parts.FileData;
import com.google.archivepatcher.parts.DataDescriptor;
import com.google.archivepatcher.parts.LocalFile;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * The manifestation of a {@link PatchCommand#NEW} in a patch file, consisting
 * of a {@link LocalFile}, {@link FileData} and optional {@link DataDescriptor}.
 * When this part is written, it first writes the {@link LocalFile}, then
 * the {@link DataDescriptor} (if present), and finally the {@link FileData}:
 * <br>[Local File Record]
 * <br>[Data Descriptor Record (if present)]
 * <br>[File Data]
 * <p>
 * The reading and writing of the first two parts (the {@link LocalFile} and
 * {@link DataDescriptor}) is done using the rules in {@link RefreshMetadata},
 * from which this class is derived.
 */
public class NewMetadata extends RefreshMetadata {
    /**
     * The {@link FileData} part that is read or written by this object.
     */
    private FileData fileDataPart;

    /**
     * Creates an empty object with no parts, suitable for reading.
     */
    public NewMetadata() {
        this(null,null,null);
    }

    /**
     * Creates a fully populated object, suitable for writing.
     * 
     * @param localFilePart the part to set
     * @param compressedDataPart the part to set
     * @param dataDescriptorPart the part to set (optional)
     */
    public NewMetadata(final LocalFile localFilePart,
            final FileData compressedDataPart,
            final DataDescriptor dataDescriptorPart) {
        super(localFilePart, dataDescriptorPart);
        this.fileDataPart = compressedDataPart;
    }

    @Override
    public void read(final DataInput input) throws IOException {
        super.read(input);
        fileDataPart = new FileData(getCompressedLength());
        fileDataPart.read(input);
    }

    @Override
    public void write(final DataOutput output) throws IOException {
        super.write(output);
        fileDataPart.write(output);
    }

    @Override
    public int getStructureLength() {
        return super.getStructureLength() + fileDataPart.getStructureLength();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result
                + ((fileDataPart == null) ? 0 : fileDataPart.hashCode());
        return result;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        NewMetadata other = (NewMetadata) obj;
        if (fileDataPart == null) {
            if (other.fileDataPart != null)
                return false;
        } else if (!fileDataPart.equals(other.fileDataPart))
            return false;
        return true;
    }

    /**
     * Returns the {@link FileData}.
     * @return the {@link FileData}
     */
    public FileData getFileDataPart() {
        return fileDataPart;
    }

    /**
     * Sets the {@link FileData}.
     * @param fileDataPart the part to set
     */
    public void setFileDataPart(final FileData fileDataPart) {
        this.fileDataPart = fileDataPart;
    }

    @Override
    public String toString() {
        return "NewMetadata [" +
            "localFilePart=" + getLocalFilePart() +
            ", dataDescriptorPart=" + getDataDescriptorPart() +
            ", fileDataPart=" + fileDataPart + "]";
    }
}