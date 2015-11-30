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
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;

/**
 * Represents an end-of-central-directory record in a ZIP-like archive.
 * This record is usually the first item encountered by an archive processor.
 * Most implementations search backwards from the end of the archive seeking
 * the 32-bit bit-pattern defined by {@link #SIGNATURE}; this is generally the
 * beginning of this record, which critically contains the offset where the
 * central directory begins.
 * <p>
 * The ZIP specification provides the ability for an archive to span multiple
 * "disks", which in modern times means splitting the archive into multiple
 * files. This library has no support for such so-called "split archives", so
 * the behavior is undefined in any case where such archives are involved.
 */
public class EndOfCentralDirectory implements Part {
    /**
     * Standard 32-bit signature for a "end-of-central-directory" record in a
     * ZIP-like archive.
     */
    public final static int SIGNATURE = 0x06054b50;

    private int diskNumber_16bit;
    private int diskNumberOfStartOfCentralDirectory_16bit;
    private int numEntriesInCentralDirThisDisk_16bit;
    private int numEntriesInCentralDir_16bit;
    private long lengthOfCentralDirectory_32bit;
    private long offsetOfStartOfCentralDirectoryRelativeToDisk_32bit;
    private int zipFileCommentLength_16bit;
    private String zipFileComment;

    /**
     * Find the "end of central directory" record by scanning backwards from
     * the end of the file looking for the signature of the record.
     * 
     * @param file the file to seek the header in
     * @return the offset in the file at which the first byte of the EOCD
     * signature is located, or -1 if there is no signature found
     * @throws IOException if there is a problem reading
     */
    public static int seek(RandomAccessFile file) throws IOException {
        final int length = (int) file.length();
        int offset = length; // start at the end of the file
        boolean found = false;
        int last4Bytes = 0;
        do {
            file.seek(--offset);
            int oneByte = file.read();
            last4Bytes <<= 8;
            last4Bytes |= oneByte;
            found = (last4Bytes == SIGNATURE);
        } while (!found);
        if (!found) return -1;
        return offset;
    }

    @Override
    public void read(DataInput in) throws IOException {
        final int signature = (int) IOUtils.readUnsignedInt(in);
        if (signature != SIGNATURE) throw new IOException("Invalid signature: " + signature);
        diskNumber_16bit = IOUtils.readUnsignedShort(in);
        diskNumberOfStartOfCentralDirectory_16bit = IOUtils.readUnsignedShort(in);
        numEntriesInCentralDirThisDisk_16bit = IOUtils.readUnsignedShort(in);
        numEntriesInCentralDir_16bit = IOUtils.readUnsignedShort(in);
        lengthOfCentralDirectory_32bit = IOUtils.readUnsignedInt(in);
        offsetOfStartOfCentralDirectoryRelativeToDisk_32bit = IOUtils.readUnsignedInt(in);
        zipFileCommentLength_16bit = IOUtils.readUnsignedShort(in);
        if (zipFileCommentLength_16bit > 0) {
            zipFileComment = IOUtils.readUTF8(in, zipFileCommentLength_16bit);
        }
    }
    
    @Override
    public void write(DataOutput out) throws IOException {
        IOUtils.writeUnsignedInt(out, SIGNATURE);
        IOUtils.writeUnsignedShort(out, diskNumber_16bit);
        IOUtils.writeUnsignedShort(out, diskNumberOfStartOfCentralDirectory_16bit);
        IOUtils.writeUnsignedShort(out, numEntriesInCentralDirThisDisk_16bit);
        IOUtils.writeUnsignedShort(out, numEntriesInCentralDir_16bit);
        IOUtils.writeUnsignedInt(out, lengthOfCentralDirectory_32bit);
        IOUtils.writeUnsignedInt(out, offsetOfStartOfCentralDirectoryRelativeToDisk_32bit);
        IOUtils.writeUnsignedShort(out, zipFileCommentLength_16bit);
        if (zipFileComment != null) {
            IOUtils.writeUTF8(out, zipFileComment);
        }
    }

    @Override
    public int getStructureLength() {
        return 4+2+2+2+2+4+4+2+zipFileCommentLength_16bit;
    }

    @Override
    public String toString() {
        return "EndOfCentralDirectory [diskNumber_16bit=" + diskNumber_16bit
                + ", diskNumberOfStartOfCentralDirectory_16bit="
                + diskNumberOfStartOfCentralDirectory_16bit
                + ", numEntriesInCentralDirThisDisk_16bit=" + numEntriesInCentralDirThisDisk_16bit
                + ", numEntriesInCentralDir_16bit=" + numEntriesInCentralDir_16bit
                + ", lengthOfCentralDirectory_32bit=" + lengthOfCentralDirectory_32bit
                + ", offsetOfStartOfCentralDirectoryRelativeToDisk_32bit="
                + offsetOfStartOfCentralDirectoryRelativeToDisk_32bit
                + ", zipFileCommentLength_16bit=" + zipFileCommentLength_16bit + ", zipFileComment="
                + zipFileComment + "]";
    }

    /**
     * Returns the "disk number" of the start of the data for this record, as
     * defined by the ZIP specification. This is an antiquated term, more
     * accurately described today as the "file number" or "part number" of the
     * start of the data for this record in a multi-file "split archive".
     * This value is zero-based, so the first disk is disk number zero.
     * <p>
     * Note that this library does not currently support multi-file archives.
     * @return the value, as an unsigned 16-bit integer
     * @see #getDiskNumberOfStartOfCentralDirectory_16bit()
     */
    public int getDiskNumber_16bit() {
        return diskNumber_16bit;
    }

    /**
     * See {@link #getDiskNumber_16bit()}.
     * @param diskNumber_16bit the value to set
     */
    public void setDiskNumber_16bit(final int diskNumber_16bit) {
        this.diskNumber_16bit = diskNumber_16bit;
    }

    /**
     * Returns the "disk number" of the start of the central directory, as
     * defined by the ZIP specification. This is an antiquated term, more
     * accurately described today as the "file number" or "part number" of the
     * start of the central directory in a multi-file "split archive".
     * This value is zero-based, so the first disk is disk number zero.
     * <p>
     * Note that this library does not currently support multi-file archives.
     * @return the value, as an unsigned 16-bit integer
     * @see #getDiskNumber_16bit()
     */
    public int getDiskNumberOfStartOfCentralDirectory_16bit() {
        return diskNumberOfStartOfCentralDirectory_16bit;
    }

    /**
     * See {@link #getDiskNumberOfStartOfCentralDirectory_16bit()}.
     * @param diskNumberOfStartOfCentralDirectory_16bit the value to set
     */
    public void setDiskNumberOfStartOfCentralDirectory_16bit(
            final int diskNumberOfStartOfCentralDirectory_16bit) {
        this.diskNumberOfStartOfCentralDirectory_16bit =
            diskNumberOfStartOfCentralDirectory_16bit;
    }

    /**
     * Returns the number of records contained in the central directory, within
     * this file only.
     * <p>
     * Note that this library does not currently support multi-file archives.
     * @return the value, as an unsigned 16-bit integer
     * @see #getNumEntriesInCentralDir_16bit()
     */
    public int getNumEntriesInCentralDirThisDisk_16bit() {
        return numEntriesInCentralDirThisDisk_16bit;
    }

    /**
     * See {@link #getNumEntriesInCentralDir_16bit()}.
     * <p>
     * Note that this library does not currently support multi-file archives.
     * @param numEntriesInCentralDirThisDisk_16bit the value to set
     */
    public void setNumEntriesInCentralDirThisDisk_16bit(
        final int numEntriesInCentralDirThisDisk_16bit) {
        this.numEntriesInCentralDirThisDisk_16bit =
            numEntriesInCentralDirThisDisk_16bit;
    }

    /**
     * Returns the total number of records contained in the central directory,
     * across any and all files.
     * <p>
     * Note that this library does not currently support multi-file archives.
     * @return the value, as an unsigned 16-bit integer
     * @see #getNumEntriesInCentralDirThisDisk_16bit()
     */
    public int getNumEntriesInCentralDir_16bit() {
        return numEntriesInCentralDir_16bit;
    }

    /**
     * See {@link #getNumEntriesInCentralDir_16bit()}.
     * @param numEntriesInCentralDir_16bit the value to set
     */
    public void setNumEntriesInCentralDir_16bit(
        final int numEntriesInCentralDir_16bit) {
        this.numEntriesInCentralDir_16bit = numEntriesInCentralDir_16bit;
    }

    /**
     * Returns the length, in bytes, of the central directory.
     * @return the value, as an unsigned 32-bit integer
     */
    public long getLengthOfCentralDirectory_32bit() {
        return lengthOfCentralDirectory_32bit;
    }

    /**
     * See {@link #getLengthOfCentralDirectory_32bit()}
     * @param lengthOfCentralDirectory_32bit the value to set
     */
    public void setLengthOfCentralDirectory_32bit(
        final long lengthOfCentralDirectory_32bit) {
        this.lengthOfCentralDirectory_32bit = lengthOfCentralDirectory_32bit;
    }

    /**
     * Returns the offset, in bytes, of the start of the central directory
     * relative to the beginning of this file. For example, a value of 50
     * means that the first byte of the central directory is located 50 bytes
     * into the file.
     * <p>
     * Note that this library does not currently support multi-file archives.
     * @return the value, as an unsigned 32-bit integer
     */
    public long getOffsetOfStartOfCentralDirectoryRelativeToDisk_32bit() {
        return offsetOfStartOfCentralDirectoryRelativeToDisk_32bit;
    }

    /**
     * See {@link #getOffsetOfStartOfCentralDirectoryRelativeToDisk_32bit()}.
     * @param offsetOfStartOfCentralDirectoryRelativeToDisk_32bit the value to
     * set
     */
    public void setOffsetOfStartOfCentralDirectoryRelativeToDisk_32bit(
            final long offsetOfStartOfCentralDirectoryRelativeToDisk_32bit) {
        this.offsetOfStartOfCentralDirectoryRelativeToDisk_32bit =
            offsetOfStartOfCentralDirectoryRelativeToDisk_32bit;
    }

    /**
     * Returns the length of the archive comment string, in bytes (not
     * characters).
     * @return the length, as a 16-bit unsigned integer
     */
    public int getZipFileCommentLength_16bit() {
        return zipFileCommentLength_16bit;
    }

    /**
     * Returns the comment for the archive.
     * @return the comment
     */
    public String getZipFileComment() {
        return zipFileComment;
    }

    /**
     * Sets the comment for the archive, also updating the comment length field.
     * @param zipFileComment the comment to set; if the comment is non-null and
     * of length zero, it is treated as if it were null.
     */
    public void setZipFileComment(final String zipFileComment) {
        if (zipFileComment == null || zipFileComment.length() == 0) {
            this.zipFileComment = null;
            this.zipFileCommentLength_16bit = 0;
        } else {
            this.zipFileComment = zipFileComment;
            try {
                this.zipFileCommentLength_16bit =
                    zipFileComment.getBytes("UTF-8").length;
            } catch (UnsupportedEncodingException e) {
                // UTF-8 support required by Java specification.
                throw new RuntimeException("System doesn't support UTF8");
            }
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + diskNumberOfStartOfCentralDirectory_16bit;
        result = prime * result + diskNumber_16bit;
        result = prime * result
                + (int) (lengthOfCentralDirectory_32bit ^
                    (lengthOfCentralDirectory_32bit >>> 32));
        result = prime * result + numEntriesInCentralDirThisDisk_16bit;
        result = prime * result + numEntriesInCentralDir_16bit;
        result = prime * result + (int) 
            (offsetOfStartOfCentralDirectoryRelativeToDisk_32bit
                ^ (offsetOfStartOfCentralDirectoryRelativeToDisk_32bit >>> 32));
        result = prime * result + ((zipFileComment == null) ?
            0 : zipFileComment.hashCode());
        result = prime * result + zipFileCommentLength_16bit;
        return result;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        EndOfCentralDirectory other = (EndOfCentralDirectory) obj;
        if (diskNumberOfStartOfCentralDirectory_16bit
                != other.diskNumberOfStartOfCentralDirectory_16bit)
            return false;
        if (diskNumber_16bit != other.diskNumber_16bit)
            return false;
        if (lengthOfCentralDirectory_32bit != other.lengthOfCentralDirectory_32bit)
            return false;
        if (numEntriesInCentralDirThisDisk_16bit != other.numEntriesInCentralDirThisDisk_16bit)
            return false;
        if (numEntriesInCentralDir_16bit != other.numEntriesInCentralDir_16bit)
            return false;
        if (offsetOfStartOfCentralDirectoryRelativeToDisk_32bit
                != other.offsetOfStartOfCentralDirectoryRelativeToDisk_32bit)
            return false;
        if (zipFileComment == null) {
            if (other.zipFileComment != null)
                return false;
        } else if (!zipFileComment.equals(other.zipFileComment))
            return false;
        if (zipFileCommentLength_16bit != other.zipFileCommentLength_16bit)
            return false;
        return true;
    }
}