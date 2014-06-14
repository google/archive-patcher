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

package com.google.archivepatcher.util;

import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

public class IOUtils {
    private final static Charset UTF8 = Charset.forName("UTF-8");

    public static String readUTF8(DataInput input, int length) throws IOException {
        byte[] buffer = new byte[length];
        input.readFully(buffer, 0, length);
        return new String(buffer, 0, length, UTF8);
    }

    public static void writeUTF8(DataOutput out, String string) throws IOException {
        out.write(string.getBytes(UTF8));
    }

    public static short readUnsignedByte(DataInput in) throws IOException {
        return (short) (in.readByte() & 0x000000ff);
    }

    public static int readUnsignedShort(DataInput in) throws IOException {
        int value = readUnsignedByte(in);
        value |= readUnsignedByte(in) << 8;
        return value;
    }

    public static void writeUnsignedShort(DataOutput out, int value) throws IOException {
        out.write(value);
        out.write(value >> 8);
    }

    public static long readUnsignedInt(DataInput in) throws IOException {
        long value = readUnsignedByte(in);
        value |= readUnsignedByte(in) << 8;
        value |= readUnsignedByte(in) << 16;
        value |= readUnsignedByte(in) << 24;
        return value;
    }

    public static void writeUnsignedInt(DataOutput out, long value) throws IOException {
        value &= 0xffffffff;
        int valueAsInt = (int) value;
        out.write(valueAsInt);
        out.write(valueAsInt >> 8);
        out.write(valueAsInt >> 16);
        out.write(valueAsInt >> 24);
    }

    public static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] temp = new byte[4096];
        int numRead = 0;
        while ( (numRead = in.read(temp)) >= 0) {
            buffer.write(temp, 0, numRead);
        }
        return buffer.toByteArray();
    }
}