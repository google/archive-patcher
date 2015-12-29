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
import java.util.Arrays;

/**
 * I/O utilities specifically designed for ZIP archive processing and
 * manipulation in Java. Unless otherwise specified, all methods here process
 * data in little-endian format because that is what the ZIP specification
 * requires. Java is a big-endian virtual machine by definition, which requires
 * manipulation of all multi-byte numeric values.
 */
public class IOUtils {
    /**
     * Reads a string as UTF-8.
     * @param input the input source
     * @param length the number of bytes to read
     * @return the string that was read, or null if the length is zero
     * @throws IOException if reading or conversion to UTF-8 fails.
     */
    public static String readUTF8(final DataInput input, final int length)
        throws IOException {
        if (length == 0) return null;
        byte[] buffer = new byte[length];
        input.readFully(buffer, 0, length);
        return new String(buffer, 0, length, "UTF8");
    }

    /**
     * Writes a UTF-8 string.
     * @param out the output destination
     * @param string the string to be written
     * @throws IOException if unable to convert the string or write the bytes
     */
    public static void writeUTF8(final DataOutput out, final String string)
        throws IOException {
        out.write(string.getBytes("UTF8"));
    }

    /**
     * Reads an unsigned 8-bit value and converts it to a 16-bit Java short
     * whose value is always in the range [0, 0xFF].
     * @param in the input source
     * @return the value as described
     * @throws IOException if unable to read the value
     */
    public static short readUnsignedByte(final DataInput in)
        throws IOException {
        return (short) (in.readByte() & 0x000000ff);
    }

    /**
     * Reads an unsigned 16-bit value and converts it to a 32-bit Java integer
     * whose value is always in the range [0, 0xFFFF].
     * @param in the input source
     * @return the value as described
     * @throws IOException if unable to read the value
     */
    public static int readUnsignedShort(final DataInput in) throws IOException {
        int value = readUnsignedByte(in);
        value |= readUnsignedByte(in) << 8;
        return value;
    }

    /**
     * Writes a 16-bit unsigned value to the specified destination.
     * @param out the output destination
     * @param value the value, which must be in the range [0, 0xFFFF]
     * @throws IOException if unable to write the value
     * @throws IllegalArgumentException if the value is outside the range
     */
    public static void writeUnsignedShort(final DataOutput out, final int value)
        throws IOException {
        if (value < 0 || value > 0xffff) {
            throw new IllegalArgumentException(
                "value must be in the range [0, 0xffff]: " + value);
        }
        writeRaw16Bit(out, value);
    }

    /**
     * Writes a raw 16-bit value to the specified destination, ignoring the
     * upper 16 bits of the specified value
     * @param out the output destination
     * @param value the value
     * @throws IOException if unable to write the value
     */
    public static void writeRaw16Bit(final DataOutput out, final int value)
        throws IOException {
        out.write(value);
        out.write(value >> 8);
    }

    /**
     * Reads an unsigned 32-bit value and converts it to a 64-bit Java long
     * whose value is always in the range [0, 0xFFFFFFFF].
     * @param in the input source
     * @return the value as described
     * @throws IOException if unable to read the value
     */
    public static long readUnsignedInt(final DataInput in) throws IOException {
        long value = readUnsignedByte(in);
        value |= readUnsignedByte(in) << 8;
        value |= readUnsignedByte(in) << 16;
        value |= readUnsignedByte(in) << 24;
        return value;
    }

    /**
     * Writes a 32-bit unsigned value to the specified destination.
     * @param out the output destination
     * @param value the value, which must be in the range [0, 0xFFFFFFFF]
     * @throws IOException if unable to write the value
     * @throws IllegalArgumentException if the value is outside the range
     */
    public static void writeUnsignedInt(final DataOutput out, final long value)
        throws IOException {
        if (value < 0L || value > 0xffffffffL) {
            throw new IllegalArgumentException(
                "value must be in the range [0, 0xffffffff]: " + value);
        }
        writeRaw32Bit(out, value);
    }

    /**
     * Writes a raw 32-bit value to the specified destination, ignoring the
     * upper 32 bits of the specified value.
     * @param out the output destination
     * @param value the value
     * @throws IOException if unable to write the value
     */
    public static void writeRaw32Bit(final DataOutput out, final long value)
        throws IOException {
        final int valueAsInt = (int) (value & 0xFFFFFFFF);
        out.write(valueAsInt);
        out.write(valueAsInt >> 8);
        out.write(valueAsInt >> 16);
        out.write(valueAsInt >> 24);
    }

    /**
     * Consume all available bytes from the given source and return them as a
     * byte array. This operation will block until the end of stream is reached
     * and may exhaust system memory; use with caution.
     * @param in the input source
     * @return all remaining data from the input source
     * @throws IOException if unable to complete the operation
     */
    public static byte[] readAll(InputStream in) throws IOException {
        return readAll(in, 0);
    }

    /**
     * Consume all available bytes from the given source and return them as a
     * byte array. This operation will block until the end of stream is reached
     * and may exhaust system memory; use with caution.
     * @param in the input source
     * @param extraBufferAtEnd number of additional bytes to add to the returned
     * buffer, filled with zeroes.
     * @return all remaining data from the input source
     * @throws IOException if unable to complete the operation
     */
    public static byte[] readAll(InputStream in, int extraBufferAtEnd) throws IOException {
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        final byte[] temp = new byte[4096];
        int numRead = 0;
        while ( (numRead = in.read(temp)) >= 0) {
            buffer.write(temp, 0, numRead);
        }
        if (extraBufferAtEnd > 0) {
            Arrays.fill(temp, (byte) 0);
            while(extraBufferAtEnd > 0) {
                int numToAdd = Math.min(extraBufferAtEnd, temp.length);
                buffer.write(temp, 0, numToAdd);
                extraBufferAtEnd -= numToAdd;
            }
        }
        return buffer.toByteArray();
    }
}