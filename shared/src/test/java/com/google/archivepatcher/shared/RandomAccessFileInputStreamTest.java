// Copyright 2016 Google LLC. All rights reserved.
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

import static com.google.archivepatcher.shared.TestUtils.assertThrows;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link RandomAccessFileInputStream}. */
@RunWith(JUnit4.class)
@SuppressWarnings("javadoc")
public class RandomAccessFileInputStreamTest {
  /**
   * The object under test.
   */
  private RandomAccessFileInputStream stream = null;

  /**
   * Test data written to the file.
   */
  private byte[] testData = null;

  /**
   * The temp file.
   */
  private File tempFile = null;

  @Before
  public void setup() throws IOException {
    testData = new byte[128];
    for (int x = 0; x < 128; x++) {
      testData[x] = (byte) x;
    }
    tempFile = File.createTempFile("ra-fist", "tmp");
    tempFile.deleteOnExit();
    try {
      FileOutputStream out = new FileOutputStream(tempFile);
      out.write(testData);
      out.flush();
      out.close();
    } catch (IOException e) {
      try {
        tempFile.delete();
      } catch (Exception ignoreD) {
        // Nothing
      }
      throw new RuntimeException(e);
    }
    stream = new RandomAccessFileInputStream(tempFile);
  }

  @After
  public void tearDown() {
    try {
      stream.close();
    } catch (Exception ignored) {
      // Nothing to do
    }
    try {
      tempFile.delete();
    } catch (Exception ignored) {
      // Nothing to do
    }
  }

  @Test
  public void testRead_OneByte() throws IOException {
    for (int x = 0; x < testData.length; x++) {
      assertThat(stream.read()).isEqualTo(x);
    }
    assertThat(stream.read()).isEqualTo(-1);
  }

  @Test
  public void testRead_WithBuffer() throws IOException {
    int bytesLeft = testData.length;
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buffer = new byte[10];
    while (bytesLeft > 0) {
      int numRead = stream.read(buffer);
      if (numRead > 0) {
        bytesLeft -= numRead;
        out.write(buffer, 0, numRead);
      }
    }
    assertThat(stream.read(buffer, 0, 1)).isEqualTo(-1);
    assertThat(out.toByteArray()).isEqualTo(testData);
  }

  @Test
  public void testRead_WithBuffer_NegativeLength() throws IOException {
    assertThat(stream.read(new byte[] {}, 0, -1)).isEqualTo(0);
  }

  @Test
  public void testRead_WithPartialBuffer() throws IOException {
    int bytesLeft = testData.length;
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buffer = new byte[10];
    while (bytesLeft > 0) {
      int numRead = stream.read(buffer, 0, 2); // At most 2 bytes of the buffer can be used
      if (numRead > 0) {
        bytesLeft -= numRead;
        out.write(buffer, 0, numRead);
      }
    }
    assertThat(stream.read()).isEqualTo(-1);
    assertThat(out.toByteArray()).isEqualTo(testData);
  }

  @Test
  public void testMarkSupported() {
    assertThat(stream.markSupported()).isTrue();
  }

  @Test
  public void testMarkAndReset_WithOffsetFile() throws IOException {
    // Reset the stream, this time one byte in to exercise non-zero offset values
    stream.close();
    stream = new RandomAccessFileInputStream(tempFile, 1, testData.length - 2);
    // Set a mark after the first byte, which should be 1. Read a second byte, which should be 2.
    assertThat(stream.read()).isEqualTo(1);
    stream.mark(1337 /* any value here, it is ignored */);
    assertThat(stream.read()).isEqualTo(2);
    // Reset the stream, it should be back to 1 now.
    stream.reset();
    assertThat(stream.read()).isEqualTo(2);
  }

  @Test
  public void testSkip() throws IOException {
    // Skip values <= 0 should always produce 0 and not actually skip anything.
    assertThat(stream.skip(-1)).isEqualTo(0);
    assertThat(stream.skip(0)).isEqualTo(0);
    // Skip the first 5 bytes and read the 6th, which should have the value 5.
    assertThat(stream.skip(5)).isEqualTo(5);
    assertThat(stream.read()).isEqualTo(5);
    // 6 bytes have been read, so the max skip is testDataLength - 6. Ensure this is true.
    assertThat(stream.skip(testData.length)).isEqualTo(testData.length - 5 - 1);
    // At the end of the file, skip should always return 0.
    assertThat(stream.skip(17)).isEqualTo(0);
  }

  @Test
  public void testAvailable() throws IOException {
    // Available always knows the answer precisely unless the file length exceeds Integer.MAX_VALUE
    assertThat(stream.available()).isEqualTo(testData.length);
    stream.read(new byte[17]);
    assertThat(stream.available()).isEqualTo(testData.length - 17);
    stream.read(new byte[testData.length]);
    assertThat(stream.available()).isEqualTo(0);
    stream.read();
    assertThat(stream.available()).isEqualTo(0);
  }

  @Test
  public void testSetRange() throws IOException {
    // Mess with the stream range multiple times
    stream.setRange(1, 3);
    assertThat(stream.read()).isEqualTo(1);
    assertThat(stream.read()).isEqualTo(2);
    assertThat(stream.read()).isEqualTo(3);
    assertThat(stream.read()).isEqualTo(-1);
    stream.setRange(99, 2);
    assertThat(stream.read()).isEqualTo(99);
    assertThat(stream.read()).isEqualTo(100);
    assertThat(stream.read()).isEqualTo(-1);
  }

  @Test
  public void testSetRange_TooLong() throws IOException {
    assertThrows(IllegalArgumentException.class, () -> stream.setRange(0, testData.length + 1));
  }

  @Test
  public void testSetRange_NegativeOffset() throws IOException {
    assertThrows(IllegalArgumentException.class, () -> stream.setRange(-1, testData.length));
  }

  @Test
  public void testSetRange_NegativeLength() throws IOException {
    assertThrows(IllegalArgumentException.class, () -> stream.setRange(0, -1));
  }

  @Test
  public void testSetRange_LongOverflow() throws IOException {
    assertThrows(
        IllegalArgumentException.class, () -> stream.setRange(Long.MAX_VALUE, 1)); // Oh dear.
  }

  @Test
  public void testReset_NoMarkSet() throws IOException {
    assertThrows(IOException.class, () -> stream.reset());
  }

  @Test
  public void testClose() throws IOException {
    stream.close();
    try {
      stream.read();
      assertWithMessage("read after close").fail();
    } catch (IOException expected) {
      // Good.
    }
  }

  @Test
  public void testLength() {
    assertThat(stream.length()).isEqualTo(testData.length);
  }

  @Test
  public void testConstructorWithSpecificLength() throws IOException {
    stream = new RandomAccessFileInputStream(tempFile, 5, 2);
    assertThat(stream.read()).isEqualTo(5);
    assertThat(stream.read()).isEqualTo(6);
    assertThat(stream.read()).isEqualTo(-1);
  }

  @Test
  public void testGetPosition() throws IOException {
    stream = new RandomAccessFileInputStream(tempFile, 5, 2);
    assertThat(stream.getPosition()).isEqualTo(5);
    stream.read();
    assertThat(stream.getPosition()).isEqualTo(6);
    stream.setRange(0, 1);
    assertThat(stream.getPosition()).isEqualTo(0);
  }
}
