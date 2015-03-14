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
import java.util.Arrays;

import com.google.archivepatcher.compression.BuiltInCompressionEngine;
import com.google.archivepatcher.compression.Compressor;
import com.google.archivepatcher.parts.DataDescriptor;
import com.google.archivepatcher.parts.LocalFile;
import com.google.archivepatcher.util.IOUtils;

/**
 * The manifestation of a {@link PatchCommand#NEW} in a patch file, consisting
 * of a {@link LocalFile}, optional {@link DataDescriptor}, compression engine
 * ID, and some data compressed by the compression engine that, when
 * uncompressed, represents the original data from the new archive. The
 * compression applied here has nothing to do with the in-situ compression of
 * the data from the new archive. For example, if the data in the new archive
 * is *uncompressed* in-situ, a compression engine may be used to compress that
 * data for transfer within the patch itself.
 * When this part is written, it first writes the {@link LocalFile}, then
 * the {@link DataDescriptor} (if present), the compression engine ID, the
 * length of the compressed data and the compressed data.
 * <br>[Local File Record]
 * <br>[Data Descriptor Record (if present)]
 * <br>[Compression engine ID]
 * <br>[Length of compressed data]
 * <br>[Compressed data]
 * <p>
 * The reading and writing of the first two parts (the {@link LocalFile} and
 * {@link DataDescriptor}) is done using the rules in {@link RefreshMetadata},
 * from which this class is derived.
 */
public class NewMetadata extends RefreshMetadata {

    /**
     * The ID of the {@link Compressor} that was used to compress the data in
     * this directive.
     * @since V2
     */
    private int patchingCompressionEngineId;

    /**
     * The length of the compressed data.
     */
    private int patchingCompressedDataLength;

    /**
     * The compressed data.
     */
    private byte[] data;

    /**
     * Creates an empty object with no parts, suitable for reading.
     */
    public NewMetadata() {
        this(null,null, BuiltInCompressionEngine.NONE.getId(), null);
    }

    /**
     * Creates a fully populated object, suitable for writing.
     * 
     * @param localFilePart the part to set
     * @param compressionEngineId the ID of the compression engine used to
     * compress the data in the specified compressedDataPart
     * @param compressedData the compressed data
     * @param dataDescriptorPart the part to set (optional)
     */
    public NewMetadata(final LocalFile localFilePart,
            final DataDescriptor dataDescriptorPart,
            final int compressionEngineId,
            final byte[] compressedData) {
        super(localFilePart, dataDescriptorPart);
        this.patchingCompressionEngineId = compressionEngineId;
        this.data = compressedData;
        this.patchingCompressedDataLength = compressedData == null ? 0 : compressedData.length;
    }

    @Override
    public void read(final DataInput input, ArchivePatcherVersion patchVersion)
        throws IOException {
        super.read(input, patchVersion);
        if(patchVersion.asInteger >= 2) {
            patchingCompressionEngineId = (int) IOUtils.readUnsignedInt(input);
            patchingCompressedDataLength = (int) IOUtils.readUnsignedInt(input);
            data = new byte[patchingCompressedDataLength];
        } else {
            patchingCompressionEngineId = BuiltInCompressionEngine.NONE.getId();
            patchingCompressedDataLength = -1;
            data = new byte[getCompressedLength()];
        }
        input.readFully(data);
    }

    @Override
    public void write(final DataOutput output) throws IOException {
        super.write(output);
        IOUtils.writeUnsignedInt(output, patchingCompressionEngineId);
        IOUtils.writeUnsignedInt(output, data.length);
        output.write(data);
    }

    @Override
    public int getStructureLength() {
        int result = 0;
        if (getPatchVersionForReading().asInteger >= 2) {
            result = super.getStructureLength() + + 4 + 4 + patchingCompressedDataLength;
        } else {
            result = super.getStructureLength() + getCompressedLength();
        }
        return result;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + Integer.hashCode(patchingCompressionEngineId);
        result = prime * result + Integer.hashCode(patchingCompressedDataLength);
        result = prime * result + Arrays.hashCode(data);
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
        if (patchingCompressionEngineId != other.patchingCompressionEngineId)
            return false;
        if (patchingCompressedDataLength != other.patchingCompressedDataLength)
            return false;
        if (!Arrays.equals(data, other.data))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "NewMetadata [" +
            "localFilePart=" + getLocalFilePart() +
            ", dataDescriptorPart=" + getDataDescriptorPart() +
            ", patchingCompressionEngineId=" + patchingCompressionEngineId + 
            ", patchingCompressedDataLength=" + patchingCompressedDataLength + 
            ", compressedData=" + Arrays.toString(data) + "]";
    }

    /**
     * Returns the ID of the compression engine that compressed the data for
     * the purposes of patching (as opposed to the compression engine that
     * may or may not have been used to compress the data within the source
     * archive)
     * @return the ID
     */
    public int getPatchingCompressionEngineId() {
        return patchingCompressionEngineId;
    }

    /**
     * Returns the length of the data. If a compression engine has been used,
     * this is the length of the output from that compression engine; otherwise
     * it is simply the "compressed length" of the data as reported by the
     * source archive.
     * @return the length
     */
    public int getDataLength() {
        int result = 0;
        if (getPatchVersionForReading().asInteger >= 2) {
            result = patchingCompressedDataLength;
        } else {
            result = getCompressedLength(); // Superclass impl
        }
        return result;
    }

    /**
     * Returns the data itself. Care should be taken not to modify this. If a
     * compression engine has been used, this is the output of that compression
     * engine; otherwise it is simple the "compressed data" from the source
     * archive.
     * @return the data
     */
    public byte[] getData() {
        return data;
    }
}