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

import com.google.archivepatcher.compat.Implementation;
import com.google.archivepatcher.util.IOUtils;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * Represents the optional "data descriptor" portion that can accompany a
 * {@link LocalFile} part in a {@link LocalSection}. Typically found as one
 * record in a {@link LocalSectionParts} along with a {@link LocalFile} and a
 * {@link FileData} record, this part <em>trails</em> the actual file data for
 * a given entry and records the CRC32 of the uncompressed data along with the
 * "uncompressed" and "compressed" sizes for the data.
 * @see LocalSectionParts
 * @see FileData
 * @see LocalFile
 * @see LocalSection
 */
public class DataDescriptor implements Part {
    /**
     * There is no defined standard for a data descriptor signature, and the
     * use of the signature is optional according to the ZIP specification.
     * However, the 32-bit value defined here is often used as such a signature
     * when data descriptors are present.
     */
    public final static int MAYBE_SIGNATURE= 0x08074b50;

    private long crc32_32bit;
    private long compressedSize_32bit;
    private long uncompressedSize_32bit;
    private boolean hasSignature;

    @Implementation
    public void read(final DataInput in) throws IOException {
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

    @Implementation
    public void write(final DataOutput out) throws IOException {
        if (hasSignature) {
            IOUtils.writeUnsignedInt(out, MAYBE_SIGNATURE);
        }
        IOUtils.writeRaw32Bit(out, crc32_32bit);
        IOUtils.writeUnsignedInt(out, compressedSize_32bit);
        IOUtils.writeUnsignedInt(out, uncompressedSize_32bit);
    }

    @Implementation
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

    /**
     * Returns the CRC-32 of the uncompressed data that this entry corresponds
     * to as an unsigned 32-bit value.
     * @return as described
     */
    public long getCrc32_32bit() {
        return crc32_32bit;
    }

    /**
     * See {@link #getCrc32_32bit()}.
     * @param crc32_32bit the value to set
     */
    public void setCrc32_32bit(final long crc32_32bit) {
        this.crc32_32bit = crc32_32bit;
    }

    /**
     * Returns the size of the "compressed" data that this entry corresponds to.
     * The field and getter/setter pair are named based on the ZIP
     * specification, but a more accurate name might be "sizeInArchive", as
     * this value represents the number of bytes of the archive itself that
     * are used by the entry's binary data whether compressed or not.
     * @return the value, as a 32-bit unsigned integer
     */
    public long getCompressedSize_32bit() {
        return compressedSize_32bit;
    }

    /**
     * See {@link #getCompressedSize_32bit()}.
     * @param compressedSize_32bit the value to set
     */
    public void setCompressedSize_32bit(final long compressedSize_32bit) {
        this.compressedSize_32bit = compressedSize_32bit;
    }

    /**
     * Returns the size of the uncompressed form of the data that this entry
     * corresponds to. The field and getter/setter pair are named based on the
     * ZIP specification, but a more accurate name might be "sizeWhenExtracted",
     * as this value represents the number of bytes that the entry's data uses
     * before it was archived (which is also the size after it is extracted).
     * @return the value, as a 32-bit unsigned integer
     */
    public long getUncompressedSize_32bit() {
        return uncompressedSize_32bit;
    }

    /**
     * See {@link #getUncompressedSize_32bit()}.
     * @param uncompressedSize_32bit the value to set
     */
    public void setUncompressedSize_32bit(final long uncompressedSize_32bit) {
        this.uncompressedSize_32bit = uncompressedSize_32bit;
    }

    /**
     * Returns true iff this part has the optional 32-bit signature.
     * @return as described
     */
    public boolean hasSignature() {
        return hasSignature;
    }

    /**
     * Sets whether or not this part has the optional 32-bit signature/
     * @param hasSignature true if yes, otherwise false
     */
    public void setHasSignature(final boolean hasSignature) {
        this.hasSignature = hasSignature;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (int)
            (compressedSize_32bit ^ (compressedSize_32bit >>> 32));
        result = prime * result + (int) (crc32_32bit ^ (crc32_32bit >>> 32));
        result = prime * result + (hasSignature ? 1231 : 1237);
        result = prime * result + (int)
            (uncompressedSize_32bit ^ (uncompressedSize_32bit >>> 32));
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
