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

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Arrays;

public class FileData implements Part {
    private int length;
    private byte[] data = null;

    public FileData(int length) {
        this.length = length;
    }

    public FileData(byte[] data) {
        this.data = data;
        this.length = data.length;
    }

    @Override
    public void read(DataInput in) throws IOException {
        data = new byte[length];
        in.readFully(data);
    }

    @Override
    public void write(DataOutput out) throws IOException {
        if (data == null) throw new IllegalStateException("data not available!");
        out.write(data);
    }

    @Override
    public int getStructureLength() {
        return length;
    }

    public int getLength() {
        return length;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
        this.length = data.length;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Arrays.hashCode(data);
        result = prime * result + length;
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
        FileData other = (FileData) obj;
        if (!Arrays.equals(data, other.data))
            return false;
        if (length != other.length)
            return false;
        return true;
    }
}