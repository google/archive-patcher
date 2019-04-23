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

package com.google.archivepatcher.applier.bsdiff;

import static com.google.archivepatcher.shared.bytesource.ByteStreams.copy;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.archivepatcher.applier.PatchFormatException;
import com.google.archivepatcher.shared.bytesource.ByteSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link BsPatch}.
 */
@RunWith(JUnit4.class)
public class BsPatchTest {

  private static final String SIGNATURE = "ENDSLEY/BSDIFF43";
  private byte[] buffer1;
  private byte[] buffer2;

  /**
   * The tests need access to an actual File object for the "old file", so that it can be used as
   * the argument to a RandomAccessFile constructor... but the old file is a resource loaded at test
   * run-time, potentially from a JAR, and therefore a copy must be made in the filesystem to access
   * via RandomAccessFile. This is not true for the new file or the patch file, both of which are
   * streamable.
   */
  private File oldFile;

  @Before
  public void setUp() throws IOException {
    buffer1 = new byte[6];
    buffer2 = new byte[6];
    try {
      oldFile = File.createTempFile("archive_patcher", "old");
      oldFile.deleteOnExit();
    } catch (IOException e) {
      if (oldFile != null) {
        oldFile.delete();
      }
      throw e;
    }
  }

  @After
  public void tearDown() {
    if (oldFile != null) {
      oldFile.delete();
    }
    oldFile = null;
  }

  @Test
  public void testTransformBytes() throws IOException {
    // In this case the "patch stream" is just a stream of addends that transformBytes(...) will
    // apply to the old data file.
    final byte[] patchInput = "this is a sample string to read".getBytes("US-ASCII");
    final ByteArrayInputStream patchInputStream = new ByteArrayInputStream(patchInput);
    copyToOldFile("bsdifftest_partial_a.txt");
    final byte[] expectedNewData = readTestData("bsdifftest_partial_b.bin");
    ByteArrayOutputStream newData = new ByteArrayOutputStream();
    try (ByteSource oldData = ByteSource.fromFile(oldFile);
        InputStream oldDataIn = oldData.openStream()) {
      BsPatch.transformBytes(
          patchInput.length, patchInputStream, oldDataIn, newData, buffer1, buffer2);
    }
    byte[] actual = newData.toByteArray();
    assertThat(actual).isEqualTo(expectedNewData);
  }

  @Test
  public void testTransformBytes_Error_NotEnoughBytes() throws IOException {
    // This test sets up a trivial 1-byte "patch" (addends) stream but then asks
    // transformBytes(...) to apply *2* bytes, which should fail when it hits EOF.
    final InputStream patchIn = new ByteArrayInputStream(new byte[] {(byte) 0x00});
    copyToOldFile("bsdifftest_partial_a.txt"); // Any file would work here
    try (ByteSource oldData = ByteSource.fromFile(oldFile);
        InputStream oldDataIn = oldData.openStream()) {
      BsPatch.transformBytes(2, patchIn, oldDataIn, new ByteArrayOutputStream(), buffer1, buffer2);
      assertWithMessage("Read past EOF").fail();
    } catch (IOException expected) {
      // Pass
    }
  }

  @Test
  public void testTransformBytes_Error_JunkPatch() throws IOException {
    final byte[] patchInput = "this is a second sample string to read".getBytes("US-ASCII");
    final ByteArrayInputStream patchInputStream = new ByteArrayInputStream(patchInput);
    copyToOldFile("bsdifftest_partial_a.txt"); // Any file would work here
    ByteArrayOutputStream newData = new ByteArrayOutputStream();
    try (ByteSource oldData = ByteSource.fromFile(oldFile);
        InputStream oldDataIn = oldData.openStream()) {
      BsPatch.transformBytes(
          patchInput.length, patchInputStream, oldDataIn, newData, buffer1, buffer2);
      assertWithMessage("Should have thrown an IOException").fail();
    } catch (IOException expected) {
      // Pass
    }
  }

  @Test
  public void testTransformBytes_Error_JunkPatch_Underflow() throws IOException {
    final byte[] patchInput = "this is a sample string".getBytes("US-ASCII");
    final ByteArrayInputStream patchInputStream = new ByteArrayInputStream(patchInput);
    copyToOldFile("bsdifftest_partial_a.txt");
    final byte[] buffer1 = new byte[6];
    final byte[] buffer2 = new byte[6];

    ByteArrayOutputStream newData = new ByteArrayOutputStream();
    try (ByteSource oldData = ByteSource.fromFile(oldFile);
        InputStream oldDataIn = oldData.openStream()) {
      BsPatch.transformBytes(
          patchInput.length + 1, patchInputStream, oldDataIn, newData, buffer1, buffer2);
      assertWithMessage("Should have thrown an IOException").fail();
    } catch (IOException expected) {
      // Pass
    }
  }

  @Test
  public void testApplyPatch_ContrivedData() throws Exception {
    invokeApplyPatch(
        "bsdifftest_internal_blob_a.bin",
        "bsdifftest_internal_patch_a_to_b.bin",
        "bsdifftest_internal_blob_b.bin");
  }

  @Test
  public void testApplyPatch_BetterData() throws Exception {
    invokeApplyPatch(
        "bsdifftest_minimal_blob_a.bin",
        "bsdifftest_minimal_patch_a_to_b.bin",
        "bsdifftest_minimal_blob_b.bin");
  }

  @Test
  public void testApplyPatch_BadSignature() throws Exception {
    createEmptyOldFile(10);
    String junkSignature = "WOOOOOO/BSDIFF43"; // Correct length, wrong content
    InputStream patchIn =
        makePatch(
            junkSignature,
            10, // newLength
            10, // diffSegmentLength
            0, // copySegmentLength
            0, // offsetToNextInput
            new byte[10] // addends
            );
    ByteArrayOutputStream newData = new ByteArrayOutputStream();
    try (ByteSource oldData = ByteSource.fromFile(oldFile)) {
      BsPatch.applyPatch(oldData, newData, patchIn);
      assertWithMessage("Read patch with bad signature").fail();
    } catch (PatchFormatException expected) {
      // No way to mock the internal logic, so resort to testing exception string for coverage
      String actual = expected.getMessage();
      assertThat(actual)
          .isEqualTo("bad signature: found WOOOOOO/BSDIFF43 should've been ENDSLEY/BSDIFF43");
    }
  }
  
  @Test
  public void testApplyPatch_NewLengthMismatch() throws Exception {
    createEmptyOldFile(10);
    InputStream patchIn =
        makePatch(
            SIGNATURE,
            10, // newLength (illegal)
            10, // diffSegmentLength
            0, // copySegmentLength
            0, // offsetToNextInput
            new byte[10] // addends
            );
    ByteArrayOutputStream newData = new ByteArrayOutputStream();
    try (ByteSource oldData = ByteSource.fromFile(oldFile)) {
      BsPatch.applyPatch(oldData, newData, patchIn, (long) 10 + 1);
      assertWithMessage("Read patch with mismatched newLength").fail();
    } catch (PatchFormatException expected) {
      // No way to mock the internal logic, so resort to testing exception string for coverage
      String actual = expected.getMessage();
      assertThat(actual).isEqualTo("expectedNewSize != newSize");
    }
  }

  @Test
  public void testApplyPatch_NewLengthNegative() throws Exception {
    createEmptyOldFile(10);
    InputStream patchIn =
        makePatch(
            SIGNATURE,
            -10, // newLength (illegal)
            10, // diffSegmentLength
            0, // copySegmentLength
            0, // offsetToNextInput
            new byte[10] // addends
            );
    ByteArrayOutputStream newData = new ByteArrayOutputStream();
    try {
      BsPatch.applyPatch(oldFile, newData, patchIn);
      assertWithMessage("Read patch with negative newLength").fail();
    } catch (PatchFormatException expected) {
      // No way to mock the internal logic, so resort to testing exception string for coverage
      String actual = expected.getMessage();
      assertThat(actual).isEqualTo("bad newSize");
    }
  }

  @Test
  public void testApplyPatch_NewLengthTooLarge() throws Exception {
    createEmptyOldFile(10);
    InputStream patchIn =
        makePatch(
            SIGNATURE,
            Integer.MAX_VALUE + 1, // newLength (max supported is Integer.MAX_VALUE)
            10, // diffSegmentLength
            0, // copySegmentLength
            0, // offsetToNextInput
            new byte[10] // addends
            );
    ByteArrayOutputStream newData = new ByteArrayOutputStream();
    try {
      BsPatch.applyPatch(oldFile, newData, patchIn);
      assertWithMessage("Read patch with excessive newLength").fail();
    } catch (PatchFormatException expected) {
      // No way to mock the internal logic, so resort to testing exception string for coverage
      String actual = expected.getMessage();
      assertThat(actual).isEqualTo("bad newSize");
    }
  }

  @Test
  public void testApplyPatch_DiffSegmentLengthNegative() throws Exception {
    createEmptyOldFile(10);
    InputStream patchIn =
        makePatch(
            SIGNATURE,
            10, // newLength
            -10, // diffSegmentLength (negative)
            0, // copySegmentLength
            0, // offsetToNextInput
            new byte[10] // addends
            );
    ByteArrayOutputStream newData = new ByteArrayOutputStream();
    try {
      BsPatch.applyPatch(oldFile, newData, patchIn);
      assertWithMessage("Read patch with negative diffSegmentLength").fail();
    } catch (PatchFormatException expected) {
      // No way to mock the internal logic, so resort to testing exception string for coverage
      String actual = expected.getMessage();
      assertThat(actual).isEqualTo("bad diffSegmentLength");
    }
  }

  @Test
  public void testApplyPatch_DiffSegmentLengthTooLarge() throws Exception {
    createEmptyOldFile(10);
    InputStream patchIn =
        makePatch(
            SIGNATURE,
            10, // newLength
            Integer.MAX_VALUE + 1, // diffSegmentLength (too big)
            0, // copySegmentLength
            0, // offsetToNextInput
            new byte[10] // addends
            );
    ByteArrayOutputStream newData = new ByteArrayOutputStream();
    try {
      BsPatch.applyPatch(oldFile, newData, patchIn);
      assertWithMessage("Read patch with excessive diffSegmentLength").fail();
    } catch (PatchFormatException expected) {
      // No way to mock the internal logic, so resort to testing exception string for coverage
      String actual = expected.getMessage();
      assertThat(actual).isEqualTo("bad diffSegmentLength");
    }
  }

  @Test
  public void testApplyPatch_CopySegmentLengthNegative() throws Exception {
    createEmptyOldFile(10);
    InputStream patchIn =
        makePatch(
            SIGNATURE,
            10, // newLength
            10, // diffSegmentLength
            -10, // copySegmentLength (negative)
            0, // offsetToNextInput
            new byte[10] // addends
            );
    ByteArrayOutputStream newData = new ByteArrayOutputStream();
    try {
      BsPatch.applyPatch(oldFile, newData, patchIn);
      assertWithMessage("Read patch with negative copySegmentLength").fail();
    } catch (PatchFormatException expected) {
      // No way to mock the internal logic, so resort to testing exception string for coverage
      String actual = expected.getMessage();
      assertThat(actual).isEqualTo("bad copySegmentLength");
    }
  }

  @Test
  public void testApplyPatch_CopySegmentLengthTooLarge() throws Exception {
    createEmptyOldFile(10);
    InputStream patchIn =
        makePatch(
            SIGNATURE,
            10, // newLength
            0, // diffSegmentLength
            Integer.MAX_VALUE + 1, // copySegmentLength (too big)
            0, // offsetToNextInput
            new byte[10] // addends
            );
    ByteArrayOutputStream newData = new ByteArrayOutputStream();
    try {
      BsPatch.applyPatch(oldFile, newData, patchIn);
      assertWithMessage("Read patch with excessive copySegmentLength").fail();
    } catch (PatchFormatException expected) {
      // No way to mock the internal logic, so resort to testing exception string for coverage
      String actual = expected.getMessage();
      assertThat(actual).isEqualTo("bad copySegmentLength");
    }
  }

  // ExpectedFinalNewDataBytesWritten_Negative case is impossible in code, so no need to test
  // that; just the TooLarge condition.
  @Test
  public void testApplyPatch_ExpectedFinalNewDataBytesWritten_PastEOF() throws Exception {
    createEmptyOldFile(10);
    // Make diffSegmentLength + copySegmentLength > newLength
    InputStream patchIn =
        makePatch(
            SIGNATURE,
            10, // newLength
            10, // diffSegmentLength
            1, // copySegmentLength
            0, // offsetToNextInput
            new byte[10] // addends
            );
    ByteArrayOutputStream newData = new ByteArrayOutputStream();
    try {
      BsPatch.applyPatch(oldFile, newData, patchIn);
      assertWithMessage("Read patch that moves past EOF in new file").fail();
    } catch (PatchFormatException expected) {
      // No way to mock the internal logic, so resort to testing exception string for coverage
      String actual = expected.getMessage();
      assertThat(actual).isEqualTo("expectedFinalNewDataBytesWritten too large");
    }
  }

  @Test
  public void testApplyPatch_ExpectedFinalOldDataOffset_Negative() throws Exception {
    createEmptyOldFile(10);
    // Make diffSegmentLength + offsetToNextInput < 0
    InputStream patchIn =
        makePatch(
            SIGNATURE,
            10, // newLength
            10, // diffSegmentLength
            0, // copySegmentLength
            -11, // offsetToNextInput
            new byte[10] // addends
            );
    ByteArrayOutputStream newData = new ByteArrayOutputStream();
    try {
      BsPatch.applyPatch(oldFile, newData, patchIn);
      assertWithMessage("Read patch with that moves to a negative offset in old file").fail();
    } catch (PatchFormatException expected) {
      // No way to mock the internal logic, so resort to testing exception string for coverage
      String actual = expected.getMessage();
      assertThat(actual).isEqualTo("expectedFinalOldDataOffset is negative");
    }
  }

  @Test
  public void testApplyPatch_ExpectedFinalOldDataOffset_PastEOF() throws Exception {
    createEmptyOldFile(10);
    // Make diffSegmentLength + offsetToNextInput > oldLength
    InputStream patchIn =
        makePatch(
            SIGNATURE,
            10, // newLength
            10, // diffSegmentLength
            0, // copySegmentLength
            1, // offsetToNextInput
            new byte[10] // addends
            );
    ByteArrayOutputStream newData = new ByteArrayOutputStream();
    try {
      BsPatch.applyPatch(oldFile, newData, patchIn);
      assertWithMessage("Read patch with that moves past EOF in old file").fail();
    } catch (PatchFormatException expected) {
      // No way to mock the internal logic, so resort to testing exception string for coverage
      String actual = expected.getMessage();
      assertThat(actual).isEqualTo("expectedFinalOldDataOffset too large");
    }
  }

  @Test
  public void testApplyPatch_TruncatedSignature() throws Exception {
    createEmptyOldFile(10);
    InputStream patchIn = new ByteArrayInputStream("X".getBytes("US-ASCII"));
    ByteArrayOutputStream newData = new ByteArrayOutputStream();
    try {
      BsPatch.applyPatch(oldFile, newData, patchIn);
      assertWithMessage("Read patch with truncated signature").fail();
    } catch (PatchFormatException expected) {
      // No way to mock the internal logic, so resort to testing exception string for coverage
      String actual = expected.getMessage();
      assertThat(actual).isEqualTo("truncated signature");
    }
  }

  @Test
  public void testReadBsdiffLong() throws Exception {
    byte[] data = {
      (byte) 0x78, (byte) 0x56, (byte) 0x34, (byte) 0x12, (byte) 0, (byte) 0, (byte) 0, (byte) 0,
      (byte) 0xef, (byte) 0xbe, (byte) 0xad, (byte) 0x0e, (byte) 0, (byte) 0, (byte) 0, (byte) 0
    };
    ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
    long actual = BsPatch.readBsdiffLong(inputStream);
    assertThat(actual).isEqualTo(0x12345678);
    actual = BsPatch.readBsdiffLong(inputStream);
    assertThat(actual).isEqualTo(0x0eadbeef);
  }

  @Test
  public void testReadBsdiffLong_Zero() throws Exception {
    long expected = 0x00000000L;
    long actual =
        BsPatch.readBsdiffLong(
            new ByteArrayInputStream(
                new byte[] {
                  (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                  (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00
                }));
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void testReadBsdiffLong_IntegerMaxValue() throws Exception {
    long expected = 0x7fffffffL;
    long actual =
        BsPatch.readBsdiffLong(
            new ByteArrayInputStream(
                new byte[] {
                  (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0x7f,
                  (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00
                }));
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void testReadBsdiffLong_IntegerMinValue() throws Exception {
    long expected = -0x80000000L;
    long actual =
        BsPatch.readBsdiffLong(
            new ByteArrayInputStream(
                new byte[] {
                  (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x80,
                  (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x80
                }));
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void testReadBsdiffLong_LongMaxValue() throws Exception {
    long expected = 0x7fffffffffffffffL;
    long actual =
        BsPatch.readBsdiffLong(
            new ByteArrayInputStream(
                new byte[] {
                  (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
                  (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0x7f
                }));
    assertThat(actual).isEqualTo(expected);
  }

  // Can't read Long.MIN_VALUE because the signed-magnitude representation stops at
  // Long.MIN_VALUE+1.
  @Test
  public void testReadBsdiffLong_LongMinValueIsh() throws Exception {
    long expected = -0x7fffffffffffffffL;
    long actual =
        BsPatch.readBsdiffLong(
            new ByteArrayInputStream(
                new byte[] {
                  (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
                  (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff
                }));
    assertThat(actual).isEqualTo(expected);
  }

  // This is also Java's Long.MAX_VALUE.
  @Test
  public void testReadBsdiffLong_NegativeZero() throws Exception {
    try {
      BsPatch.readBsdiffLong(
          new ByteArrayInputStream(
              new byte[] {
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x80
              }));
      assertWithMessage("Tolerated negative zero").fail();
    } catch (PatchFormatException expected) {
      // Pass
    }
  }

  @Test
  public void testPipe() throws IOException {
    final String inputString = "this is a sample string to read";
    final byte[] input = inputString.getBytes("US-ASCII");
    final ByteArrayInputStream inputStream = new ByteArrayInputStream(input);
    final byte[] buffer = new byte[5];
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    BsPatch.pipe(inputStream, outputStream, buffer, 0);
    int actualLength = outputStream.toByteArray().length;
    assertThat(actualLength).isEqualTo(0);

    inputStream.reset();
    BsPatch.pipe(inputStream, outputStream, buffer, 1);
    actualLength = outputStream.toByteArray().length;
    assertThat(actualLength).isEqualTo(1);
    byte actualByte = outputStream.toByteArray()[0];
    assertThat(actualByte).isEqualTo((byte) 't');

    outputStream = new ByteArrayOutputStream();
    inputStream.reset();
    BsPatch.pipe(inputStream, outputStream, buffer, 5);
    actualLength = outputStream.toByteArray().length;
    assertThat(actualLength).isEqualTo(5);
    String actualOutput = outputStream.toString();
    String expectedOutput = inputString.substring(0, 5);
    assertThat(actualOutput).isEqualTo(expectedOutput);

    outputStream = new ByteArrayOutputStream();
    inputStream.reset();
    BsPatch.pipe(inputStream, outputStream, buffer, input.length);
    actualLength = outputStream.toByteArray().length;
    assertThat(actualLength).isEqualTo(input.length);
    expectedOutput = outputStream.toString();
    assertThat(expectedOutput).isEqualTo(inputString);
  }

  @Test
  public void testPipe_Underrun() {
    int dataLength = 10;
    ByteArrayInputStream in = new ByteArrayInputStream(new byte[dataLength]);
    try {
      // Tell pipe to copy 1 more byte than is actually available
      BsPatch.pipe(in, new ByteArrayOutputStream(), new byte[dataLength], dataLength + 1);
      assertWithMessage("Should've thrown an IOException").fail();
    } catch (IOException expected) {
      // Pass
    }
  }

  @Test
  public void testPipe_CopyZeroBytes() throws IOException {
    int dataLength = 0;
    ByteArrayInputStream in = new ByteArrayInputStream(new byte[dataLength]);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    BsPatch.pipe(in, out, new byte[100], dataLength);
    int actualLength = out.toByteArray().length;
    assertThat(actualLength).isEqualTo(0);
  }

  /**
   * Invoke applyPatch(...) and verify that the results are as expected.
   * @param oldPath the path to the old asset in /assets
   * @param patchPatch the path to the patch asset in /assets
   * @param newPath the path to the new asset in /assets
   * @throws IOException if unable to read/write
   * @throws PatchFormatException if the patch is invalid
   */
  private void invokeApplyPatch(String oldPath, String patchPatch, String newPath)
      throws IOException, PatchFormatException {
    copyToOldFile(oldPath);
    InputStream patchInputStream = new ByteArrayInputStream(readTestData(patchPatch));
    byte[] expectedNewDataBytes = readTestData(newPath);
    ByteArrayOutputStream actualNewData = new ByteArrayOutputStream();
    BsPatch.applyPatch(oldFile, actualNewData, patchInputStream);
    byte[] actualNewDataBytes = actualNewData.toByteArray();
    assertThat(actualNewDataBytes).isEqualTo(expectedNewDataBytes);
  }

  // (Copied from BsDiffTest)
  // Some systems force all text files to end in a newline, which screws up this test.
  private static byte[] stripNewlineIfNecessary(byte[] b) {
    if (b[b.length - 1] != (byte) '\n') {
      return b;
    }

    byte[] ret = new byte[b.length - 1];
    System.arraycopy(b, 0, ret, 0, ret.length);
    return ret;
  }

  // (Copied from BsDiffTest)
  private byte[] readTestData(String testDataFileName) throws IOException {
    InputStream in = getClass().getResourceAsStream("testdata/" + testDataFileName);
    assertWithMessage("test data file doesn't exist: " + testDataFileName).that(in).isNotNull();
    ByteArrayOutputStream result = new ByteArrayOutputStream();
    copy(in, result);
    return stripNewlineIfNecessary(result.toByteArray());
  }

  /**
   * Copy the contents of the specified testdata asset into {@link #oldFile}.
   * @param testDataFileName the name of the testdata asset to read
   * @throws IOException if unable to complete the copy
   */
  private void copyToOldFile(String testDataFileName) throws IOException {
    oldFile = File.createTempFile("archive_patcher", "temp");
    assertWithMessage("cant create file!").that(oldFile).isNotNull();
    byte[] buffer = readTestData(testDataFileName);
    FileOutputStream out = new FileOutputStream(oldFile);
    out.write(buffer);
    out.flush();
    out.close();
  }

  /**
   * Make {@link #oldFile} an empty file (full of binary zeroes) of the specified length.
   * @param desiredLength the desired length in bytes
   * @throws IOException if unable to write the file
   */
  private void createEmptyOldFile(int desiredLength) throws IOException {
    OutputStream out = new FileOutputStream(oldFile);
    for (int x = 0; x < desiredLength; x++) {
      out.write(0);
    }
    out.close();
  }

  /**
   * Create an arbitrary patch that consists of a signature, a length, and a directive sequence.
   * Used to manufacture junk for failure and edge cases.
   * @param signature the signature to use
   * @param newLength the expected length of the "new" file produced by applying the patch
   * @param diffSegmentLength the value to supply as diffSegmentLength
   * @param copySegmentLength the value to supply as copySegmentLength
   * @param offsetToNextInput the value to supply as offsetToNextInput
   * @param addends a byte array of addends; all are written, ignoring |diffSegmentLength|.
   * @return the bytes constituting the patch
   * @throws IOException
   */
  private static InputStream makePatch(
      String signature,
      long newLength,
      long diffSegmentLength,
      long copySegmentLength,
      long offsetToNextInput,
      byte[] addends)
      throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.write(signature.getBytes("US-ASCII"));
    writeBsdiffLong(newLength, out);
    writeBsdiffLong(diffSegmentLength, out);
    writeBsdiffLong(copySegmentLength, out);
    writeBsdiffLong(offsetToNextInput, out);
    out.write(addends);
    return new ByteArrayInputStream(out.toByteArray());
  }

  // Copied from com.google.archivepatcher.generator.bsdiff.BsUtil for convenience.
  private static void writeBsdiffLong(final long value, OutputStream out) throws IOException {
    long y = value;
    if (y < 0) {
      y = (-y) | (1L << 63);
    }
    for (int i = 0; i < 8; ++i) {
      out.write((byte) (y & 0xff));
      y >>>= 8;
    }
  }
}
