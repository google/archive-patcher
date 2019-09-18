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

package com.google.archivepatcher.applier.gdiff;

import static com.google.archivepatcher.shared.bytesource.ByteStreams.COPY_BUFFER_SIZE;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Random;
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

  private static final byte[] INPUT_1 =
      new byte[] {
          (byte) 'A', (byte) 'B', (byte) 'C', (byte) 'D', (byte) 'E', (byte) 'F', (byte) 'G'
      };
  private static final byte[] INPUT_2 =
      new byte[] {
          (byte) 'A', (byte) 'B', (byte) 'C', (byte) 'D', (byte) 'E', (byte) 'F', (byte) 'G',
          (byte) 'H', (byte) 'I', (byte) 'J'
      };
  private static final byte[] INPUT_3 =
      new byte[] {
          (byte) 'A', (byte) 'B', (byte) 'C', (byte) 'D', (byte) 'E', (byte) 'F', (byte) 'G',
          (byte) 'H'
      };
  private static final String INPUT_4_FILE_NAME = "source.zip";

  /** Normal patch used for basic tests. */
  private static final byte[] PATCH_1 =
      new byte[] {
          (byte) 0xd1, (byte) 0xff, (byte) 0xd1, (byte) 0xff, (byte) 4,  // magic + version
          (byte) 249, 0, 0, 2,                                           // COPY_USHORT_UBYTE 0, 2
          (byte) 2, (byte) 'X', (byte) 'Y',                              // DATA_2
          (byte) 249, 0, 2, 2,                                           // COPY_USHORT_UBYTE 2, 2
          (byte) 249, 0, 1, 4,                                           // COPY_USHORT_UBYTE 1, 4
          0                                                              // EOF
      };
  /**
   * Used to test:
   *
   * <ul>
   *   <li> Each opcode except for the full range of data opcodes.
   *   <li> Input underruns (input terminates earlier than expected)
   *   <li> Output overruns
   * </ul>
   */
  private static final byte[] PATCH_2 =
      new byte[] {
          (byte) 0xd1, (byte) 0xff, (byte) 0xd1, (byte) 0xff, (byte) 4,  // magic + version
          (byte) 1, (byte) 'X',                                          // DATA_MIN (DATA_1)
          (byte) 247, 0, 1, (byte) 'Y',                                  // DATA_USHORT
          (byte) 248, 0, 0, 0, 1, (byte) 'Z',                            // DATA_INT
          (byte) 249, 0, 0, 2,                                           // COPY_USHORT_UBYTE
          (byte) 250, 0, 1, 0, 2,                                        // COPY_USHORT_USHORT
          (byte) 251, 0, 2, 0, 0, 0, 2,                                  // COPY_USHORT_INT
          (byte) 252, 0, 0, 0, 3, 2,                                     // COPY_INT_UBYTE
          (byte) 253, 0, 0, 0, 4, 0, 2,                                  // COPY_INT_USHORT
          (byte) 254, 0, 0, 0, 5, 0, 0, 0, 2,                            // COPY_INT_INT
          (byte) 255, 0, 0, 0, 0, 0, 0, 0, 6, 0, 0, 0, 2,                // COPY_LONG_INT
          0                                                              // EOF
      };
  /** Used to test patch underruns (patch terminates earlier than expected). */
  private static final byte[] PATCH_3 =
      new byte[] {
          (byte) 0xd1, (byte) 0xff, (byte) 0xd1, (byte) 0xff, (byte) 4,  // magic + version
          (byte) 249, 0, 0, 2,                                           // COPY_USHORT_UBYTE 0, 2
          (byte) 2, (byte) 'X', (byte) 'Y',                              // DATA_2
          (byte) 249, 0, 2, 2,                                           // COPY_USHORT_UBYTE 2, 2
          (byte) 249, 0, 1, 4,                                           // COPY_USHORT_UBYTE 1, 4
          0                                                              // EOF
      };
  /** Used to test a negative DATA_INT error. */
  private static final byte[] PATCH_4 =
      new byte[] {
          (byte) 0xd1, (byte) 0xff, (byte) 0xd1, (byte) 0xff, (byte) 4,  // magic + version
          (byte) 248, (byte) 255, (byte) 255, (byte) 255, (byte) 255,    // DATA_INT
          (byte) 'Y',
          0                                                              // EOF
      };
  /** Used to test a negative COPY_USHORT_INT error. */
  private static final byte[] PATCH_5 =
      new byte[] {
          (byte) 0xd1, (byte) 0xff, (byte) 0xd1, (byte) 0xff, (byte) 4,      // magic + version
          (byte) 251, 0, 0, (byte) 255, (byte) 255, (byte) 255, (byte) 255,  // COPY_USHORT_INT
          0                                                                  // EOF
      };
  /** Used to test a negative COPY_INT_UBYTE error. */
  private static final byte[] PATCH_6 =
      new byte[] {
          (byte) 0xd1, (byte) 0xff, (byte) 0xd1, (byte) 0xff, (byte) 4,   // magic + version
          (byte) 252, (byte) 255, (byte) 255, (byte) 255, (byte) 255, 0,  // COPY_INT_UBYTE
          0                                                               // EOF
      };
  /** Used to test a negative COPY_LONG_INT error. */
  private static final byte[] PATCH_7 =
      new byte[] {
          (byte) 0xd1, (byte) 0xff, (byte) 0xd1, (byte) 0xff, (byte) 4,   // magic + version
          (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255,     // COPY_LONG_INT
          (byte) 255, (byte) 255, (byte) 255, (byte) 255, 0, 0, 0         // EOF
      };
  private static final String PATCH_8_FILE_NAME = "patch.gdiff";

  private static final byte[] MAGIC_VERSION_PREFIX =
      new byte[] {
          (byte) 0xd1, (byte) 0xff, (byte) 0xd1, (byte) 0xff, (byte) 4
      };

  private static final byte[] OUTPUT_1 =
      new byte[] {
          (byte) 'A', (byte) 'B', (byte) 'X', (byte) 'Y', (byte) 'C', (byte) 'D', (byte) 'B',
          (byte) 'C', (byte) 'D', (byte) 'E'
      };
  private static final byte[] OUTPUT_2 =
      new byte[] {
          (byte) 'X', (byte) 'Y', (byte) 'Z', (byte) 'A', (byte) 'B', (byte) 'B', (byte) 'C',
          (byte) 'C', (byte) 'D', (byte) 'D', (byte) 'E', (byte) 'E', (byte) 'F', (byte) 'F',
          (byte) 'G', (byte) 'G', (byte) 'H'
      };
  private static final String OUTPUT_3_FILE_NAME = "target.zip";


  /** Very lightweight smoke test using example in http://www.w3.org/TR/NOTE-gdiff-19970901 */
  @Test
  public void testExample() throws IOException {
    File input = createInputFile(INPUT_1);
    ByteArrayInputStream patchStream = new ByteArrayInputStream(PATCH_1);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream(OUTPUT_1.length);

    long outputLength = Gdiff.patch(input, patchStream, outputStream, OUTPUT_1.length);
    assertThat(outputLength).isEqualTo(OUTPUT_1.length);
    assertThat(outputStream.toByteArray()).isEqualTo(OUTPUT_1);
  }

  @Test
  public void testLargePatch() throws IOException {
    InputStream patchStream = new ByteArrayInputStream(readTestData(PATCH_8_FILE_NAME));
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    byte[] expectedNewDataBytes = readTestData(OUTPUT_3_FILE_NAME);

    long outputLength = Gdiff.patch(createTempInputFile(INPUT_4_FILE_NAME), patchStream, outputStream, expectedNewDataBytes.length);
    assertThat(outputLength).isEqualTo(expectedNewDataBytes.length);
    assertThat(outputStream.toByteArray()).isEqualTo(expectedNewDataBytes);
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
    File input = createInputFile(INPUT_2);
    ByteArrayInputStream patchStream = new ByteArrayInputStream(PATCH_2);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream(OUTPUT_2.length);

    long outputLength = Gdiff.patch(input, patchStream, outputStream, OUTPUT_2.length);
    assertThat(outputLength).isEqualTo(OUTPUT_2.length);
    assertThat(outputStream.toByteArray()).isEqualTo(OUTPUT_2);
  }

  /** A test to exercise all of the data inline opcodes (commands 1..246). */
  @Test
  public void testInlineDataCommands() throws IOException {
    // We never read "input" so create an single, empty one.
    File input = createInputFile(new byte[]{});
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
      System.arraycopy(MAGIC_VERSION_PREFIX, 0, patch, 0, MAGIC_VERSION_PREFIX.length);
      patch[5] = (byte) spanLength;
      System.arraycopy(data, 0, patch, 6, spanLength);
      patch[6 + spanLength] = 0;  // EOF

      // Create "patch" file - this is just a stream
      ByteArrayInputStream patchStream = new ByteArrayInputStream(patch);

      // Create "output" file - this is just a stream
      ByteArrayOutputStream outputStream = new ByteArrayOutputStream(data.length);

      // Run the patch and check the output file
      long outputLength = Gdiff.patch(input, patchStream, outputStream, data.length);

      assertThat(outputLength).isEqualTo(spanLength);
      assertThat(outputStream.toByteArray()).isEqualTo(data);
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
  public void testPatchUnderrunFile() throws IOException {
    int[] patchLimits =
        new int[] {
            3,    // Short magic bytes
            4,    // No version
            6,    // After an opcode
            11,   // During inline data
            20    // Missing EOF
        };

    for (int patchLimit : patchLimits) {
      checkExpectedIOException(
          INPUT_1,
          /* inputLimit= */-1,
          PATCH_3,
          patchLimit,
          /* outputLimit= */ -1);
    }
  }

  /**
   * Tests for input underrun (the input terminates earlier than expected in each case).
   * Reuses the "all opcodes" patch for maximum coverage.
   */
  @Test
  public void testInputUnderrun() throws IOException {
    // The {@link INPUT_3} array is sufficient to satisfy the patch, but this loop terminates
    // one byte short of using the entire input array.
    for (int inputLimit = 0; inputLimit < INPUT_3.length; inputLimit++) {
      checkExpectedIOException(
          INPUT_3,
          inputLimit,
          PATCH_2,
          /* patchLimit= */ -1,
          /* outputLimit= */ -1);
    }
  }

  /**
   * Tests for overrun limiter in output file.
   * Reuses the "all opcodes" patch for maximum coverage.
   */
  @Test
  public void testOutputOverrun() throws IOException {
    for (int newBytesLimit = 0; newBytesLimit < OUTPUT_2.length; newBytesLimit++) {
      checkExpectedIOException(INPUT_2, -1, PATCH_2, -1, newBytesLimit);
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
    // All of these should throw exceptions due to negative numbers in the patch arguments
    for (byte[] patch : Arrays.asList(PATCH_4, PATCH_5, PATCH_6, PATCH_7)) {
      checkExpectedIOException(
          INPUT_2,
          /* inputLimit= */-1,
          patch,
          /* patchLimit= */ -1,
          /* outputLimit= */ -1);
    }
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

    if (patchLimit == -1) {
      patchLimit = patchBytes.length;
    }
    ByteArrayInputStream patchStream = new ByteArrayInputStream(patchBytes, 0, patchLimit);

    if (outputLimit == -1) {
      outputLimit = (inputBytes.length * 2) + (patchBytes.length * 2);    // 100% arbitrary
    }
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    try {
      Gdiff.patch(inputFile, patchStream, outputStream, outputLimit);
      assertWithMessage("Expected IOException").fail();
    } catch (IOException expected) {
    }
    assertThat(outputStream.size()).isAtMost(outputLimit);
  }

  private byte[] readTestData(String testDataFileName) throws IOException {
    InputStream in = getClass().getResourceAsStream("testdata/" + testDataFileName);
    assertWithMessage("test data file doesn't exist: " + testDataFileName).that(in).isNotNull();
    ByteArrayOutputStream result = new ByteArrayOutputStream();
    copy(in, result);
    return stripNewlineIfNecessary(result.toByteArray());
  }

  /** Copy the contents of the specified testdata asset into temporary file. */
  private File createTempInputFile(String testDataFileName) throws IOException {
    File tempFile = File.createTempFile("archive_patcher", "temp");
    assertWithMessage("cant create file!").that(tempFile).isNotNull();
    byte[] buffer = readTestData(testDataFileName);
    try (FileOutputStream out = new FileOutputStream(tempFile)) {
      out.write(buffer);
    }
    return tempFile;
  }


  private static File createInputFile(byte[] content) throws IOException {
    File inputFile = File.createTempFile("testExample", null);
    try (FileOutputStream writeInputFile = new FileOutputStream(inputFile)) {
      writeInputFile.write(content);
      writeInputFile.write(content);
    }
    return inputFile;
  }

  /** Copies everything from an {@link InputStream} to an {@link OutputStream}. */
  public static void copy(InputStream in, OutputStream out) throws IOException {
    byte[] buffer = new byte[COPY_BUFFER_SIZE];
    int numRead;
    while ((numRead = in.read(buffer)) >= 0) {
      out.write(buffer, 0, numRead);
    }
  }

  /** Some systems force all text files to end in a newline, which screws up this test. */
  private static byte[] stripNewlineIfNecessary(byte[] b) {
    if (b[b.length - 1] != (byte) '\n') {
      return b;
    }

    return Arrays.copyOf(b, b.length - 1);
  }
}
