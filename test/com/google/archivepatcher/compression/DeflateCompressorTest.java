// Copyright 2015 Google Inc. All rights reserved.
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

package com.google.archivepatcher.compression;

import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.zip.InflaterInputStream;

import org.junit.Before;
import org.junit.Test;

/**
 * Tests for {@link DeflateCompressor}.
 */
@SuppressWarnings("javadoc")
public class DeflateCompressorTest {
    private final static int CONTENT_LENGTH = 50000;
    private final static byte[] CONTENT;
    private DeflateCompressor compressor;
    private ByteArrayInputStream rawContentIn;
    private ByteArrayOutputStream compressedContentOut;

    static {
        // Generate highly-compressible data
        CONTENT = new byte[CONTENT_LENGTH];
        for (int x=0; x<CONTENT_LENGTH; x++) {
            CONTENT[x] = (byte) (x % 256);
        }
    }

    @Before
    public void setUp() {
        compressor = new DeflateCompressor();
        rawContentIn = new ByteArrayInputStream(CONTENT);
        compressedContentOut = new ByteArrayOutputStream();
    }

    @Test
    public void testCompress() throws Exception {
        compressor.compress(rawContentIn, compressedContentOut);
        assertTrue(compressedContentOut.size() > 0);
        assertTrue(compressedContentOut.size() < CONTENT_LENGTH);
        // Uncompress with builtin inflater input stream independent of the
        // DeflateUncompressor class.
        InflaterInputStream inflaterIn = new InflaterInputStream(
            new ByteArrayInputStream(compressedContentOut.toByteArray()));
        byte[] buffer = new byte[32768];
        ByteArrayOutputStream uncompressedOut = new ByteArrayOutputStream();
        int numRead = 0;
        while ((numRead = inflaterIn.read(buffer)) >= 0) {
            uncompressedOut.write(buffer, 0, numRead);
        }
        assertTrue(Arrays.equals(CONTENT, uncompressedOut.toByteArray()));
    }

}
