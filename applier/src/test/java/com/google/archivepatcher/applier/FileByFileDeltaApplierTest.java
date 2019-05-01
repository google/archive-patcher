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

package com.google.archivepatcher.applier;

import static com.google.common.truth.Truth.assertThat;

import com.google.archivepatcher.shared.JreDeflateParameters;
import com.google.archivepatcher.shared.PatchConstants;
import com.google.archivepatcher.shared.PatchConstants.DeltaFormat;
import com.google.archivepatcher.shared.UnitTestZipEntry;
import com.google.archivepatcher.shared.bytesource.ByteSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link FileByFileDeltaApplier}. */
@RunWith(JUnit4.class)
@SuppressWarnings("javadoc")
public class FileByFileDeltaApplierTest {

  // These constants are used to construct all the blobs (note the OLD and NEW contents):
  //   old file := UNCOMPRESSED_HEADER + COMPRESSED_OLD_CONTENT + UNCOMPRESSED_TRAILER +
  //               UNCOMPRESSED_ZIP_OLD_CONTENT
  //   delta-friendly old file := UNCOMPRESSED_HEADER + UNCOMPRESSED_OLD_CONTENT +
  //                              UNCOMPRESSED_TRAILER + UNCOMPRESSED_ZIP_OLD_CONTENT
  //   delta-friendly new file := UNCOMPRESSED_HEADER + UNCOMPRESSED_NEW_CONTENT +
  //                              UNCOMPRESSED_TRAILER + UNCOMPRESSED_ZIP_NEW_CONTENT
  //   new file := UNCOMPRESSED_HEADER + COMPRESSED_NEW_CONTENT + UNCOMPRESSED_TRAILIER +
  //                                     UNCOMPRESSED_ZIP_NEW_CONTENT
  // NB: The patch *applier* is agnostic to the format of the file, and so it doesn't have to be a
  //     valid zip or zip-like archive.
  // We expect the delta applier to apply BSDIFF to the first three sections and FBF to the last.
  private static final JreDeflateParameters PARAMS1 = JreDeflateParameters.of(6, 0, true);
  private static final String OLD_CONTENT = "This is Content the Old";
  private static final UnitTestZipEntry OLD_ENTRY =
      new UnitTestZipEntry("/foo", PARAMS1.level, PARAMS1.nowrap, OLD_CONTENT, null);
  private static final String NEW_CONTENT = "Rambunctious Absinthe-Loving Stegosaurus";
  private static final UnitTestZipEntry NEW_ENTRY =
      new UnitTestZipEntry("/foo", PARAMS1.level, PARAMS1.nowrap, NEW_CONTENT, null);

  // We have to make sure these are valid so we can verify that the applier handles "uncompression
  // settings" correctly.
  private static final byte[] UNCOMPRESSED_OLD_CONTENT = OLD_ENTRY.getUncompressedBinaryContent();
  private static final byte[] COMPRESSED_OLD_CONTENT = OLD_ENTRY.getCompressedBinaryContent();
  private static final byte[] UNCOMPRESSED_NEW_CONTENT = NEW_ENTRY.getUncompressedBinaryContent();
  private static final byte[] COMPRESSED_NEW_CONTENT = NEW_ENTRY.getCompressedBinaryContent();

  // These can just be random data. We make them all distinct to prevent accidental false-positives.
  private static final byte[] UNCOMPRESSED_HEADER = new byte[] {0, 1, 2, 3, 4};
  private static final byte[] UNCOMPRESSED_TRAILER = new byte[] {5, 6, 7, 8, 9};
  private static final byte[] UNCOMPRESSED_ZIP_OLD_CONTENT = new byte[] {10, 11, 12, 13, 14};
  private static final byte[] UNCOMPRESSED_ZIP_NEW_CONTENT = new byte[] {15, 16, 17, 18, 19};
  private static final byte[] BSDIFF_DELTA = new byte[] {20, 21, 22, 23, 24};
  private static final byte[] FBF_DELTA = new byte[] {25, 26, 27, 28, 29};

  // Helper variables for later processing
  private static final byte[] BSDIFF_OLD_SECTION =
      concat(UNCOMPRESSED_HEADER, UNCOMPRESSED_OLD_CONTENT, UNCOMPRESSED_TRAILER);
  private static final byte[] BSDIFF_NEW_SECTION =
      concat(UNCOMPRESSED_HEADER, UNCOMPRESSED_NEW_CONTENT, UNCOMPRESSED_TRAILER);
  private static final byte[] FBF_OLD_SECTION = UNCOMPRESSED_ZIP_OLD_CONTENT;
  private static final byte[] FBF_NEW_SECTION = UNCOMPRESSED_ZIP_NEW_CONTENT;

  /**
   * Where to store temp files.
   */
  private File tempDir;

  /**
   * The old file.
   */
  private File oldFile;

  /**
   * Bytes that describe a patch to convert the old file to the new file.
   */
  private byte[] patchBytes;

  /**
   * Bytes that describe the new file.
   */
  private byte[] expectedNewBytes;

  /**
   * For debugging test issues, it is convenient to be able to see these bytes in the debugger
   * instead of on the filesystem.
   */
  private byte[] oldFileBytes;

  /**
   * To mock the dependency on bsdiff, a subclass of FileByFileDeltaApplier is made that always
   * returns a testing delta applier. This delta applier asserts that the old content is as
   * expected, and "patches" it by simply writing the expected *new* content to the output stream.
   */
  private FileByFileDeltaApplier fakeApplier;

  @Before
  public void setUp() throws IOException {
    // Creates the following resources:
    // 1. The old file, on disk (and in-memory, for convenience).
    // 2. The new file, in memory only (for comparing results at the end).
    // 3. The patch, in memory.

    File tempFile = File.createTempFile("foo", "bar");
    tempDir = tempFile.getParentFile();
    tempFile.delete();
    oldFile = File.createTempFile("fbfv1dat", "old");
    oldFile.deleteOnExit();

    // Write the old file to disk:
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    buffer.write(UNCOMPRESSED_HEADER);
    buffer.write(COMPRESSED_OLD_CONTENT);
    buffer.write(UNCOMPRESSED_TRAILER);
    buffer.write(UNCOMPRESSED_ZIP_OLD_CONTENT);
    oldFileBytes = buffer.toByteArray();
    FileOutputStream out = new FileOutputStream(oldFile);
    out.write(oldFileBytes);
    out.flush();
    out.close();

    // Write the new file to a byte array
    buffer = new ByteArrayOutputStream();
    buffer.write(UNCOMPRESSED_HEADER);
    buffer.write(COMPRESSED_NEW_CONTENT);
    buffer.write(UNCOMPRESSED_TRAILER);
    buffer.write(UNCOMPRESSED_ZIP_NEW_CONTENT);
    expectedNewBytes = buffer.toByteArray();

    // Finally, write the patch that should transform old to new
    patchBytes = writePatch();

    // Initialize fake delta applier to mock out dependency on bsdiff
    fakeApplier =
        new FileByFileDeltaApplier(tempDir) {
          @Override
          protected DeltaApplier getDeltaApplier(DeltaFormat deltaFormat) {
            return new FakeDeltaApplier(deltaFormat);
          }
        };
  }

  /**
   * Write a patch that will convert the old file to the new file, and return it.
   * @return the patch, as a byte array
   * @throws IOException if anything goes wrong
   */
  private byte[] writePatch() throws IOException {
    // The long type cast is to prevent int overflow.
    long deltaFriendlyOldFileSize = ((long) BSDIFF_OLD_SECTION.length) + FBF_OLD_SECTION.length;

    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    DataOutputStream dataOut = new DataOutputStream(buffer);
    // Now write a patch, independent of the PatchWrite code.
    dataOut.write(PatchConstants.IDENTIFIER.getBytes(StandardCharsets.US_ASCII));
    dataOut.writeInt(0); // Flags (reserved)
    dataOut.writeLong(deltaFriendlyOldFileSize);

    // Write a single uncompress instruction to uncompress the compressed content in oldFile
    dataOut.writeInt(1); // num instructions that follow
    dataOut.writeLong(UNCOMPRESSED_HEADER.length);
    dataOut.writeLong(COMPRESSED_OLD_CONTENT.length);

    // Write a single compress instruction to recompress the uncompressed content in the
    // delta-friendly old file.
    dataOut.writeInt(1); // num instructions that follow
    dataOut.writeLong(UNCOMPRESSED_HEADER.length);
    dataOut.writeLong(UNCOMPRESSED_NEW_CONTENT.length);
    dataOut.write(PatchConstants.CompatibilityWindowId.DEFAULT_DEFLATE.patchValue);
    dataOut.write(PARAMS1.level);
    dataOut.write(PARAMS1.strategy);
    dataOut.write(PARAMS1.nowrap ? 1 : 0);

    // Write a delta. This test class uses its own delta applier to intercept and mangle the data.
    dataOut.writeInt(2);

    // First, write a BSDIFF patch.
    dataOut.write(PatchConstants.DeltaFormat.BSDIFF.patchValue);
    dataOut.writeLong(0); // i.e., start of the working range in the delta-friendly old file
    dataOut.writeLong(BSDIFF_OLD_SECTION.length); // i.e., length of the working range in old
    dataOut.writeLong(0); // i.e., start of the working range in the delta-friendly new file
    dataOut.writeLong(BSDIFF_NEW_SECTION.length); // i.e., length of the working range in new

    // Write the length of the delta and the delta itself. Again, this test class uses its own
    // delta applier; so this is irrelevant.
    dataOut.writeLong(BSDIFF_DELTA.length);
    dataOut.write(BSDIFF_DELTA);

    // Do the same thing for FBF patch
    dataOut.write(DeltaFormat.FILE_BY_FILE.patchValue);
    dataOut.writeLong(
        BSDIFF_OLD_SECTION
            .length); // i.e., start of the working range in the delta-friendly old file
    dataOut.writeLong(FBF_OLD_SECTION.length); // i.e., length of the working range in old
    dataOut.writeLong(
        BSDIFF_NEW_SECTION
            .length); // i.e., start of the working range in the delta-friendly new file
    dataOut.writeLong(FBF_NEW_SECTION.length); // i.e., length of the working range in new
    dataOut.writeLong(FBF_DELTA.length);
    dataOut.write(FBF_DELTA);

    dataOut.flush();
    return buffer.toByteArray();
  }

  private class FakeDeltaApplier extends DeltaApplier {

    private final byte[] expectedDelta;
    private final byte[] expectedOldBlobData;
    private final byte[] newBlobData;

    public FakeDeltaApplier(DeltaFormat deltaFormat) {
      switch (deltaFormat) {
        case BSDIFF:
          expectedDelta = BSDIFF_DELTA;
          expectedOldBlobData = BSDIFF_OLD_SECTION;
          newBlobData = BSDIFF_NEW_SECTION;
          break;
        case FILE_BY_FILE:
          expectedDelta = FBF_DELTA;
          expectedOldBlobData = FBF_OLD_SECTION;
          newBlobData = FBF_NEW_SECTION;
          break;
        default:
          throw new IllegalArgumentException("Invalid delta format " + deltaFormat);
      }
    }

    @SuppressWarnings("resource")
    @Override
    public void applyDelta(ByteSource oldBlob, InputStream deltaIn, OutputStream newBlobOut)
        throws IOException {
      // Check the patch is as expected
      DataInputStream deltaData = new DataInputStream(deltaIn);
      byte[] actualDeltaDataRead = new byte[expectedDelta.length];
      deltaData.readFully(actualDeltaDataRead);
      assertThat(actualDeltaDataRead).isEqualTo(expectedDelta);

      // Check that the old data is as expected
      byte[] oldData = new byte[expectedOldBlobData.length];
      try (InputStream oldBlobIn = oldBlob.openStream();
          DataInputStream oldBlobDataIn = new DataInputStream(oldBlobIn)) {
        oldBlobDataIn.readFully(oldData);
      }
      assertThat(oldData).isEqualTo(expectedOldBlobData);

      // "Convert" the old blob to the new blow as if this were a real patching algorithm.
      newBlobOut.write(newBlobData);
    }
  }

  @After
  public void tearDown() {
    try {
      oldFile.delete();
    } catch (Exception ignored) {
      // Nothing
    }
  }

  @Test
  public void testApplyDelta() throws IOException {
    // Test all aspects of patch apply: copying, uncompressing and recompressing ranges.
    // This test uses the subclasses applier to apply the test patch to the old file, producing the
    // new file. Along the way the entry is uncompressed, altered by the testing delta applier, and
    // recompressed. It's deceptively simple below, but this is a lot of moving parts.
    ByteArrayOutputStream actualNewBlobOut = new ByteArrayOutputStream();
    fakeApplier.applyDelta(oldFile, new ByteArrayInputStream(patchBytes), actualNewBlobOut);
    assertThat(actualNewBlobOut.toByteArray()).isEqualTo(expectedNewBytes);
  }

  @Test
  public void testApplyDelta_DoesntCloseStream() throws IOException {
    // Test for https://github.com/andrewhayden/archive-patcher/issues/6
    final AtomicBoolean closed = new AtomicBoolean(false);
    ByteArrayOutputStream actualNewBlobOut = new ByteArrayOutputStream() {
      @Override
      public void close() throws IOException {
        closed.set(true);
      }
    };
    fakeApplier.applyDelta(oldFile, new ByteArrayInputStream(patchBytes), actualNewBlobOut);
    assertThat(actualNewBlobOut.toByteArray()).isEqualTo(expectedNewBytes);
    assertThat(closed.get()).isFalse();
  }

  private static byte[] concat(byte[]... byteArrays) {
    try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
      for (byte[] byteArray : byteArrays) {
        outputStream.write(byteArray);
      }
      return outputStream.toByteArray();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
