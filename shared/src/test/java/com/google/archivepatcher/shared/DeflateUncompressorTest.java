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

package com.google.archivepatcher.shared;

import static org.junit.Assert.assertTrue;

import com.google.archivepatcher.shared.DeflateUncompressor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.zip.DeflaterOutputStream;

import org.junit.Before;
import org.junit.Test;

/**
 * Tests for {@link DeflateUncompressor}.
 */
@SuppressWarnings("javadoc")
public class DeflateUncompressorTest {
  private final static int CONTENT_LENGTH = 50000;
  private final static byte[] CONTENT;
  private byte[] compressedContent;
  private DeflateUncompressor uncompressor;

  static {
    // Generate highly-compressible data
    CONTENT = new byte[CONTENT_LENGTH];
    for (int x = 0; x < CONTENT_LENGTH; x++) {
      CONTENT[x] = (byte) (x % 256);
    }
  }

  @Before
  public void setUp() throws IOException {
    ByteArrayOutputStream compressedContentBuffer = new ByteArrayOutputStream();
    DeflaterOutputStream deflateOut = new DeflaterOutputStream(compressedContentBuffer);
    deflateOut.write(CONTENT);
    deflateOut.finish();
    deflateOut.close();
    compressedContent = compressedContentBuffer.toByteArray();
    uncompressor = new DeflateUncompressor();
  }

  @Test
  public void testUncompress() throws Exception {
    ByteArrayOutputStream uncompressedOut = new ByteArrayOutputStream();
    uncompressor.uncompress(new ByteArrayInputStream(compressedContent), uncompressedOut);
    assertTrue(Arrays.equals(CONTENT, uncompressedOut.toByteArray()));
  }
}
