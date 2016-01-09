// Copyright 2016 Google Inc. All rights reserved.
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

package com.google.archivepatcher.tools.transformer;

import java.io.DataOutput;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UTFDataFormatException;

import com.google.archivepatcher.compat.Implementation;

/**
 * A wrapper around a {@link DataOutput} that counts the number of bytes
 * written.
 */
public class CountingDataOutput extends OutputStream implements DataOutput {
    private final DataOutput wrapped;
    private long count = 0;

    /**
     * Construct a counting output that counts the number of bytes written.
     * @param output the output to wrap
     */
    public CountingDataOutput(DataOutput output) {
        super();
        wrapped = output;
    }

    /**
     * Returns the number of bytes that have been written so far.
     * @return as described
     */
    public long getBytesWrittenCount() {
        return count;
    }

    /**
     * See {@link DataOutput}.
     * @param b
     * @param off
     * @param len
     * @throws IOException
     */
    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        wrapped.write(b, off, len);
        count += len;
    }

    /**
     * See {@link DataOutput}.
     * @param b
     * @throws IOException
     */
    @Override
    public void write(byte[] b) throws IOException {
        wrapped.write(b);
        count += b.length;
    }

    /**
     * See {@link DataOutput}.
     * @param b
     * @throws IOException
     */
    @Override
    public void write(int b) throws IOException {
        wrapped.write(b);
        count += 1;
    }

    /**
     * See {@link DataOutput}.
     * @param v
     * @throws IOException
     */
    @Implementation
    public void writeBoolean(boolean v) throws IOException {
        wrapped.writeBoolean(v);
        count += 1;
    }

    /**
     * See {@link DataOutput}.
     * @param v
     * @throws IOException
     */
    @Implementation
    public void writeByte(int v) throws IOException {
        wrapped.writeByte(v);
        count += 1;
    }

    /**
     * See {@link DataOutput}.
     * @param s
     * @throws IOException
     */
    @Implementation
    public void writeBytes(String s) throws IOException {
        wrapped.writeBytes(s);
        // Per DataOutput contract, exactly 1 byte is written per char.
        count += s.length();
    }

    /**
     * See {@link DataOutput}.
     * @param v
     * @throws IOException
     */
    @Implementation
    public void writeChar(int v) throws IOException {
        wrapped.writeChar(v);
        // Per DataOutput contract, exactly 2 bytes are written.
        count += 2;
    }

    /**
     * See {@link DataOutput}.
     * @param s
     * @throws IOException
     */
    @Implementation
    public void writeChars(String s) throws IOException {
        // Per DataOutput contract, exactly 2 bytes are written per char.
        wrapped.writeChars(s);
        count += 2 * s.length();
    }

    /**
     * See {@link DataOutput}.
     * @param v
     * @throws IOException
     */
    @Implementation
    public void writeDouble(double v) throws IOException {
        wrapped.writeDouble(v);
        count += 8;
    }

    /**
     * See {@link DataOutput}.
     * @param v
     * @throws IOException
     */
    @Implementation
    public void writeFloat(float v) throws IOException {
        wrapped.writeFloat(v);
        count += 4;
    }

    /**
     * See {@link DataOutput}.
     * @param v
     * @throws IOException
     */
    @Implementation
    public void writeInt(int v) throws IOException {
        wrapped.writeInt(v);
        count += 4;
    }

    /**
     * See {@link DataOutput}.
     * @param v
     * @throws IOException
     */
    @Implementation
    public void writeLong(long v) throws IOException {
        wrapped.writeLong(v);
        count += 8;
    }

    /**
     * See {@link DataOutput}.
     * @param v
     * @throws IOException
     */
    @Implementation
    public void writeShort(int v) throws IOException {
        wrapped.writeShort(v);
        count += 2;
    }

    /**
     * See {@link DataOutput}.
     * @param s
     * @throws IOException
     */
    @Implementation
    public void writeUTF(String s) throws IOException {
        // Trickier. To avoid reconversion, replicate the functionality
        // described by the contract in DataOutput here.
        byte[] bytes = s.getBytes("UTF-8");
        if (bytes.length > 65535) {
            throw new UTFDataFormatException("length too long");
        }
        writeShort(bytes.length);
        count += 2;
        write(bytes);
        count += bytes.length;
    }

    @Override
    public void close() throws IOException {
        // Nothing
    }

    @Override
    public void flush() throws IOException {
        // Nothing
    }
}