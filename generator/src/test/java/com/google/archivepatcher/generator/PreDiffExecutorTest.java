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

package com.google.archivepatcher.generator;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.archivepatcher.shared.PatchConstants.DeltaFormat;
import com.google.archivepatcher.shared.UnitTestZipArchive;
import com.google.archivepatcher.shared.UnitTestZipEntry;
import com.google.archivepatcher.shared.bytesource.ByteSource;
import com.google.common.collect.ImmutableList;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link PreDiffExecutor}.
 */
@RunWith(JUnit4.class)
@SuppressWarnings("javadoc")
public class PreDiffExecutorTest {
  private static final UnitTestZipEntry ENTRY_LEVEL_6 =
      UnitTestZipArchive.makeUnitTestZipEntry("/for/great/justice", 6, "entry A", null);
  private static final UnitTestZipEntry ENTRY_LEVEL_9 =
      UnitTestZipArchive.makeUnitTestZipEntry("/for/great/justice", 9, "entry A", null);

  private List<File> tempFilesCreated;
  private TempBlob deltaFriendlyOldFile;
  private TempBlob deltaFriendlyNewFile;

  @Before
  public void setup() throws IOException {
    tempFilesCreated = new LinkedList<File>();
    deltaFriendlyOldFile = new TempBlob();
    deltaFriendlyNewFile = new TempBlob();
  }

  @After
  public void tearDown() {
    for (File file : tempFilesCreated) {
      try {
        file.delete();
      } catch (Exception ignored) {
        // Nothing
      }
    }
  }

  /**
   * Stores the specified bytes to disk in a temp file and returns the temp file.
   * @param data the bytes to store
   * @throws IOException if it fails
   */
  private File store(byte[] data) throws IOException {
    File file = newTempFile();
    FileOutputStream out = new FileOutputStream(file);
    out.write(data);
    out.flush();
    out.close();
    return file;
  }

  /**
   * Make a new temp file and schedule it for deletion on exit and during teardown.
   * @return the file created
   * @throws IOException if anything goes wrong
   */
  private File newTempFile() throws IOException {
    File file = File.createTempFile("pdet", "bin");
    tempFilesCreated.add(file);
    file.deleteOnExit();
    return file;
  }

  private MinimalZipEntry findEntry(File file, String path) throws IOException {
    List<MinimalZipEntry> entries = MinimalZipArchive.listEntries(file);
    for (MinimalZipEntry entry : entries) {
      if (path.equals(entry.getFileName())) {
        return entry;
      }
    }
    assertWithMessage("path not found: " + path).fail();
    return null; // Never executed
  }

  @Test
  public void testPrepareForDiffing_OneCompressedEntry_Unchanged() throws IOException {
    byte[] bytes = UnitTestZipArchive.makeTestZip(ImmutableList.of(ENTRY_LEVEL_6));
    File oldFile = store(bytes);
    File newFile = store(bytes);
    PreDiffPlan plan;
    try (ByteSource oldBlob = ByteSource.fromFile(oldFile);
        ByteSource newBlob = ByteSource.fromFile(newFile)) {
      PreDiffExecutor executor =
          new PreDiffExecutor.Builder()
              .readingOriginalFiles(oldBlob, newBlob)
              .writingDeltaFriendlyFiles(deltaFriendlyOldFile, deltaFriendlyNewFile)
              .build();
      plan = executor.prepareForDiffing();
    }
    assertThat(plan).isNotNull();
    // The plan should be to leave everything alone because there is no change.
    assertThat(plan.getOldFileUncompressionPlan()).isEmpty();
    assertThat(plan.getNewFileUncompressionPlan()).isEmpty();
    assertThat(plan.getDeltaFriendlyNewFileRecompressionPlan()).isEmpty();
    // Because nothing has changed, the delta-friendly files should be exact matches for the
    // original files.
    assertEqualBytes(bytes, deltaFriendlyOldFile);
    assertEqualBytes(bytes, deltaFriendlyNewFile);
  }

  @Test
  public void testPrepareForDiffing_OneCompressedEntry_Changed() throws IOException {
    byte[] oldBytes = UnitTestZipArchive.makeTestZip(ImmutableList.of(ENTRY_LEVEL_6));
    File oldFile = store(oldBytes);
    byte[] newBytes = UnitTestZipArchive.makeTestZip(ImmutableList.of(ENTRY_LEVEL_9));
    File newFile = store(newBytes);
    PreDiffPlan plan;
    try (ByteSource oldBlob = ByteSource.fromFile(oldFile);
        ByteSource newBlob = ByteSource.fromFile(newFile)) {
      PreDiffExecutor executor =
          new PreDiffExecutor.Builder()
              .readingOriginalFiles(oldBlob, newBlob)
              .writingDeltaFriendlyFiles(deltaFriendlyOldFile, deltaFriendlyNewFile)
              .build();
      plan = executor.prepareForDiffing();
    }
    assertThat(plan).isNotNull();
    // The plan should be to uncompress the data in both the old and new files.
    assertThat(plan.getOldFileUncompressionPlan()).hasSize(1);
    assertThat(plan.getNewFileUncompressionPlan()).hasSize(1);
    assertThat(plan.getDeltaFriendlyNewFileRecompressionPlan()).hasSize(1);
    // The delta-friendly files should be larger than the originals.
    assertThat(oldFile.length()).isLessThan(deltaFriendlyOldFile.length());
    assertThat(newFile.length()).isLessThan(deltaFriendlyNewFile.length());

    assertThat(plan.getPreDiffPlanEntries()).hasSize(1);
    assertThat(plan.getPreDiffPlanEntries().get(0).deltaFormat()).isEqualTo(DeltaFormat.BSDIFF);
    assertThat(plan.getPreDiffPlanEntries().get(0).deltaFormatExplanation())
        .isEqualTo(DeltaFormatExplanation.UNCHANGED);

    // Nitty-gritty, assert that the file content is exactly what is expected.
    // 1. Find the entry in the old file.
    // 2. Create a buffer to hold the expected data.
    // 3. Copy all the file data that PRECEDES the compressed data into the buffer.
    // 4. Copy the UNCOMPRESSED data from the unit test object into the buffer.
    // 5. Copy all the file data the FOLLOWS the compressed data into the buffer.
    // This should be exactly what is produced. Note that this is not a valid ZIP archive, as the
    // offsets and lengths in the zip metadata are no longer tied to the actual data. This is
    // normal and expected, since the delta-friendly file is not actually an archive anymore.
    { // Scoping block for sanity
      MinimalZipEntry oldEntry = findEntry(oldFile, ENTRY_LEVEL_6.path);
      ByteArrayOutputStream expectedDeltaFriendlyOldFileBytes = new ByteArrayOutputStream();
      expectedDeltaFriendlyOldFileBytes.write(
          oldBytes, 0, (int) oldEntry.compressedDataRange().offset());
      expectedDeltaFriendlyOldFileBytes.write(ENTRY_LEVEL_6.getUncompressedBinaryContent());
      int oldRemainderOffset =
          (int) (oldEntry.compressedDataRange().offset() + oldEntry.compressedDataRange().length());
      int oldRemainderLength = oldBytes.length - oldRemainderOffset;
      expectedDeltaFriendlyOldFileBytes.write(oldBytes, oldRemainderOffset, oldRemainderLength);
      assertEqualBytes(
          expectedDeltaFriendlyOldFileBytes.toByteArray(), deltaFriendlyOldFile);
    }

    // Now do the same for the new file and new entry
    { // Scoping block for sanity
      MinimalZipEntry newEntry = findEntry(newFile, ENTRY_LEVEL_9.path);
      ByteArrayOutputStream expectedDeltaFriendlyNewFileBytes = new ByteArrayOutputStream();
      expectedDeltaFriendlyNewFileBytes.write(
          newBytes, 0, (int) newEntry.compressedDataRange().offset());
      expectedDeltaFriendlyNewFileBytes.write(ENTRY_LEVEL_9.getUncompressedBinaryContent());
      int newRemainderOffset =
          (int) (newEntry.compressedDataRange().offset() + newEntry.compressedDataRange().length());
      int newRemainderLength = newBytes.length - newRemainderOffset;
      expectedDeltaFriendlyNewFileBytes.write(newBytes, newRemainderOffset, newRemainderLength);
      assertEqualBytes(
          expectedDeltaFriendlyNewFileBytes.toByteArray(), deltaFriendlyNewFile);
    }
  }

  @Test
  public void testPrepareForDiffing_OneCompressedEntry_Changed_Limited() throws IOException {
    // Like above, but this time limited by a TotalRecompressionLimiter that will prevent the
    // uncompression of the resources.
    byte[] oldBytes = UnitTestZipArchive.makeTestZip(ImmutableList.of(ENTRY_LEVEL_6));
    File oldFile = store(oldBytes);
    byte[] newBytes = UnitTestZipArchive.makeTestZip(ImmutableList.of(ENTRY_LEVEL_9));
    File newFile = store(newBytes);
    TotalRecompressionLimiter limiter = new TotalRecompressionLimiter(1); // 1 byte limitation
    PreDiffPlan plan;
    try (ByteSource oldBlob = ByteSource.fromFile(oldFile);
        ByteSource newBlob = ByteSource.fromFile(newFile)) {
      PreDiffExecutor executor =
          new PreDiffExecutor.Builder()
              .readingOriginalFiles(oldBlob, newBlob)
              .writingDeltaFriendlyFiles(deltaFriendlyOldFile, deltaFriendlyNewFile)
              .addPreDiffPlanEntryModifier(limiter)
              .build();
      plan = executor.prepareForDiffing();
    }
    assertThat(plan).isNotNull();
    // The plan should be to leave everything alone because of the limiter
    assertThat(plan.getOldFileUncompressionPlan()).isEmpty();
    assertThat(plan.getNewFileUncompressionPlan()).isEmpty();
    assertThat(plan.getDeltaFriendlyNewFileRecompressionPlan()).isEmpty();
    // Because nothing has changed, the delta-friendly files should be exact matches for the
    // original files.
    assertEqualBytes(oldBytes, deltaFriendlyOldFile);
    assertEqualBytes(newBytes, deltaFriendlyNewFile);
  }

  private static void assertEqualBytes(byte[] expected, TempBlob actual) throws IOException {
    byte[] deltaFriendlyContent = new byte[(int) actual.length()];
    actual.asByteSource().openStream().read(deltaFriendlyContent);
    assertThat(deltaFriendlyContent).isEqualTo(expected);
  }
}
