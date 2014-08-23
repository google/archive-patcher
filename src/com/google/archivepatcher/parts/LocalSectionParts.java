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

import com.google.archivepatcher.meta.Flag;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * A convenience class that gathers together all of the {@link Part}s that
 * are typically found in a {@link LocalSection} for a specific file entry.
 * Since all {@link Part}s of a {@link LocalSection} entry are contiguous in
 * an archive, it is convenient to group them together - particularly for
 * reading and writing operations, where they can be treated as a single
 * contiguous entry.
 */
public class LocalSectionParts implements Part {
    private LocalFile localFilePart;
    private FileData fileDataPart;
    private DataDescriptor dataDescriptorPart;
    private final CentralDirectorySection centralDirectory;

    /**
     * Creates an object suitable for use in manipulating in-memory or writing
     * to an output file, but not for reading. The reason that reading is not
     * allowed for these objects is due to the potential lack of information
     * about the length of the "file data" section, which can only be reliably
     * obtained with the central directory (this, in turn, is because the length
     * field of a local file header may be zeroed when a data descriptor follows
     * the entry, which requires reading the file data before its length is
     * known from the data descriptor).
     * 
     * For a general purpose implementation that can be used for both reading
     * and writing, see {@link #LocalSectionParts(CentralDirectorySection)}.
     */
    public LocalSectionParts() {
        this(null);
    }

    /**
     * Creates an object suitable for any use case. Upon a call to
     * {@link #read(DataInput)}, the central directory is queried for the
     * length of the "file data" to be read from the input for the file path
     * that is read from the local file header. This allows the read operation
     * to consume the correct number of bytes in all cases (data descriptor
     * present or not).
     * 
     * If you don't need to read from input, you can use
     * {@link #LocalSectionParts()} instead.
     * 
     * @param centralDirectory the central directory which contains the length
     * information for reading the file data chunk of the entry from input.
     */
    public LocalSectionParts(CentralDirectorySection centralDirectory) {
        this.centralDirectory = centralDirectory;
    }

    /**
     * Convenience method to check if the local file header flags indicate that
     * a data descriptor is present after the file data part.
     * @return true if so, otherwise false
     */
    public boolean hasDataDescriptor() {
        return Flag.has(Flag.USE_DATA_DESCRIPTOR_FOR_SIZES_AND_CRC32,
                (short) localFilePart.getGeneralPurposeBitFlag_16bit());
    }

    @Override
    public void read(DataInput input) throws IOException {
        if (centralDirectory == null) {
            throw new IllegalStateException("cannot read without a central directory attached");
        }
        localFilePart = new LocalFile();
        localFilePart.read(input);
        CentralDirectoryFile cdf = centralDirectory.getByPath(localFilePart.getFileName());
        if (cdf == null) {
            throw new IOException("no such record in central directory: " + localFilePart.getFileName());
        }
        fileDataPart = new FileData(
                (int) cdf.getCompressedSize_32bit());
        fileDataPart.read(input);
        if (!hasDataDescriptor()) return;
        dataDescriptorPart = new DataDescriptor();
        dataDescriptorPart.read(input);
    }

    @Override
    public void write(DataOutput output) throws IOException {
        localFilePart.write(output);
        fileDataPart.write(output);
        if (!hasDataDescriptor()) return;
        dataDescriptorPart.write(output);
    }

    @Override
    public int getStructureLength() {
        int length = localFilePart.getStructureLength();
        length += fileDataPart.getStructureLength();
        if (hasDataDescriptor()) {
            length += dataDescriptorPart.getStructureLength();
        }
        return length;
    }

    /**
     * Returns the {@link LocalFile} component of this object.
     * @return as described
     */
    public LocalFile getLocalFilePart() {
        return localFilePart;
    }

    /**
     * See {@link #getLocalFilePart()}.
     * @param localFilePart the value to set
     */
    public void setLocalFilePart(final LocalFile localFilePart) {
        this.localFilePart = localFilePart;
    }

    /**
     * Returns the {@link FileData} component of this object.
     * @return as described
     */
    public FileData getFileDataPart() {
        return fileDataPart;
    }

    /**
     * See {@link #getFileDataPart()}.
     * @param fileDataPart the value to set
     */
    public void setFileDataPart(final FileData fileDataPart) {
        this.fileDataPart = fileDataPart;
    }

    /**
     * Returns the {@link DataDescriptor} component of this object.
     * @return as described
     */
    public DataDescriptor getDataDescriptorPart() {
        return dataDescriptorPart;
    }

    /**
     * See {@link #getDataDescriptorPart()}.
     * @param dataDescriptor the value to set
     */
    public void setDataDescriptorPart(final DataDescriptor dataDescriptor) {
        this.dataDescriptorPart = dataDescriptor;
    }

    // Deliberately omits computation of the central directory
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((fileDataPart == null) ?
            0 : fileDataPart.hashCode());
        result = prime * result + ((dataDescriptorPart == null) ?
            0 : dataDescriptorPart.hashCode());
        result = prime * result + ((localFilePart == null) ?
            0 : localFilePart.hashCode());
        return result;
    }

    // Deliberately omits comparison with the central directory
    @Override
    public boolean equals(final Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        LocalSectionParts other = (LocalSectionParts) obj;
        if (fileDataPart == null) {
            if (other.fileDataPart != null)
                return false;
        } else if (!fileDataPart.equals(other.fileDataPart))
            return false;
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

    // Deliberately omits the central directory
    @Override
    public String toString() {
        return "LocalSectionParts [" +
            "localFilePart=" + localFilePart +
            ", fileDataPart=" + fileDataPart +
            ", dataDescriptorPart=" + dataDescriptorPart + "]";
    }
}
