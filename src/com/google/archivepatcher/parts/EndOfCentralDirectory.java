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

public class EndOfCentralDirectory implements Part {
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
        zipFileComment = IOUtils.readUTF8(in, zipFileCommentLength_16bit);
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
        IOUtils.writeUTF8(out, zipFileComment);
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

    public int getDiskNumber_16bit() {
        return diskNumber_16bit;
    }

    public void setDiskNumber_16bit(int diskNumber_16bit) {
        this.diskNumber_16bit = diskNumber_16bit;
    }

    public int getDiskNumberOfStartOfCentralDirectory_16bit() {
        return diskNumberOfStartOfCentralDirectory_16bit;
    }

    public void setDiskNumberOfStartOfCentralDirectory_16bit(
            int diskNumberOfStartOfCentralDirectory_16bit) {
        this.diskNumberOfStartOfCentralDirectory_16bit = diskNumberOfStartOfCentralDirectory_16bit;
    }

    public int getNumEntriesInCentralDirThisDisk_16bit() {
        return numEntriesInCentralDirThisDisk_16bit;
    }

    public void setNumEntriesInCentralDirThisDisk_16bit(int numEntriesInCentralDirThisDisk_16bit) {
        this.numEntriesInCentralDirThisDisk_16bit = numEntriesInCentralDirThisDisk_16bit;
    }

    public int getNumEntriesInCentralDir_16bit() {
        return numEntriesInCentralDir_16bit;
    }

    public void setNumEntriesInCentralDir_16bit(int numEntriesInCentralDir_16bit) {
        this.numEntriesInCentralDir_16bit = numEntriesInCentralDir_16bit;
    }

    public long getLengthOfCentralDirectory_32bit() {
        return lengthOfCentralDirectory_32bit;
    }

    public void setLengthOfCentralDirectory_32bit(long lengthOfCentralDirectory_32bit) {
        this.lengthOfCentralDirectory_32bit = lengthOfCentralDirectory_32bit;
    }

    public long getOffsetOfStartOfCentralDirectoryRelativeToDisk_32bit() {
        return offsetOfStartOfCentralDirectoryRelativeToDisk_32bit;
    }

    public void setOffsetOfStartOfCentralDirectoryRelativeToDisk_32bit(
            long offsetOfStartOfCentralDirectoryRelativeToDisk_32bit) {
        this.offsetOfStartOfCentralDirectoryRelativeToDisk_32bit = offsetOfStartOfCentralDirectoryRelativeToDisk_32bit;
    }

    public int getZipFileCommentLength_16bit() {
        return zipFileCommentLength_16bit;
    }

    public String getZipFileComment() {
        return zipFileComment;
    }

    public void setZipFileComment(String zipFileComment) {
        this.zipFileComment = zipFileComment;
        this.zipFileCommentLength_16bit = zipFileComment.length();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + diskNumberOfStartOfCentralDirectory_16bit;
        result = prime * result + diskNumber_16bit;
        result = prime * result
                + (int) (lengthOfCentralDirectory_32bit ^ (lengthOfCentralDirectory_32bit >>> 32));
        result = prime * result + numEntriesInCentralDirThisDisk_16bit;
        result = prime * result + numEntriesInCentralDir_16bit;
        result = prime * result + (int) (offsetOfStartOfCentralDirectoryRelativeToDisk_32bit
                ^ (offsetOfStartOfCentralDirectoryRelativeToDisk_32bit >>> 32));
        result = prime * result + ((zipFileComment == null) ? 0 : zipFileComment.hashCode());
        result = prime * result + zipFileCommentLength_16bit;
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