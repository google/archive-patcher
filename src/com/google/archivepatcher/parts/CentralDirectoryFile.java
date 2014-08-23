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

import com.google.archivepatcher.meta.CompressionMethod;
import com.google.archivepatcher.util.IOUtils;
import com.google.archivepatcher.util.MsDosDate;
import com.google.archivepatcher.util.MsDosTime;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;

/**
 * Represents one "central directory" entry in a ZIP-like archive. Each
 * "central directory" entry must have a corresponding entry in a local section,
 * and in general the fields in each location that are identically named
 * <em>should</em> have identical values. Some tools do not modify local
 * entries the same way that they modify the corresponding entries in the
 * central directory; for example, Android's "zipalign" will use the "extras"
 * field to pad resources such that they start on 32-bit or 64-bit boundaries
 * for convenient in-place mmap'ing of uncompressed resources within the
 * archive. Because these kinds of tools exist, it is an error to assume that
 * "local" fields will always match the similarly-named fields in the entry
 * within the central directory, even though they <em>almost always</em> do.
 * <p>
 * Note that most values in the ZIP specification are unsigned, but Java only
 * provides signed values. As a result, most 16-bit values from the
 * specification are handled using "int" (32-bit) and most 32-bit values from
 * the specification are handled using "long" (64-bit). This allows the full
 * range of values from the specification to be utilized, but may mislead
 * callers into thinking that larger values can be used where they cannot.
 * <p>
 * To help avoid confusion, all getter and setter methods have a suffix
 * indicating the number of bits that can actually be used, i.e. "_16bit" and
 * "_32bit". These suffixes are <em>always</em> authoritative. Where practical,
 * runtime checks are added to make sure that illegal values are disallowed
 * (e.g., a value higher than 65535 passed to a "_16bit" setter method should
 * result in an {@link IllegalArgumentException}.
 * @see LocalFile
 * @see CentralDirectorySection
 */
public class CentralDirectoryFile implements Part {
    /**
     * Standard 32-bit signature for a "central directory file entry" in a
     * ZIP-like archive.
     */
    public final static int SIGNATURE = 0x2014b50;

    private int versionMadeBy_16bit;
    private int versionNeededToExtract_16bit;
    private int generalPurposeBitFlag_16bit;
    private int compressionMethod_16bit;
    private int lastModifiedFileTime_16bit;
    private int lastModifiedFileDate_16bit;
    private long crc32_32bit;
    private long compressedSize_32bit;
    private long uncompressedSize_32bit;
    private int fileNameLength_16bit;
    private int extraFieldLength_16bit;
    private int fileCommentLength_16bit;
    private int diskNumberStart_16bit;
    private int internalFileAttributes_16bit;
    private long externalFileAttributes_32bit;
    private long relativeOffsetOfLocalHeader_32bit;
    private String fileName;
    private byte[] extraField;
    private String fileComment;

    /**
     * Convenience method to extract and return the {@link CompressionMethod}
     * set in this entry's flags.
     * @return the {@link CompressionMethod}
     */
    public CompressionMethod getCompressionMethod() {
        return CompressionMethod.fromHeaderValue(compressionMethod_16bit);
    }

    /**
     * Convenience method to extract and convert the standard ("MS-DOS" based)
     * last-modified date from this entry. Note that the zip specification
     * defines such a date for all entries, so this will always be present
     * even if additional (non-MS-DOS) last-modified information exists in the
     * entry.
     * @return a {@link MsDosDate} representing the last-modified date that was
     * present on the original source when it was archived
     * @see #getLastModifiedFileDate_16bit()
     * @see #getLastModifiedFileTime()
     */
    public MsDosDate getLastModifiedFileDate() {
        return MsDosDate.from16BitPackedValue(lastModifiedFileDate_16bit);
    }

    /**
     * Convenience method to extract and convert the standard ("MS-DOS" based)
     * last-modified time from this entry. Note that the zip specification
     * defines such a time for all entries, so this will always be present
     * even if additional (non-MS-DOS) last-modified information exists in the
     * entry.
     * @return a {@link MsDosTime} representing the last-modified time that was
     * present on the original source when it was archived
     * @see #getLastModifiedFileTime_16bit()
     * @see #getLastModifiedFileDate()
     */
    public MsDosTime getLastModifiedFileTime() {
        return MsDosTime.from16BitPackedValue(lastModifiedFileTime_16bit);
    }

    @Override
    public void read(final DataInput in) throws IOException {
        final int signature = (int) IOUtils.readUnsignedInt(in);
        if (signature != SIGNATURE) throw new IOException("Invalid signature: " + signature);
        versionMadeBy_16bit = IOUtils.readUnsignedShort(in);
        versionNeededToExtract_16bit = IOUtils.readUnsignedShort(in);
        generalPurposeBitFlag_16bit = IOUtils.readUnsignedShort(in);
        compressionMethod_16bit = IOUtils.readUnsignedShort(in);
        lastModifiedFileTime_16bit = IOUtils.readUnsignedShort(in);
        lastModifiedFileDate_16bit = IOUtils.readUnsignedShort(in);
        crc32_32bit = IOUtils.readUnsignedInt(in);
        compressedSize_32bit = IOUtils.readUnsignedInt(in);
        uncompressedSize_32bit = IOUtils.readUnsignedInt(in);
        fileNameLength_16bit = IOUtils.readUnsignedShort(in);
        extraFieldLength_16bit = IOUtils.readUnsignedShort(in);
        fileCommentLength_16bit = IOUtils.readUnsignedShort(in);
        diskNumberStart_16bit = IOUtils.readUnsignedShort(in);
        internalFileAttributes_16bit = IOUtils.readUnsignedShort(in);
        externalFileAttributes_32bit = IOUtils.readUnsignedInt(in);
        relativeOffsetOfLocalHeader_32bit = IOUtils.readUnsignedInt(in);
        
        if (fileNameLength_16bit > 0) {
            fileName = IOUtils.readUTF8(in, fileNameLength_16bit);
        }
        if (extraFieldLength_16bit > 0) {
            extraField = new byte[extraFieldLength_16bit];
            in.readFully(extraField);
        }
        if (fileCommentLength_16bit > 0) {
            fileComment= IOUtils.readUTF8(in, fileCommentLength_16bit);
        }
    }

    @Override
    public void write(final DataOutput out) throws IOException {
        IOUtils.writeRaw32Bit(out, SIGNATURE);
        IOUtils.writeUnsignedShort(out, versionMadeBy_16bit);
        IOUtils.writeUnsignedShort(out, versionNeededToExtract_16bit);
        IOUtils.writeRaw16Bit(out, generalPurposeBitFlag_16bit);
        IOUtils.writeRaw16Bit(out, compressionMethod_16bit);
        IOUtils.writeUnsignedShort(out, lastModifiedFileTime_16bit);
        IOUtils.writeUnsignedShort(out, lastModifiedFileDate_16bit);
        IOUtils.writeRaw32Bit(out, crc32_32bit);
        IOUtils.writeUnsignedInt(out, compressedSize_32bit);
        IOUtils.writeUnsignedInt(out, uncompressedSize_32bit);
        IOUtils.writeUnsignedShort(out, fileNameLength_16bit);
        IOUtils.writeUnsignedShort(out, extraFieldLength_16bit);
        IOUtils.writeUnsignedShort(out, fileCommentLength_16bit);
        IOUtils.writeUnsignedShort(out, diskNumberStart_16bit);
        IOUtils.writeRaw16Bit(out, internalFileAttributes_16bit);
        IOUtils.writeRaw32Bit(out, externalFileAttributes_32bit);
        IOUtils.writeUnsignedInt(out, relativeOffsetOfLocalHeader_32bit);
        if (fileName != null) {
            IOUtils.writeUTF8(out, fileName);
        }
        if (extraField != null) {
            out.write(extraField);
        }
        if (fileComment != null) {
            IOUtils.writeUTF8(out, fileComment);
        }
    }

    @Override
    public int getStructureLength() {
        return 4+2+2+2+2+2+2+4+4+4+2+2+2+2+2+4+4+
                fileNameLength_16bit +
                extraFieldLength_16bit +
                fileCommentLength_16bit;
    }

    @Override
    public String toString() {
        return "CentralDirectoryFile ["
                + "versionMadeBy_16bit=" + versionMadeBy_16bit
                + ", versionNeededToExtract_16bit=" + versionNeededToExtract_16bit
                + ", generalPurposeBitFlag_16bit=" + generalPurposeBitFlag_16bit
                + ", compressionMethod_16bit=" + getCompressionMethod()
                + ", lastModifiedFileTime=" + getLastModifiedFileTime()
                + ", lastModifiedFileDate=" + getLastModifiedFileDate()
                + ", crc32_32bit=" + crc32_32bit
                + ", compressedSize_32bit=" + compressedSize_32bit
                + ", uncompressedSize_32bit=" + uncompressedSize_32bit
                + ", fileNameLength_16bit=" + fileNameLength_16bit
                + ", extraFieldLength_16bit=" + extraFieldLength_16bit
                + ", fileCommentLength_16bit=" + fileCommentLength_16bit
                + ", diskNumberStart_16bit=" + diskNumberStart_16bit
                + ", internalFileAttributes_16bit=" + internalFileAttributes_16bit
                + ", externalFileAttributes_32bit=" + externalFileAttributes_32bit
                + ", relativeOffsetOfLocalHeader_32bit=" + relativeOffsetOfLocalHeader_32bit
                + ", fileName=" + fileName
                + ", extraField=" + Arrays.toString(extraField)
                + ", fileComment=" + fileComment + "]";
    }

    // Autogenerated, no special logic
    // Modifications: Change order to find differences fast, starting with crc
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (int) (crc32_32bit ^ (crc32_32bit >>> 32));
        result = prime * result + (int) (compressedSize_32bit ^
            (compressedSize_32bit >>> 32));
        result = prime * result + compressionMethod_16bit;
        result = prime * result + diskNumberStart_16bit;
        result = prime * result + (int) (externalFileAttributes_32bit ^
            (externalFileAttributes_32bit >>> 32));
        result = prime * result + Arrays.hashCode(extraField);
        result = prime * result + extraFieldLength_16bit;
        result = prime * result + ((fileComment == null) ?
            0 : fileComment.hashCode());
        result = prime * result + fileCommentLength_16bit;
        result = prime * result + ((fileName == null) ?
            0 : fileName.hashCode());
        result = prime * result + fileNameLength_16bit;
        result = prime * result + generalPurposeBitFlag_16bit;
        result = prime * result + internalFileAttributes_16bit;
        result = prime * result + lastModifiedFileDate_16bit;
        result = prime * result + lastModifiedFileTime_16bit;
        result = prime * result + (int) (relativeOffsetOfLocalHeader_32bit
                ^ (relativeOffsetOfLocalHeader_32bit >>> 32));
        result = prime * result + (int) (uncompressedSize_32bit ^
            (uncompressedSize_32bit >>> 32));
        result = prime * result + versionMadeBy_16bit;
        result = prime * result + versionNeededToExtract_16bit;
        return result;
    }

    @Override
    public boolean equals(final Object obj) {
        return equals(obj, false);
    }

    /**
     * Provides an alternative implementation of {@link #equals(Object)} that
     * evaluates objects independent of the position of the paired
     * {@link LocalFile} entry. This allows for equivalence checks that are not
     * sensitive to the order of entries within an archive.
     * 
     * @param obj the object to compare to
     * @return the same as {@link #equals(Object)}, except that the position
     * of the corresponding {@link LocalFile} entry is not taken into
     * consideration in any way
     */
    public boolean positionIndependentEquals(final Object obj) {
        return equals(obj, true);
    }

    // Autogenerated, no special logic
    // Modifications: Change order to find differences fast, starting with crc
    private boolean equals(final Object obj,
        final boolean positionIndependent) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final CentralDirectoryFile other = (CentralDirectoryFile) obj;
        if (crc32_32bit != other.crc32_32bit)
            return false;
        if (!positionIndependent) {
            if (relativeOffsetOfLocalHeader_32bit !=
                other.relativeOffsetOfLocalHeader_32bit)
                return false;
        }
        if (compressedSize_32bit != other.compressedSize_32bit)
            return false;
        if (compressionMethod_16bit != other.compressionMethod_16bit)
            return false;
        if (diskNumberStart_16bit != other.diskNumberStart_16bit)
            return false;
        if (externalFileAttributes_32bit != other.externalFileAttributes_32bit)
            return false;
        if (!Arrays.equals(extraField, other.extraField))
            return false;
        if (extraFieldLength_16bit != other.extraFieldLength_16bit)
            return false;
        if (fileComment == null) {
            if (other.fileComment != null)
                return false;
        } else if (!fileComment.equals(other.fileComment))
            return false;
        if (fileCommentLength_16bit != other.fileCommentLength_16bit)
            return false;
        if (fileName == null) {
            if (other.fileName != null)
                return false;
        } else if (!fileName.equals(other.fileName))
            return false;
        if (fileNameLength_16bit != other.fileNameLength_16bit)
            return false;
        if (generalPurposeBitFlag_16bit != other.generalPurposeBitFlag_16bit)
            return false;
        if (internalFileAttributes_16bit != other.internalFileAttributes_16bit)
            return false;
        if (lastModifiedFileDate_16bit != other.lastModifiedFileDate_16bit)
            return false;
        if (lastModifiedFileTime_16bit != other.lastModifiedFileTime_16bit)
            return false;
        if (uncompressedSize_32bit != other.uncompressedSize_32bit)
            return false;
        if (versionMadeBy_16bit != other.versionMadeBy_16bit)
            return false;
        if (versionNeededToExtract_16bit != other.versionNeededToExtract_16bit)
            return false;
        return true;
    }

    /**
     * Returns the "version number" of the software used to create the entry
     * in the archive, which corresponds to the features enumerated in the ZIP
     * specification.
     * @return the version (16 bit unsigned integer)
     */
    public int getVersionMadeBy_16bit() {
        return versionMadeBy_16bit;
    }

    /**
     * See {@link #getVersionMadeBy_16bit()}.
     * @param versionMadeBy_16bit
     */
    public void setVersionMadeBy_16bit(final int versionMadeBy_16bit) {
        this.versionMadeBy_16bit = versionMadeBy_16bit;
    }

    /**
     * Returns the minimum "version number" (corresponding to features
     * enumerated in the ZIP specification) required to extract this entry
     * from the archive.
     * @return the version (16 bit unsigned integer)
     */
    public int getVersionNeededToExtract_16bit() {
        return versionNeededToExtract_16bit;
    }

    /**
     * See {@link #getVersionNeededToExtract_16bit()}.
     * @param versionNeededToExtract_16bit the value to set
     */
    public void setVersionNeededToExtract_16bit(
        final int versionNeededToExtract_16bit) {
        this.versionNeededToExtract_16bit = versionNeededToExtract_16bit;
    }

    /**
     * Returns the "general purpose flags" of the entry, as defined in the
     * ZIP specification.
     * @return the value, as a 16-bit bitset
     */
    public int getGeneralPurposeBitFlag_16bit() {
        return generalPurposeBitFlag_16bit;
    }

    /**
     * See {@link #getGeneralPurposeBitFlag_16bit()}.
     * @param generalPurposeBitFlag_16bit the value to set
     */
    public void setGeneralPurposeBitFlag_16bit(
        final int generalPurposeBitFlag_16bit) {
        this.generalPurposeBitFlag_16bit = generalPurposeBitFlag_16bit;
    }

    /**
     * Returns the {@link CompressionMethod} identifying the method used to
     * compress this entry, as defined by the ZIP specification. For a more
     * convenient method, use {@link #getCompressionMethod()}, which returns
     * one of the enumerated constants from {@link CompressionMethod}.
     * @return the compression method
     * @see #getCompressionMethod()
     */
    public int getCompressionMethod_16bit() {
        return compressionMethod_16bit;
    }

    /**
     * See {@link #getCompressionMethod_16bit()}.
     * @param compressionMethod_16bit the value to set
     */
    public void setCompressionMethod_16bit(final int compressionMethod_16bit) {
        this.compressionMethod_16bit = compressionMethod_16bit;
    }

    /**
     * Returns the standard ("MS-DOS" based) last-modified time from this entry.
     * Note that the zip specification defines such a time for all entries, so
     * this will always be present even if additional (non-MS-DOS) last-modified
     * information exists in the entry. For more information on the format of
     * this value, see {@link MsDosTime}.
     * @return a 16-bit integer value representing the last-modified time that
     * was present on the original source when it was archived
     * @see #getLastModifiedFileDate()
     * @see #getLastModifiedFileTime()
     */
    public int getLastModifiedFileTime_16bit() {
        return lastModifiedFileTime_16bit;
    }

    /**
     * See {@link #getLastModifiedFileTime_16bit()}.
     * @param lastModifiedFileTime_16bit the value to set
     */
    public void setLastModifiedFileTime_16bit(
        final int lastModifiedFileTime_16bit) {
        this.lastModifiedFileTime_16bit = lastModifiedFileTime_16bit;
    }

    /**
     * Returns the standard ("MS-DOS" based) last-modified date from this entry.
     * Note that the zip specification defines such a date for all entries, so
     * this will always be present even if additional (non-MS-DOS) last-modified
     * information exists in the entry. For more information on the format of
     * this value, see {@link MsDosDate}.
     * @return a 16-bit integer value representing the last-modified date that
     * was present on the original source when it was archived
     * @see #getLastModifiedFileDate()
     * @see #getLastModifiedFileTime()
     */
    public int getLastModifiedFileDate_16bit() {
        return lastModifiedFileDate_16bit;
    }

    /**
     * See {@link #getLastModifiedFileDate_16bit()}.
     * @param lastModifiedFileDate_16bit the value to set
     */
    public void setLastModifiedFileDate_16bit(
        final int lastModifiedFileDate_16bit) {
        this.lastModifiedFileDate_16bit = lastModifiedFileDate_16bit;
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
     * Returns the length of the file comment string, in bytes (not characters).
     * @return the length, as a 16-bit unsigned integer
     */
    public int getFileCommentLength_16bit() {
        return fileCommentLength_16bit;
    }

    /**
     * Returns the comment for the file.
     * @return the comment
     */
    public String getFileComment() {
        return fileComment;
    }

    /**
     * Sets the comment for the file, also updating the comment length field.
     * @param fileComment the comment to set; if the comment is non-null and of
     * length zero, it is treated as if it were null.
     */
    public void setFileComment(final String fileComment) {
        if (fileComment == null || fileComment.isEmpty()) {
            this.fileComment = null;
            this.fileCommentLength_16bit = 0;
        } else {
            this.fileComment = fileComment;
            try {
                this.fileCommentLength_16bit =
                    fileComment.getBytes("UTF-8").length;
            } catch (UnsupportedEncodingException e) {
                // UTF-8 support required by Java specification.
                throw new RuntimeException("System doesn't support UTF8");
            }
        }
    }

    /**
     * Returns the length of the file name, in bytes (not characters).
     * @return the length, as a 16-bit unsigned integer
     */
    public int getFileNameLength_16bit() {
        return fileNameLength_16bit;
    }

    /**
     * Returns the name of the file.
     * @return the name
     */
    public String getFileName() {
        return fileName;
    }

    /**
     * Sets the name of the file, also updating the file name length field.
     * @param fileName the value to set; if the name is non-null and of length
     * zero, it is treated as if it were null.
     */
    public void setFileName(final String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            this.fileName = null;
            this.fileNameLength_16bit = 0;
        } else {
            this.fileName = fileName;
            try {
                this.fileNameLength_16bit = fileName.getBytes("UTF-8").length;
            } catch (UnsupportedEncodingException e) {
                // UTF-8 support required by Java specification.
                throw new RuntimeException("System doesn't support UTF8");
            }
        }

    }

    /**
     * Returns the length of the "extras" field, in bytes.
     * @return the length, as an unsigned 16-bit integer
     */
    public int getExtraFieldLength_16bit() {
        return extraFieldLength_16bit;
    }

    /**
     * Returns the "extras" field, as a raw byte array. This is the actual field
     * within this object; care should be taken not to modify the contents
     * inadvertently.
     * @return the "extras" field (possibly null)
     */
    public byte[] getExtraField() {
        return extraField;
    }

    /**
     * Sets the "extras" field, as a raw byte array. This is the actual field
     * within this object; care should be taken not to modify the contents
     * inadvertently. Also updates the "extras" length field.
     * @param extraField the "extras" field to set; if the array is non-null
     * and of length zero, it is treated as if it were null.
     */
    public void setExtraField(final byte[] extraField) {
        if (extraField == null || extraField.length == 0) {
            this.extraField = null;
            this.extraFieldLength_16bit = 0;
        } else {
            this.extraField = extraField;
            this.extraFieldLength_16bit = extraField.length;
        }
    }

    /**
     * Returns the "disk number" of the start of the {@link LocalFile}, as
     * defined by the ZIP specification. This is an antiquated term, more
     * accurately described today as the "file number" or "part number" of the
     * start of the corresponding {@link LocalFile} in a multi-file
     * "split archive".
     * This value is zero-based, so the first disk is disk number zero.
     * <p>
     * Note that this library does not currently support multi-file archives.
     * @return the value, as an unsigned 16-bit integer
     * @see #getRelativeOffsetOfLocalHeader_32bit()
     */
    public int getDiskNumberStart_16bit() {
        return diskNumberStart_16bit;
    }

    /**
     * See {@link #getDiskNumberStart_16bit()}.
     * @param diskNumberStart_16bit the value to set
     */
    public void setDiskNumberStart_16bit(final int diskNumberStart_16bit) {
        this.diskNumberStart_16bit = diskNumberStart_16bit;
    }

    /**
     * Returns the "internal file attributes" as a 16-bit bitset. This field
     * describes whether or not the related file is believed to be text, and
     * also determines whether or not the records within are preceded by length
     * headers.
     * @return the file attributes, as a 16-bit bitset
     */
    public int getInternalFileAttributes_16bit() {
        return internalFileAttributes_16bit;
    }

    /**
     * See {@link #getInternalFileAttributes_16bit()}.
     * @param internalFileAttributes_16bit the value to set
     */
    public void setInternalFileAttributes_16bit(
        final int internalFileAttributes_16bit) {
        this.internalFileAttributes_16bit = internalFileAttributes_16bit;
    }

    /**
     * Returns the "external file attributes" as a 32-bit bitset. This field is
     * dependent upon the OS that produced the entry in the archive, and may
     * contain arbitrary filesystem-specific data such as whether or not the
     * file this entry corresponds to is supposed to be executable.
     * @return the file attributes, as a 32-bit bitset
     */
    public long getExternalFileAttributes_32bit() {
        return externalFileAttributes_32bit;
    }

    /**
     * See {@link #getExternalFileAttributes_32bit()}.
     * @param externalFileAttributes_32bit the value to set
     */
    public void setExternalFileAttributes_32bit(
        final int externalFileAttributes_32bit) {
        this.externalFileAttributes_32bit = externalFileAttributes_32bit;
    }

    /**
     * Returns the offset (in bytes) relative to the start of the archive file
     * where the {@link LocalFile} entry begins. If this is a multi-part
     * (aka "split") archive, the offset is relative to the beginning of the
     * file or part whose number is returned by
     * {@link #getDiskNumberStart_16bit()}.
     * @return the offset, as an unsigned 32-bit integer
     * @see #getDiskNumberStart_16bit()
     */
    public long getRelativeOffsetOfLocalHeader_32bit() {
        return relativeOffsetOfLocalHeader_32bit;
    }

    /**
     * See {@link #getRelativeOffsetOfLocalHeader_32bit()}.
     * @param relativeOffsetOfLocalHeader_32bit the value to set
     */
    public void setRelativeOffsetOfLocalHeader_32bit(
        final long relativeOffsetOfLocalHeader_32bit) {
        this.relativeOffsetOfLocalHeader_32bit =
            relativeOffsetOfLocalHeader_32bit;
    }
}