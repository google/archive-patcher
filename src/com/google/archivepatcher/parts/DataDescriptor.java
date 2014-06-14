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

package com.google.archivepatcher.parts;

import com.google.archivepatcher.util.IOUtils;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class DataDescriptor implements Part {
    public final static int MAYBE_SIGNATURE= 0x08074b50;
    private long crc32_32bit;
    private long compressedSize_32bit;
    private long uncompressedSize_32bit;
    private boolean hasSignature;

    @Override
    public void read(DataInput in) throws IOException {
        long maybeSignature = IOUtils.readUnsignedInt(in);
        if (maybeSignature == MAYBE_SIGNATURE) {
            // Has signature, so read again
            hasSignature = true;
            crc32_32bit = IOUtils.readUnsignedInt(in);
        } else {
            // No signature, the first bits are the crc32.
            hasSignature = false;
            crc32_32bit = maybeSignature & 0x00000000ffffffff;
        }
        compressedSize_32bit = IOUtils.readUnsignedInt(in);
        uncompressedSize_32bit = IOUtils.readUnsignedInt(in);
    }

    @Override
    public void write(DataOutput out) throws IOException {
        if (hasSignature) {
            IOUtils.writeUnsignedInt(out, MAYBE_SIGNATURE);
        }
        IOUtils.writeUnsignedInt(out, crc32_32bit);
        IOUtils.writeUnsignedInt(out, compressedSize_32bit);
        IOUtils.writeUnsignedInt(out, uncompressedSize_32bit);
    }

    @Override
    public int getStructureLength() {
        return 4+4+4+ (hasSignature ? 4 : 0);
    }

    @Override
    public String toString() {
        return "DataDescriptor [hasSignature=" + hasSignature +
                ", crc32_32bit=" + crc32_32bit +
                ", compressedSize_32bit=" + compressedSize_32bit +
                ", uncompressedSize_32bit=" + uncompressedSize_32bit + "]";
    }

    public long getCrc32_32bit() {
        return crc32_32bit;
    }

    public void setCrc32_32bit(long crc32_32bit) {
        this.crc32_32bit = crc32_32bit;
    }

    public long getCompressedSize_32bit() {
        return compressedSize_32bit;
    }

    public void setCompressedSize_32bit(long compressedSize_32bit) {
        this.compressedSize_32bit = compressedSize_32bit;
    }

    public long getUncompressedSize_32bit() {
        return uncompressedSize_32bit;
    }

    public void setUncompressedSize_32bit(long uncompressedSize_32bit) {
        this.uncompressedSize_32bit = uncompressedSize_32bit;
    }

    public boolean hasSignature() {
        return hasSignature;
    }

    public void setHasSignature(boolean hasSignature) {
        this.hasSignature = hasSignature;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (int) (compressedSize_32bit ^ (compressedSize_32bit >>> 32));
        result = prime * result + (int) (crc32_32bit ^ (crc32_32bit >>> 32));
        result = prime * result + (hasSignature ? 1231 : 1237);
        result = prime * result + (int) (uncompressedSize_32bit ^ (uncompressedSize_32bit >>> 32));
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
        DataDescriptor other = (DataDescriptor) obj;
        if (compressedSize_32bit != other.compressedSize_32bit)
            return false;
        if (crc32_32bit != other.crc32_32bit)
            return false;
        if (hasSignature != other.hasSignature)
            return false;
        if (uncompressedSize_32bit != other.uncompressedSize_32bit)
            return false;
        return true;
    }
}
