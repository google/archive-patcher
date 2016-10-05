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

package com.google.archivepatcher.generator.bsdiff;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Utility functions to be shared between BsDiff and BsPatch.
 */
class BsUtil {
    /**
     * Mask to determine whether a long written by {@link #writeFormattedLong(long, OutputStream)}
     * is negative.
     */
    private static final long NEGATIVE_MASK = 1L << 63;

    /**
     * Writes a 64-bit signed integer to the specified {@link OutputStream}. The least significant
     * byte is written first and the most significant byte is written last.
     * @param value the value to write
     * @param outputStream the stream to write to
     */
    static void writeFormattedLong(final long value, OutputStream outputStream)
      throws IOException {
        long y = value;
        if (y < 0) {
            y = (-y) | NEGATIVE_MASK;
        }

        for (int i = 0; i < 8; ++i) {
            outputStream.write((byte) (y & 0xff));
            y >>>= 8;
        }
    }

    /**
     * Reads a 64-bit signed integer written by {@link #writeFormattedLong(long, OutputStream)} from
     * the specified {@link InputStream}.
     * @param inputStream the stream to read from
     */
    static long readFormattedLong(InputStream inputStream) throws IOException {
        long result = 0;
        for (int bitshift = 0; bitshift < 64; bitshift += 8) {
            result |= ((long) inputStream.read()) << bitshift;
        }

        if ((result - NEGATIVE_MASK) > 0) {
            result = (result & ~NEGATIVE_MASK) * -1;
        }
        return result;
    }

  /**
   * Provides functional equivalent to C/C++ lexicographical_compare. Warning: this calls {@link
   * RandomAccessObject#seek(long)}, so the internal state of the data objects will be modified.
   *
   * @param data1 first byte array
   * @param start1 index in the first array at which to start comparing
   * @param length1 length of first byte array
   * @param data2 second byte array
   * @param start2 index in the second array at which to start comparing
   * @param length2 length of second byte array
   * @return result of lexicographical compare: negative if the first difference has a lower value
   *     in the first array, positive if the first difference has a lower value in the second array.
   *     If both arrays compare equal until one of them ends, the shorter sequence is
   *     lexicographically less than the longer one (i.e., it returns len(first array) -
   *     len(second array)).
   */
  static int lexicographicalCompare(
      final RandomAccessObject data1,
      final int start1,
      final int length1,
      final RandomAccessObject data2,
      final int start2,
      final int length2)
      throws IOException {
    int bytesLeft = Math.min(length1, length2);

    data1.seek(start1);
    data2.seek(start2);
    while (bytesLeft-- > 0) {
      final int i1 = data1.readUnsignedByte();
      final int i2 = data2.readUnsignedByte();

      if (i1 != i2) {
        return i1 - i2;
      }
    }

    return length1 - length2;
  }
}
