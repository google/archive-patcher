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

package com.google.archivepatcher.bsdiff;

import java.io.IOException;
import java.io.InputStream;

/**
 * A Java implementation of the "bspatch" algorithm based on the BSD-2 licensed
 * source code available here: https://github.com/mendsley/bsdiff.
 * <p>
 * A pristine copy of the code is checked into this project under
 * third_party/bsdiff.
 * <p>
 * This class is completely standalone and depends only upon JRE functionality
 * that has been available since Java 1.0.
 */
public class BsPatch {

    /**
     * Read exactly the specified number of bytes into the specified buffer in
     * a blocking manner.
     * @param in the input stream to read from
     * @param destination where to write the bytes to
     * @param startAt the offset at which to start writing bytes in the
     * destination buffer
     * @param numBytes the number of bytes to read
     * @throws IOException if unable to complete the operation
     */
    private static void readExactly(final InputStream in,
        final byte[] destination, final int startAt, final int numBytes)
            throws IOException {
        int numRead = 0;
        while (numRead < numBytes) {
            int readNow = in.read(
                destination, startAt+numRead, numBytes - numRead);
            if (readNow == -1) {
                // EOF
                throw new IOException("truncated input stream");
            }
            numRead += readNow;
        }
    }

    /**
     * Convenience shortcut to get an unsigned integer from a byte.
     * @param b the byte
     * @return a value in the range [0,255]
     */
    private final static int u(byte b) {
        return b & 0x000000ff;
    }

    /**
     * Read an offset (64-bit signed integer).
     * The least significant bit is read from index [start], the most
     * significant bit is read from index [start+7].
     * @param source the buffer to read from
     * @param start the index at which to start reading
     * @return the offset
     */
    // Original name: offtin (derived presumably from the data type "off_t")
    private static long readOffset(final byte[] source, final int start) {
        final boolean isNegative = (source[start+7] & 0x80L) != 0;
        long result = 0;
        result |= u(source[start+0]) << 0;
        result |= u(source[start+1]) << 8;
        result |= u(source[start+2]) << 16;
        result |= u(source[start+3]) << 24;
        result |= u(source[start+4]) << 32;
        result |= u(source[start+5]) << 40;
        result |= u(source[start+6]) << 48;
        result |= (u(source[start+7]) & 0x7f) << 56;
        if (isNegative) result *= -1;
        return result;
    }

    /**
     * Applies a patch to the specified old data, generating the result into
     * the specified new data buffer.
     * @param oldData the data to apply the patch against
     * @param newData the buffer that will contain the "new" data when this
     * operation completes
     * @param patchInputStream stream from which to read the patch (bsdiff
     * output)
     * @throws IOException if unable to apply the patch
     */
    // Original name: bspatch
    public static void applyPatch(final byte[] oldData, final byte[] newData,
        final InputStream patchInputStream) throws IOException {
        final byte[] offsetBuffer = new byte[8];
        final long[] controlValues = new long[3];

        int oldPosition = 0;
        int newPosition = 0;
        while(newPosition < newData.length) {
            // Read control data
            for(int i=0; i<=2; i++) {
                readExactly(patchInputStream, offsetBuffer, 0, 8);
                controlValues[i]=readOffset(offsetBuffer, 0);
            }

            // Sanity-check
            if(newPosition + controlValues[0] > newData.length) {
                throw new IOException("Bad length: " + controlValues[0]);
            }

            // Read bsdiff chunk
            readExactly(patchInputStream, newData, newPosition, (int) controlValues[0]);

            // Add old data to diff string
            for(int i=0; i<controlValues[0]; i++) {
                if((oldPosition+i>=0) && (oldPosition+i<oldData.length)) {
                    newData[newPosition+i]+=oldData[oldPosition+i];
                }
            }

            // Adjust pointers
            newPosition += controlValues[0];
            oldPosition += controlValues[0];

            // Sanity-check
            if(newPosition  +controlValues[1] > newData.length) {
                throw new IOException("Bad length: " + controlValues[1]);
            }

            // Read extra string
            readExactly(patchInputStream, newData, newPosition, (int) controlValues[1]);

            // Adjust pointers
            newPosition += controlValues[1];
            oldPosition += controlValues[2];
        }
    }
}