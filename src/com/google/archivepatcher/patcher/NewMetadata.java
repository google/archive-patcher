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

public class NewMetadata extends RefreshMetadata {
    private FileData fileDataPart;

    public NewMetadata() {
        this(null,null,null);
    }
    public NewMetadata(LocalFile localFilePart,
            FileData compressedDataPart,
            DataDescriptor dataDescriptorPart) {
        super(localFilePart, dataDescriptorPart);
        this.fileDataPart = compressedDataPart;
    }
    @Override
    public void read(DataInput input) throws IOException {
        super.read(input);
        fileDataPart = new FileData(getCompressedLength());
        fileDataPart.read(input);
    }

    @Override
    public void write(DataOutput output) throws IOException {
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
    public boolean equals(Object obj) {
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
    public FileData getFileDataPart() {
        return fileDataPart;
    }
    public void setFileDataPart(FileData compressedDataPart) {
        this.fileDataPart = compressedDataPart;
    }

}
