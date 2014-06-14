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

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;

public class EncryptionHeader {
    public final static int TAG = 0x0017;
    private int tag_16bit;
    private int TSize_16bit;
    private int Format_16bit;
    private int AlgID_16bit;
    private int Bitlen_16bit;
    private int Flags_16bit;
    private byte[] CertData;

    public void read(RandomAccessFile file) throws IOException {
        tag_16bit = IOUtils.readUnsignedShort(file);
        TSize_16bit = IOUtils.readUnsignedShort(file);
        Format_16bit = IOUtils.readUnsignedShort(file);
        AlgID_16bit = IOUtils.readUnsignedShort(file);
        Bitlen_16bit = IOUtils.readUnsignedShort(file);
        Flags_16bit = IOUtils.readUnsignedShort(file);
        CertData = new byte[TSize_16bit - 8];
        file.read(CertData, 0, CertData.length);
    }

    @Override
    public String toString() {
        return "EncryptionHeader [tag_16bit=" + tag_16bit + ", TSize_16bit=" + TSize_16bit
                + ", Format_16bit=" + Format_16bit + ", AlgID_16bit=" + AlgID_16bit
                + ", Bitlen_16bit=" + Bitlen_16bit + ", Flags_16bit=" + Flags_16bit + ", CertData="
                + Arrays.toString(CertData) + "]";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + AlgID_16bit;
        result = prime * result + Bitlen_16bit;
        result = prime * result + Arrays.hashCode(CertData);
        result = prime * result + Flags_16bit;
        result = prime * result + Format_16bit;
        result = prime * result + TSize_16bit;
        result = prime * result + tag_16bit;
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
        EncryptionHeader other = (EncryptionHeader) obj;
        if (AlgID_16bit != other.AlgID_16bit)
            return false;
        if (Bitlen_16bit != other.Bitlen_16bit)
            return false;
        if (!Arrays.equals(CertData, other.CertData))
            return false;
        if (Flags_16bit != other.Flags_16bit)
            return false;
        if (Format_16bit != other.Format_16bit)
            return false;
        if (TSize_16bit != other.TSize_16bit)
            return false;
        if (tag_16bit != other.tag_16bit)
            return false;
        return true;
    }
}
