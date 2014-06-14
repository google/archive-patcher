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
import java.util.Arrays;

public class LocalFile implements Part {
    public final static int SIGNATURE = 0x04034b50;
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
    private String fileName;
    private byte[] extraField;

    public CompressionMethod getCompressionMethod() {
        return CompressionMethod.fromHeaderValue(compressionMethod_16bit);
    }
    public MsDosDate getLastModifiedFileDate() {
        return MsDosDate.from16BitPackedValue(lastModifiedFileDate_16bit);
    }
    public MsDosTime getLastModifiedFileTime() {
        return MsDosTime.from16BitPackedValue(lastModifiedFileTime_16bit);
    }

    @Override
    public void read(DataInput in) throws IOException {
        final int signature = (int) IOUtils.readUnsignedInt(in);
        if (signature != SIGNATURE) throw new IOException("Invalid signature: " + signature);
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
        fileName = IOUtils.readUTF8(in, fileNameLength_16bit);
        extraField = new byte[extraFieldLength_16bit];
        in.readFully(extraField);
    }

    @Override
    public void write(DataOutput out) throws IOException {
        IOUtils.writeUnsignedInt(out, SIGNATURE);
        IOUtils.writeUnsignedShort(out, versionNeededToExtract_16bit);
        IOUtils.writeUnsignedShort(out, generalPurposeBitFlag_16bit);
        IOUtils.writeUnsignedShort(out, compressionMethod_16bit);
        IOUtils.writeUnsignedShort(out, lastModifiedFileTime_16bit);
        IOUtils.writeUnsignedShort(out, lastModifiedFileDate_16bit);
        IOUtils.writeUnsignedInt(out, crc32_32bit);
        IOUtils.writeUnsignedInt(out, compressedSize_32bit);
        IOUtils.writeUnsignedInt(out, uncompressedSize_32bit);
        IOUtils.writeUnsignedShort(out, fileNameLength_16bit);
        IOUtils.writeUnsignedShort(out, extraFieldLength_16bit);
        IOUtils.writeUTF8(out, fileName);
        out.write(extraField);
    }

    @Override
    public int getStructureLength() {
        return 4+2+2+2+2+2+4+4+4+2+2+fileNameLength_16bit+extraFieldLength_16bit;
    }

    @Override
    public String toString() {
        return "LocalFileHeader ["
                + "versionNeededToExtract_16bit=" + versionNeededToExtract_16bit
                + ", generalPurposeBitFlag_16bit=" + generalPurposeBitFlag_16bit
                + ", compressionMethod=" + getCompressionMethod()
                + ", lastModifiedFileTime=" + getLastModifiedFileDate()
                + ", lastModifiedFileDate=" + getLastModifiedFileTime()
                + ", crc32_32bit=" + crc32_32bit
                + ", compressedSize_32bit=" + compressedSize_32bit
                + ", uncompressedSize_32bit=" + uncompressedSize_32bit
                + ", fileNameLength_16bit=" + fileNameLength_16bit
                + ", extraFieldLength_16bit=" + extraFieldLength_16bit
                + ", fileName=" + fileName
                + ", extraField=" + Arrays.toString(extraField)
                + "]";
    }

    public int getVersionNeededToExtract_16bit() {
        return versionNeededToExtract_16bit;
    }

    public void setVersionNeededToExtract_16bit(int versionNeededToExtract_16bit) {
        this.versionNeededToExtract_16bit = versionNeededToExtract_16bit;
    }

    public int getGeneralPurposeBitFlag_16bit() {
        return generalPurposeBitFlag_16bit;
    }

    public void setGeneralPurposeBitFlag_16bit(int generalPurposeBitFlag_16bit) {
        this.generalPurposeBitFlag_16bit = generalPurposeBitFlag_16bit;
    }

    public int getCompressionMethod_16bit() {
        return compressionMethod_16bit;
    }

    public void setCompressionMethod_16bit(int compressionMethod_16bit) {
        this.compressionMethod_16bit = compressionMethod_16bit;
    }

    public int getLastModifiedFileTime_16bit() {
        return lastModifiedFileTime_16bit;
    }

    public void setLastModifiedFileTime_16bit(int lastModifiedFileTime_16bit) {
        this.lastModifiedFileTime_16bit = lastModifiedFileTime_16bit;
    }

    public int getLastModifiedFileDate_16bit() {
        return lastModifiedFileDate_16bit;
    }

    public void setLastModifiedFileDate_16bit(int lastModifiedFileDate_16bit) {
        this.lastModifiedFileDate_16bit = lastModifiedFileDate_16bit;
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

    public int getFileNameLength_16bit() {
        return fileNameLength_16bit;
    }

    public int getExtraFieldLength_16bit() {
        return extraFieldLength_16bit;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
        this.fileNameLength_16bit = fileName.length();
    }

    public byte[] getExtraField() {
        return extraField;
    }

    public void setExtraField(byte[] extraField) {
        this.extraField = extraField;
        this.extraFieldLength_16bit = extraField.length;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (int) (compressedSize_32bit ^ (compressedSize_32bit >>> 32));
        result = prime * result + compressionMethod_16bit;
        result = prime * result + (int) (crc32_32bit ^ (crc32_32bit >>> 32));
        result = prime * result + Arrays.hashCode(extraField);
        result = prime * result + extraFieldLength_16bit;
        result = prime * result + ((fileName == null) ? 0 : fileName.hashCode());
        result = prime * result + fileNameLength_16bit;
        result = prime * result + generalPurposeBitFlag_16bit;
        result = prime * result + lastModifiedFileDate_16bit;
        result = prime * result + lastModifiedFileTime_16bit;
        result = prime * result + (int) (uncompressedSize_32bit ^ (uncompressedSize_32bit >>> 32));
        result = prime * result + versionNeededToExtract_16bit;
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
        LocalFile other = (LocalFile) obj;
        if (crc32_32bit != other.crc32_32bit)
            return false;
        if (compressedSize_32bit != other.compressedSize_32bit)
            return false;
        if (compressionMethod_16bit != other.compressionMethod_16bit)
            return false;
        if (!Arrays.equals(extraField, other.extraField))
            return false;
        if (extraFieldLength_16bit != other.extraFieldLength_16bit)
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
        if (lastModifiedFileDate_16bit != other.lastModifiedFileDate_16bit)
            return false;
        if (lastModifiedFileTime_16bit != other.lastModifiedFileTime_16bit)
            return false;
        if (uncompressedSize_32bit != other.uncompressedSize_32bit)
            return false;
        if (versionNeededToExtract_16bit != other.versionNeededToExtract_16bit)
            return false;
        return true;
    }
}