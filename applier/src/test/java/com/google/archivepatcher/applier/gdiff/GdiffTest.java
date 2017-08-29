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

package com.google.archivepatcher.applier.gdiff;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Random;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * A fairly comprehensive set of tests for sunny and rainy day conditions for GDiff operations.
 * The intention is that a bad patch will *always* throw an IOException, either via explicit test
 * or by known safety checks (e.g. reading from a nonexistent part of the input file.)
 */
@RunWith(JUnit4.class)
public class GdiffTest {
  /**
   * Very lightweight smoke test using example in http://www.w3.org/TR/NOTE-gdiff-19970901
   */
  @Test
  public void testExample() throws IOException {
    byte[] oldBytes = new byte[] {
        (byte) 'A', (byte) 'B', (byte) 'C', (byte) 'D', (byte) 'E', (byte) 'F', (byte) 'G' };
    byte[] newBytes = new byte[] {
        (byte) 'A', (byte) 'B', (byte) 'X', (byte) 'Y', (byte) 'C', (byte) 'D',
        (byte) 'B', (byte) 'C', (byte) 'D', (byte) 'E' };
    byte[] patch = new byte[] {
        (byte) 0xd1, (byte) 0xff, (byte) 0xd1, (byte) 0xff, (byte) 4,  // magic+version
        (byte) 249, 0, 0, 2,                                           // COPY_USHORT_UBYTE 0, 2
        (byte) 2, (byte) 'X', (byte) 'Y',                              // DATA_2
        (byte) 249, 0, 2, 2,                                           // COPY_USHORT_UBYTE 2, 2
        (byte) 249, 0, 1, 4,                                           // COPY_USHORT_UBYTE 1, 4
        0 };                                                           // EOF

    // Create "input file".
    File inputFile = File.createTempFile("testExample", null);
    FileOutputStream writeInputFile = new FileOutputStream(inputFile);
    writeInputFile.write(oldBytes);
    writeInputFile.close();
    RandomAccessFile readInputFile = new RandomAccessFile(inputFile, "r");

    // Create "patch" file - this is just a stream
    ByteArrayInputStream patchStream = new ByteArrayInputStream(patch);

    // Create "output" file - this is just a stream
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream(newBytes.length);

    long outputLength = Gdiff.patch(readInputFile, patchStream, outputStream, newBytes.length);
    Assert.assertEquals(newBytes.length, outputLength);
    Assert.assertArrayEquals(newBytes, outputStream.toByteArray());
  }

  /**
   * A test to artificially (and minimally) exercise each opcode except for the full range of
   * data opcodes.
   *
   *          01234567890123456
   * Input:   ABCDEFGHIJ
   * Output:  XYZABBCCDDEEFFGGH
   */
  @Test
  public void testOpcodes() throws IOException {
    byte[] oldBytes = new byte[] {
        (byte) 'A', (byte) 'B', (byte) 'C', (byte) 'D', (byte) 'E', (byte) 'F', (byte) 'G',
        (byte) 'H', (byte) 'I', (byte) 'J' };
    byte[] newBytes = new byte[] {
        (byte) 'X', (byte) 'Y', (byte) 'Z',
        (byte) 'A', (byte) 'B', (byte) 'B', (byte) 'C', (byte) 'C', (byte) 'D', (byte) 'D',
        (byte) 'E', (byte) 'E', (byte) 'F', (byte) 'F', (byte) 'G', (byte) 'G', (byte) 'H' };
    byte[] patch = new byte[] {
        (byte) 0xd1, (byte) 0xff, (byte) 0xd1, (byte) 0xff, (byte) 4,    // magic+version
        (byte) 1, (byte) 'X',                                            // DATA_MIN (DATA_1)
        (byte) 247, 0, 1, (byte) 'Y',                                    // DATA_USHORT
        (byte) 248, 0, 0, 0, 1, (byte) 'Z',                              // DATA_INT
        (byte) 249, 0, 0, 2,                                             // COPY_USHORT_UBYTE
        (byte) 250, 0, 1, 0, 2,                                          // COPY_USHORT_USHORT
        (byte) 251, 0, 2, 0, 0, 0, 2,                                    // COPY_USHORT_INT
        (byte) 252, 0, 0, 0, 3, 2,                                       // COPY_INT_UBYTE
        (byte) 253, 0, 0, 0, 4, 0, 2,                                    // COPY_INT_USHORT
        (byte) 254, 0, 0, 0, 5, 0, 0, 0, 2,                              // COPY_INT_INT
        (byte) 255, 0, 0, 0, 0, 0, 0, 0, 6, 0, 0, 0, 2,                  // COPY_LONG_INT
        0 };                                                             // EOF

    // Create "input file".
    File inputFile = File.createTempFile("testExample", null);
    FileOutputStream writeInputFile = new FileOutputStream(inputFile);
    writeInputFile.write(oldBytes);
    writeInputFile.close();
    RandomAccessFile readInputFile = new RandomAccessFile(inputFile, "r");

    // Create "patch" file - this is just a stream
    ByteArrayInputStream patchStream = new ByteArrayInputStream(patch);

    // Create "output" file - this is just a stream
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream(newBytes.length);

    long outputLength = Gdiff.patch(readInputFile, patchStream, outputStream, newBytes.length);
    Assert.assertEquals(newBytes.length, outputLength);
    Assert.assertArrayEquals(newBytes, outputStream.toByteArray());
  }

  /**
   * A test to exercise all of the data inline opcodes (commands 1..246)
   */
  @Test
  public void testInlineDataCommands() throws IOException {
    // We never read "input" so create an single, empty one.
    File inputFile = File.createTempFile("testExample", null);
    FileOutputStream writeInputFile = new FileOutputStream(inputFile);
    writeInputFile.close();
    RandomAccessFile readInputFile = new RandomAccessFile(inputFile, "r");

    // First 5 bytes for copying into each patch
    byte[] magicAndVersion = new byte[] {
        (byte) 0xd1, (byte) 0xff, (byte) 0xd1, (byte) 0xff, (byte) 4 };

    // Use a pseudo random source to fill each data stream differently
    Random random = new Random();

    for (int spanLength = 1; spanLength <= 246; spanLength++) {
      // Generate data array (save for later to compare against output)
      byte[] data = new byte[spanLength];
      random.setSeed(spanLength);
      random.nextBytes(data);

      // The patch will consist of the following data:
      //  magic+version
      //  command "n"
      //  "n" bytes
      //  EOF
      int patchLength = 5 + 1 + spanLength + 1;
      byte[] patch = new byte[patchLength];
      System.arraycopy(magicAndVersion, 0, patch, 0, magicAndVersion.length);
      patch[5] = (byte) spanLength;
      System.arraycopy(data, 0, patch, 6, spanLength);
      patch[6 + spanLength] = 0;  // EOF

      // Create "patch" file - this is just a stream
      ByteArrayInputStream patchStream = new ByteArrayInputStream(patch);

      // Create "output" file - this is just a stream
      ByteArrayOutputStream outputStream = new ByteArrayOutputStream(data.length);

      // Run the patch and check the output file
      long outputLength = Gdiff.patch(readInputFile, patchStream, outputStream, data.length);
      Assert.assertEquals(spanLength, outputLength);
      Assert.assertArrayEquals(data, outputStream.toByteArray());
    }
  }

  /**
   * Tests for patch underrun (the patch terminates earlier than expected in each case).
   *
   * Cases:   1. Short magic bytes
   *          2. No version
   *          3. After an opcode (note - opcode arg underruns covered by testReaders_underrun())
   *          4. During inline data
   *          5. Missing EOF
   */
  @Test
  public void testPatchUnderrun() throws IOException {
    byte[] oldBytes = new byte[] {
        (byte) 'A', (byte) 'B', (byte) 'C', (byte) 'D', (byte) 'E', (byte) 'F', (byte) 'G' };
    byte[] patch = new byte[] {
        (byte) 0xd1, (byte) 0xff, (byte) 0xd1, (byte) 0xff, (byte) 4,        // magic+version
        (byte) 249, 0, 0, 2,                // COPY_USHORT_UBYTE 0, 2
        (byte) 2, (byte) 'X', (byte) 'Y',   // DATA_2
        (byte) 249, 0, 2, 2,                // COPY_USHORT_UBYTE 2, 2
        (byte) 249, 0, 1, 4,                // COPY_USHORT_UBYTE 1, 4
        0 };                                // EOF

    checkExpectedIOException(oldBytes, -1, patch, 3, -1);       // Short magic bytes
    checkExpectedIOException(oldBytes, -1, patch, 4, -1);       // No version
    checkExpectedIOException(oldBytes, -1, patch, 6, -1);       // After an opcode
    checkExpectedIOException(oldBytes, -1, patch, 11, -1);      // During inline data
    checkExpectedIOException(oldBytes, -1, patch, 20, -1);      // Missing EOF
  }

  /**
   * Tests for input underrun (the input terminates earlier than expected in each case).
   * Reuses the "all opcodes" patch for maximum coverage.
   */
  @Test
  public void testInputUnderrun() throws IOException {
    byte[] oldBytes = new byte[] {
        (byte) 'A', (byte) 'B', (byte) 'C', (byte) 'D', (byte) 'E', (byte) 'F', (byte) 'G',
        (byte) 'H' };
    byte[] patch = new byte[] {
        (byte) 0xd1, (byte) 0xff, (byte) 0xd1, (byte) 0xff, (byte) 4,    // magic+version
        (byte) 1, (byte) 'X',                                            // DATA_MIN (DATA_1)
        (byte) 247, 0, 1, (byte) 'Y',                                    // DATA_USHORT
        (byte) 248, 0, 0, 0, 1, (byte) 'Z',                              // DATA_INT
        (byte) 249, 0, 0, 2,                                             // COPY_USHORT_UBYTE
        (byte) 250, 0, 1, 0, 2,                                          // COPY_USHORT_USHORT
        (byte) 251, 0, 2, 0, 0, 0, 2,                                    // COPY_USHORT_INT
        (byte) 252, 0, 0, 0, 3, 2,                                       // COPY_INT_UBYTE
        (byte) 253, 0, 0, 0, 4, 0, 2,                                    // COPY_INT_USHORT
        (byte) 254, 0, 0, 0, 5, 0, 0, 0, 2,                              // COPY_INT_INT
        (byte) 255, 0, 0, 0, 0, 0, 0, 0, 6, 0, 0, 0, 2,                  // COPY_LONG_INT
        0 };                                                             // EOF

    // The oldBytes array above is sufficient to satisfy the patch, but this loop terminates
    // one byte short of using the entire input array.
    for (int inputLimit = 0; inputLimit < oldBytes.length; inputLimit++) {
      checkExpectedIOException(oldBytes, inputLimit, patch, -1, -1);
    }
  }

  /**
   * Tests for overrun limiter in output file.
   * Reuses the "all opcodes" patch for maximum coverage.
   */
  @Test
  public void testOutputOverrun() throws IOException {
    byte[] oldBytes = new byte[] {
        (byte) 'A', (byte) 'B', (byte) 'C', (byte) 'D', (byte) 'E', (byte) 'F', (byte) 'G',
        (byte) 'H', (byte) 'I', (byte) 'J' };
    byte[] newBytes = new byte[] {
        (byte) 'X', (byte) 'Y', (byte) 'Z',
        (byte) 'A', (byte) 'B', (byte) 'B', (byte) 'C', (byte) 'C', (byte) 'D', (byte) 'D',
        (byte) 'E', (byte) 'E', (byte) 'F', (byte) 'F', (byte) 'G', (byte) 'G', (byte) 'H' };
    byte[] patch = new byte[] {
        (byte) 0xd1, (byte) 0xff, (byte) 0xd1, (byte) 0xff, (byte) 4,    // magic+version
        (byte) 1, (byte) 'X',                                            // DATA_MIN (DATA_1)
        (byte) 247, 0, 1, (byte) 'Y',                                    // DATA_USHORT
        (byte) 248, 0, 0, 0, 1, (byte) 'Z',                              // DATA_INT
        (byte) 249, 0, 0, 2,                                             // COPY_USHORT_UBYTE
        (byte) 250, 0, 1, 0, 2,                                          // COPY_USHORT_USHORT
        (byte) 251, 0, 2, 0, 0, 0, 2,                                    // COPY_USHORT_INT
        (byte) 252, 0, 0, 0, 3, 2,                                       // COPY_INT_UBYTE
        (byte) 253, 0, 0, 0, 4, 0, 2,                                    // COPY_INT_USHORT
        (byte) 254, 0, 0, 0, 5, 0, 0, 0, 2,                              // COPY_INT_INT
        (byte) 255, 0, 0, 0, 0, 0, 0, 0, 6, 0, 0, 0, 2,                  // COPY_LONG_INT
        0 };                                                             // EOF

    for (int newBytesLimit = 0; newBytesLimit < newBytes.length; newBytesLimit++) {
      checkExpectedIOException(oldBytes, -1, patch, -1, newBytesLimit);
    }
  }

  /**
   * Test for negative values in patch command arguments.  Since the checks for this are
   * in two specific methods, copyFromPatch() and copyFromOriginal(), I'm not going to exercise
   * the full set of opcodes here - just the full set of argument widths.  Note that we don't
   * test "ubyte" or "ushort" values here, as they are unsigned.
   */
  @Test
  public void testNegativeArgumentValues() throws IOException {
    byte[] oldBytes = new byte[] {
        (byte) 'A', (byte) 'B', (byte) 'C', (byte) 'D', (byte) 'E', (byte) 'F', (byte) 'G',
        (byte) 'H', (byte) 'I', (byte) 'J' };

    byte[] negativeDataInt = new byte[] {
        (byte) 0xd1, (byte) 0xff, (byte) 0xd1, (byte) 0xff, (byte) 4,        // magic+version
        (byte) 248, (byte) 255, (byte) 255, (byte) 255, (byte) 255,          // DATA_INT
        (byte) 'Y',
        0 };                                                                 // EOF
    byte[] negativeCopyLengthInt = new byte[] {
        (byte) 0xd1, (byte) 0xff, (byte) 0xd1, (byte) 0xff, (byte) 4,        // magic+version
        (byte) 251, 0, 0, (byte) 255, (byte) 255, (byte) 255, (byte) 255,    // COPY_USHORT_INT
        0 };                                                                 // EOF
    byte[] negativeCopyOffsetInt = new byte[] {
        (byte) 0xd1, (byte) 0xff, (byte) 0xd1, (byte) 0xff, (byte) 4,        // magic+version
        (byte) 252, (byte) 255, (byte) 255, (byte) 255, (byte) 255, 0,       // COPY_INT_UBYTE
        0 };                                                                 // EOF
    byte[] negativeCopyOffsetLong = new byte[] {
        (byte) 0xd1, (byte) 0xff, (byte) 0xd1, (byte) 0xff, (byte) 4,        // magic+version
        (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255,          // COPY_LONG_INT
        (byte) 255, (byte) 255, (byte) 255, (byte) 255, 0, 0, 0 };           // EOF

    // All of these should throw exceptions due to negative numbers in the arguments
    checkExpectedIOException(oldBytes, -1, negativeDataInt, -1, -1);
    checkExpectedIOException(oldBytes, -1, negativeCopyLengthInt, -1, -1);
    checkExpectedIOException(oldBytes, -1, negativeCopyOffsetInt, -1, -1);
    checkExpectedIOException(oldBytes, -1, negativeCopyOffsetLong, -1, -1);
  }

  /**
   * Helper for calling patch(), expects to throw an error.
   *
   * @param inputBytes the bytes representing the input (original) file
   * @param inputLimit if -1, use entire input array.  Otherwise, shorten input to this length.
   * @param patchBytes byte array containing patch
   * @param patchLimit if -1, use entire patch array.  Otherwise, shorten patch to this length.
   * @param outputLimit if -1, expect a "very large" output.  Otherwise, set limit this length.
   */
  private void checkExpectedIOException(byte[] inputBytes, int inputLimit,
      byte[] patchBytes, int patchLimit, int outputLimit) throws IOException {
    if (inputLimit == -1) {
      inputLimit = inputBytes.length;
    }
    // Create "input file".
    File inputFile = File.createTempFile("testExample", null);
    FileOutputStream writeInputFile = new FileOutputStream(inputFile);
    writeInputFile.write(inputBytes, 0, inputLimit);
    writeInputFile.close();
    RandomAccessFile readInputFile = new RandomAccessFile(inputFile, "r");

    if (patchLimit == -1) {
      patchLimit = patchBytes.length;
    }
    ByteArrayInputStream patchStream = new ByteArrayInputStream(patchBytes, 0, patchLimit);

    if (outputLimit == -1) {
      outputLimit = (inputBytes.length * 2) + (patchBytes.length * 2);    // 100% arbitrary
    }
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    try {
      Gdiff.patch(readInputFile, patchStream, outputStream, outputLimit);
      Assert.fail("Expected IOException");
    } catch (IOException expected) {
    }
    Assert.assertTrue(outputStream.size() <= outputLimit);
  }
}
