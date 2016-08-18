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

package com.google.archivepatcher.generator;

import com.google.archivepatcher.shared.UnitTestZipArchive;
import com.google.archivepatcher.shared.UnitTestZipEntry;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import org.junit.After;
import org.junit.Assert;
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
  private File deltaFriendlyOldFile;
  private File deltaFriendlyNewFile;

  @Before
  public void setup() throws IOException {
    tempFilesCreated = new LinkedList<File>();
    deltaFriendlyOldFile = newTempFile();
    deltaFriendlyNewFile = newTempFile();
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
    Assert.fail("path not found: " + path);
    return null; // Never executed
  }

  private byte[] readFile(File file) throws IOException {
    byte[] result = new byte[(int) file.length()];
    try (FileInputStream fis = new FileInputStream(file);
        DataInputStream dis = new DataInputStream(fis)) {
      dis.readFully(result);
    }
    return result;
  }

  private void assertFileEquals(File file1, File file2) throws IOException {
    Assert.assertEquals(file1.length(), file2.length());
    byte[] content1 = readFile(file1);
    byte[] content2 = readFile(file2);
    Assert.assertArrayEquals(content1, content2);
  }

  @Test
  public void testPrepareForDiffing_OneCompressedEntry_Unchanged() throws IOException {
    byte[] bytes = UnitTestZipArchive.makeTestZip(Collections.singletonList(ENTRY_LEVEL_6));
    File oldFile = store(bytes);
    File newFile = store(bytes);
    PreDiffExecutor executor =
        new PreDiffExecutor.Builder()
            .readingOriginalFiles(oldFile, newFile)
            .writingDeltaFriendlyFiles(deltaFriendlyOldFile, deltaFriendlyNewFile)
            .build();
    PreDiffPlan plan = executor.prepareForDiffing();
    Assert.assertNotNull(plan);
    // The plan should be to leave everything alone because there is no change.
    Assert.assertTrue(plan.getOldFileUncompressionPlan().isEmpty());
    Assert.assertTrue(plan.getNewFileUncompressionPlan().isEmpty());
    Assert.assertTrue(plan.getDeltaFriendlyNewFileRecompressionPlan().isEmpty());
    // Because nothing has changed, the delta-friendly files should be exact matches for the
    // original files.
    assertFileEquals(oldFile, deltaFriendlyOldFile);
    assertFileEquals(newFile, deltaFriendlyNewFile);
  }

  @Test
  public void testPrepareForDiffing_OneCompressedEntry_Changed() throws IOException {
    byte[] oldBytes = UnitTestZipArchive.makeTestZip(Collections.singletonList(ENTRY_LEVEL_6));
    File oldFile = store(oldBytes);
    byte[] newBytes = UnitTestZipArchive.makeTestZip(Collections.singletonList(ENTRY_LEVEL_9));
    File newFile = store(newBytes);
    PreDiffExecutor executor =
        new PreDiffExecutor.Builder()
            .readingOriginalFiles(oldFile, newFile)
            .writingDeltaFriendlyFiles(deltaFriendlyOldFile, deltaFriendlyNewFile)
            .build();
    PreDiffPlan plan = executor.prepareForDiffing();
    Assert.assertNotNull(plan);
    // The plan should be to uncompress the data in both the old and new files.
    Assert.assertEquals(1, plan.getOldFileUncompressionPlan().size());
    Assert.assertEquals(1, plan.getNewFileUncompressionPlan().size());
    Assert.assertEquals(1, plan.getDeltaFriendlyNewFileRecompressionPlan().size());
    // The delta-friendly files should be larger than the originals.
    Assert.assertTrue(oldFile.length() < deltaFriendlyOldFile.length());
    Assert.assertTrue(newFile.length() < deltaFriendlyNewFile.length());

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
          oldBytes, 0, (int) oldEntry.getFileOffsetOfCompressedData());
      expectedDeltaFriendlyOldFileBytes.write(ENTRY_LEVEL_6.getUncompressedBinaryContent());
      int oldRemainderOffset =
          (int) (oldEntry.getFileOffsetOfCompressedData() + oldEntry.getCompressedSize());
      int oldRemainderLength = oldBytes.length - oldRemainderOffset;
      expectedDeltaFriendlyOldFileBytes.write(oldBytes, oldRemainderOffset, oldRemainderLength);
      byte[] expectedOld = expectedDeltaFriendlyOldFileBytes.toByteArray();
      byte[] actualOld = readFile(deltaFriendlyOldFile);
      Assert.assertArrayEquals(expectedOld, actualOld);
    }

    // Now do the same for the new file and new entry
    { // Scoping block for sanity
      MinimalZipEntry newEntry = findEntry(newFile, ENTRY_LEVEL_9.path);
      ByteArrayOutputStream expectedDeltaFriendlyNewFileBytes = new ByteArrayOutputStream();
      expectedDeltaFriendlyNewFileBytes.write(
          newBytes, 0, (int) newEntry.getFileOffsetOfCompressedData());
      expectedDeltaFriendlyNewFileBytes.write(ENTRY_LEVEL_9.getUncompressedBinaryContent());
      int newRemainderOffset =
          (int) (newEntry.getFileOffsetOfCompressedData() + newEntry.getCompressedSize());
      int newRemainderLength = newBytes.length - newRemainderOffset;
      expectedDeltaFriendlyNewFileBytes.write(newBytes, newRemainderOffset, newRemainderLength);
      byte[] expectedNew = expectedDeltaFriendlyNewFileBytes.toByteArray();
      byte[] actualNew = readFile(deltaFriendlyNewFile);
      Assert.assertArrayEquals(expectedNew, actualNew);
    }
  }

  @Test
  public void testPrepareForDiffing_OneCompressedEntry_Changed_Limited() throws IOException {
    // Like above, but this time limited by a TotalRecompressionLimiter that will prevent the
    // uncompression of the resources.
    byte[] oldBytes = UnitTestZipArchive.makeTestZip(Collections.singletonList(ENTRY_LEVEL_6));
    File oldFile = store(oldBytes);
    byte[] newBytes = UnitTestZipArchive.makeTestZip(Collections.singletonList(ENTRY_LEVEL_9));
    File newFile = store(newBytes);
    TotalRecompressionLimiter limiter = new TotalRecompressionLimiter(1); // 1 byte limitation
    PreDiffExecutor executor =
        new PreDiffExecutor.Builder()
            .readingOriginalFiles(oldFile, newFile)
            .writingDeltaFriendlyFiles(deltaFriendlyOldFile, deltaFriendlyNewFile)
            .withRecommendationModifier(limiter)
            .build();
    PreDiffPlan plan = executor.prepareForDiffing();
    Assert.assertNotNull(plan);
    // The plan should be to leave everything alone because of the limiter
    Assert.assertTrue(plan.getOldFileUncompressionPlan().isEmpty());
    Assert.assertTrue(plan.getNewFileUncompressionPlan().isEmpty());
    Assert.assertTrue(plan.getDeltaFriendlyNewFileRecompressionPlan().isEmpty());
    // Because nothing has changed, the delta-friendly files should be exact matches for the
    // original files.
    assertFileEquals(oldFile, deltaFriendlyOldFile);
    assertFileEquals(newFile, deltaFriendlyNewFile);
  }
}
