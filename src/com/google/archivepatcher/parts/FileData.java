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

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Arrays;

/**
 * Represents the binary data for one {@link LocalFile}. The data contained in
 * this object is what is referred to throughout the ZIP specification as the
 * "compressed" form of the data, regardless of whether or not compression is
 * applied.
 * <p>
 * This part has no overhead bytes; it is simply a wrapper around a byte array.
 */
public class FileData implements Part {
    private int length;
    private byte[] data = null;

    /**
     * Constructs a new, empty part having the specified capacity in bytes,
     * suitable for reading.
     * @param length the number of bytes that are expected when calling
     * {@link #read(DataInput)}
     */
    public FileData(final int length) {
        this.length = length;
    }

    /**
     * Constructs a new part having the specified data, suitable for writing.
     * @param data the raw binary data exactly as it should be represented in
     * the archive, after any and all compression has taken place. The value is
     * used directly within this object; care should be taken not to modify the
     * contents inadvertently.
     */
    public FileData(final byte[] data) {
        setData(data);
    }

    @Implementation
    public void read(DataInput in) throws IOException {
        data = new byte[length];
        in.readFully(data);
    }

    @Implementation
    public void write(DataOutput out) throws IOException {
        if (data == null) {
            throw new IllegalStateException("data not set!");
        }
        out.write(data);
    }

    @Implementation
    public int getStructureLength() {
        return length;
    }

    /**
     * Returns the data contained within this part. This is the actual field
     * within this object; care should be taken not to modify the contents
     * inadvertently.
     * @return the data
     */
    public byte[] getData() {
        return data;
    }

    /**
     * Sets the data within this part.  The value is used directly within this
     * object; care should be taken not to modify the contents inadvertently.
     * @param data the value to set; if the array is non-null and of length
     * zero, it is treated as if it were null.
     */
    public void setData(final byte[] data) {
        if (data == null || data.length == 0) {
            this.data = null;
            length = 0;
        } else {
            this.data = data;
            length = data.length;
        }
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
        if (length != other.length)
            return false;
        if (!Arrays.equals(data, other.data))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "FileData [length=" + length
            + ", data=" + Arrays.toString(data) + "]";
    }

}