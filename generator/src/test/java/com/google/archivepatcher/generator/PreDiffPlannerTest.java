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

import com.google.archivepatcher.generator.DefaultDeflateCompressionDiviner.DivinationResult;
import com.google.archivepatcher.shared.DefaultDeflateCompatibilityWindow;
import com.google.archivepatcher.shared.JreDeflateParameters;
import com.google.archivepatcher.shared.RandomAccessFileInputStream;
import com.google.archivepatcher.shared.TypedRange;
import com.google.archivepatcher.shared.UnitTestZipArchive;
import com.google.archivepatcher.shared.UnitTestZipEntry;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link PreDiffPlanner}.
 */
@RunWith(JUnit4.class)
@SuppressWarnings("javadoc")
public class PreDiffPlannerTest {

  // All the A and B entries consist of a chunk of text followed by a standard corpus of text from
  // the DefaultDeflateCompatibilityDiviner that ensures the tests will be able to discriminate
  // between any compression level. Without this additional corpus text, multiple compression levels
  // can match the entry and the unit tests would not be accurate.
  private static final UnitTestZipEntry ENTRY_A_LEVEL_6 =
      UnitTestZipArchive.makeUnitTestZipEntry("/path A", 6, "entry A", null);
  private static final UnitTestZipEntry ENTRY_A_LEVEL_9 =
      UnitTestZipArchive.makeUnitTestZipEntry("/path A", 9, "entry A", null);
  private static final UnitTestZipEntry ENTRY_A_STORED =
      UnitTestZipArchive.makeUnitTestZipEntry("/path A", 0, "entry A", null);
  private static final UnitTestZipEntry ENTRY_B_LEVEL_6 =
      UnitTestZipArchive.makeUnitTestZipEntry("/path B", 6, "entry B", null);
  private static final UnitTestZipEntry ENTRY_B_LEVEL_9 =
      UnitTestZipArchive.makeUnitTestZipEntry("/path B", 9, "entry B", null);

  /**
   * Entry C1 is a small entry WITHOUT the standard corpus of text from
   * {@link DefaultDeflateCompatibilityWindow} appended. It has exactly the same compressed length
   * as {@link #FIXED_LENGTH_ENTRY_C2_LEVEL_6}, and can be used to test the byte-matching logic in
   * the code when the compressed lengths are identical.
   */
  private static final UnitTestZipEntry FIXED_LENGTH_ENTRY_C1_LEVEL_6 =
      new UnitTestZipEntry("/path C", 6, "qqqqqqqqqqqqqqqqqqqqqqqqqqqq", null);

  /**
   * Entry C2 is a small entry WITHOUT the standard corpus of text from
   * {@link DefaultDeflateCompatibilityWindow} appended. It has exactly the same compressed length
   * as {@link #FIXED_LENGTH_ENTRY_C1_LEVEL_6}, and can be used to test the byte-matching logic in
   * the code when the compressed lengths are identical.
   */
  private static final UnitTestZipEntry FIXED_LENGTH_ENTRY_C2_LEVEL_6 =
      new UnitTestZipEntry("/path C", 6, "rrrrrrrrrrrrrrrrrrrrrrrrrrrr", null);

  // The "shadow" entries are exact copies of ENTRY_A_* but have a different path. These are used
  // for the detection of renames that don't involve modification (i.e., the uncompressed CRC32 is
  // exactly the same as the ENTRY_A_* entries)
  private static final UnitTestZipEntry SHADOW_ENTRY_A_LEVEL_1 =
      UnitTestZipArchive.makeUnitTestZipEntry("/uncompressed data same as A", 1, "entry A", null);
  private static final UnitTestZipEntry SHADOW_ENTRY_A_LEVEL_6 =
      UnitTestZipArchive.makeUnitTestZipEntry("/same as A level 6", 6, "entry A", null);
  private static final UnitTestZipEntry SHADOW_ENTRY_A_LEVEL_9 =
      UnitTestZipArchive.makeUnitTestZipEntry("/same as A level 9", 9, "entry A", null);
  private static final UnitTestZipEntry SHADOW_ENTRY_A_STORED =
      UnitTestZipArchive.makeUnitTestZipEntry("/same as A stored", 0, "entry A", null);

  private List<File> tempFilesCreated;
  private Map<File, Map<ByteArrayHolder, MinimalZipEntry>> entriesByPathByTempFile;

  @Before
  public void setup() {
    tempFilesCreated = new LinkedList<File>();
    entriesByPathByTempFile = new HashMap<File, Map<ByteArrayHolder, MinimalZipEntry>>();
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
   * Stores the specified bytes to disk in a temp file, returns the temp file and caches the zip
   * entries for the file for use in later code.
   * @param data the bytes to store, expected to be a valid zip file
   * @throws IOException if it fails
   */
  private File storeAndMapArchive(byte[] data) throws IOException {
    File file = File.createTempFile("pdpt", "zip");
    tempFilesCreated.add(file);
    file.deleteOnExit();
    FileOutputStream out = new FileOutputStream(file);
    out.write(data);
    out.flush();
    out.close();
    Map<ByteArrayHolder, MinimalZipEntry> entriesByPath = new HashMap<>();
    for (MinimalZipEntry zipEntry : MinimalZipArchive.listEntries(file)) {
      ByteArrayHolder key = new ByteArrayHolder(zipEntry.getFileNameBytes());
      entriesByPath.put(key, zipEntry);
    }
    entriesByPathByTempFile.put(file, entriesByPath);
    return file;
  }

  /**
   * Finds a unit test entry in the specified temp file.
   * @param tempFile the archive to search within
   * @param unitTestEntry the unit test entry to look up
   * @return the {@link MinimalZipEntry} corresponding to the unit test entry
   */
  private MinimalZipEntry findEntry(File tempFile, UnitTestZipEntry unitTestEntry) {
    Map<ByteArrayHolder, MinimalZipEntry> subMap = entriesByPathByTempFile.get(tempFile);
    Assert.assertNotNull("temp file not mapped", subMap);
    ByteArrayHolder key;
    try {
      key = new ByteArrayHolder(unitTestEntry.path.getBytes("UTF8"));
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    }
    return subMap.get(key);
  }

  /**
   * Finds the {@link TypedRange} corresponding to the compressed data for the specified unit test
   * entry in the specified temp file.
   * @param tempFile the archive to search within
   * @param unitTestEntry the unit test entry to look up
   * @return the {@link TypedRange} for the unit test entry's compressed data
   */
  private TypedRange<Void> findRangeWithoutParams(File tempFile, UnitTestZipEntry unitTestEntry) {
    MinimalZipEntry found = findEntry(tempFile, unitTestEntry);
    Assert.assertNotNull("entry not found in temp file", found);
    return new TypedRange<Void>(
        found.getFileOffsetOfCompressedData(), found.getCompressedSize(), null);
  }

  /**
   * Finds the {@link TypedRange} corresponding to the compressed data for the specified unit test
   * entry in the specified temp file.
   * @param tempFile the archive to search within
   * @param unitTestEntry the unit test entry to look up
   * @return the {@link TypedRange} for the unit test entry's compressed data
   */
  private TypedRange<JreDeflateParameters> findRangeWithParams(
      File tempFile, UnitTestZipEntry unitTestEntry) {
    MinimalZipEntry found = findEntry(tempFile, unitTestEntry);
    Assert.assertNotNull("entry not found in temp file", found);
    return new TypedRange<JreDeflateParameters>(
        found.getFileOffsetOfCompressedData(),
        found.getCompressedSize(),
        JreDeflateParameters.of(unitTestEntry.level, 0, true));
  }

  /**
   * Deliberately introduce an error into the specified entry. This will make the entry impossible
   * to divine the settings for, because it is broken.
   * @param tempFile the archive to search within
   * @param unitTestEntry the unit test entry to deliberately corrupt
   */
  private void corruptEntryData(File tempFile, UnitTestZipEntry unitTestEntry) throws IOException {
    TypedRange<Void> range = findRangeWithoutParams(tempFile, unitTestEntry);
    Assert.assertTrue("range too short to corrupt with 'junk'", range.getLength() >= 4);
    try (RandomAccessFile raf = new RandomAccessFile(tempFile, "rw")) {
      raf.seek(range.getOffset());
      raf.write("junk".getBytes("UTF8"));
    }
  }

  /**
   * Deliberately garble the compression method in the specified entry such that it is no longer
   * deflate.
   * @param tempFile the archive to search within
   * @param unitTestEntry the unit test entry to deliberately corrupt
   */
  private void corruptCompressionMethod(File tempFile, UnitTestZipEntry unitTestEntry)
      throws IOException {
    long centralDirectoryRecordOffset = -1;
    try (RandomAccessFileInputStream rafis = new RandomAccessFileInputStream(tempFile)) {
      long startOfEocd = MinimalZipParser.locateStartOfEocd(rafis, 32768);
      rafis.setRange(startOfEocd, tempFile.length() - startOfEocd);
      MinimalCentralDirectoryMetadata centralDirectoryMetadata = MinimalZipParser.parseEocd(rafis);
      int numEntries = centralDirectoryMetadata.getNumEntriesInCentralDirectory();
      rafis.setRange(
          centralDirectoryMetadata.getOffsetOfCentralDirectory(),
          centralDirectoryMetadata.getLengthOfCentralDirectory());
      for (int x = 0; x < numEntries; x++) {
        long recordStartOffset = rafis.getPosition();
        MinimalZipEntry candidate = MinimalZipParser.parseCentralDirectoryEntry(rafis);
        if (candidate.getFileName().equals(unitTestEntry.path)) {
          // Located! Track offset and bail out.
          centralDirectoryRecordOffset = recordStartOffset;
          x = numEntries;
        }
      }
    }

    Assert.assertNotEquals("Entry not found", -1L, centralDirectoryRecordOffset);
    try (RandomAccessFile raf = new RandomAccessFile(tempFile, "rw")) {
      // compression method is a 2 byte field stored 10 bytes into the record
      raf.seek(centralDirectoryRecordOffset + 10);
      raf.write(7);
      raf.write(7);
    }
  }

  private PreDiffPlan invokeGeneratePreDiffPlan(
      File oldFile, File newFile, RecommendationModifier... recommendationModifiers)
      throws IOException {
    Map<ByteArrayHolder, MinimalZipEntry> originalOldArchiveZipEntriesByPath =
        new LinkedHashMap<ByteArrayHolder, MinimalZipEntry>();
    Map<ByteArrayHolder, MinimalZipEntry> originalNewArchiveZipEntriesByPath =
        new LinkedHashMap<ByteArrayHolder, MinimalZipEntry>();
    Map<ByteArrayHolder, JreDeflateParameters> originalNewArchiveJreDeflateParametersByPath =
        new LinkedHashMap<ByteArrayHolder, JreDeflateParameters>();

    for (MinimalZipEntry zipEntry : MinimalZipArchive.listEntries(oldFile)) {
      ByteArrayHolder key = new ByteArrayHolder(zipEntry.getFileNameBytes());
      originalOldArchiveZipEntriesByPath.put(key, zipEntry);
    }

    DefaultDeflateCompressionDiviner diviner = new DefaultDeflateCompressionDiviner();
    for (DivinationResult divinationResult : diviner.divineDeflateParameters(newFile)) {
      ByteArrayHolder key = new ByteArrayHolder(divinationResult.minimalZipEntry.getFileNameBytes());
      originalNewArchiveZipEntriesByPath.put(key, divinationResult.minimalZipEntry);
      originalNewArchiveJreDeflateParametersByPath.put(key, divinationResult.divinedParameters);
    }

    PreDiffPlanner preDiffPlanner =
        new PreDiffPlanner(
            oldFile,
            originalOldArchiveZipEntriesByPath,
            newFile,
            originalNewArchiveZipEntriesByPath,
            originalNewArchiveJreDeflateParametersByPath,
            recommendationModifiers);
    return preDiffPlanner.generatePreDiffPlan();
  }

  private void checkRecommendation(PreDiffPlan plan, QualifiedRecommendation... expected) {
    Assert.assertNotNull(plan.getQualifiedRecommendations());
    Assert.assertEquals(expected.length, plan.getQualifiedRecommendations().size());
    for (int x = 0; x < expected.length; x++) {
      QualifiedRecommendation actual = plan.getQualifiedRecommendations().get(x);
      Assert.assertEquals(
          expected[x].getOldEntry().getFileName(), actual.getOldEntry().getFileName());
      Assert.assertEquals(
          expected[x].getNewEntry().getFileName(), actual.getNewEntry().getFileName());
      Assert.assertEquals(expected[x].getRecommendation(), actual.getRecommendation());
      Assert.assertEquals(expected[x].getReason(), actual.getReason());
    }
  }

  @Test
  public void testGeneratePreDiffPlan_OneCompressedEntry_Unchanged() throws IOException {
    byte[] bytes = UnitTestZipArchive.makeTestZip(Collections.singletonList(ENTRY_A_LEVEL_6));
    File oldFile = storeAndMapArchive(bytes);
    File newFile = storeAndMapArchive(bytes);
    PreDiffPlan plan = invokeGeneratePreDiffPlan(oldFile, newFile);
    Assert.assertNotNull(plan);
    // The plan should be to leave the entry alone in both the old and new archives (empty plans).
    Assert.assertTrue(plan.getOldFileUncompressionPlan().isEmpty());
    Assert.assertTrue(plan.getNewFileUncompressionPlan().isEmpty());
    checkRecommendation(plan, new QualifiedRecommendation(
        findEntry(oldFile, ENTRY_A_LEVEL_6),
        findEntry(newFile, ENTRY_A_LEVEL_6),
        Recommendation.UNCOMPRESS_NEITHER,
        RecommendationReason.COMPRESSED_BYTES_IDENTICAL));
  }

  @Test
  public void testGeneratePreDiffPlan_OneCompressedEntry_LengthsChanged() throws IOException {
    // Test detection of compressed entry differences based on length mismatch.
    byte[] oldBytes = UnitTestZipArchive.makeTestZip(Collections.singletonList(ENTRY_A_LEVEL_6));
    byte[] newBytes = UnitTestZipArchive.makeTestZip(Collections.singletonList(ENTRY_A_LEVEL_9));
    File oldFile = storeAndMapArchive(oldBytes);
    File newFile = storeAndMapArchive(newBytes);
    PreDiffPlan plan = invokeGeneratePreDiffPlan(oldFile, newFile);
    Assert.assertNotNull(plan);
    // The plan should be to uncompress the entry in both the old and new archives.
    Assert.assertEquals(1, plan.getOldFileUncompressionPlan().size());
    Assert.assertEquals(
        findRangeWithoutParams(oldFile, ENTRY_A_LEVEL_6),
        plan.getOldFileUncompressionPlan().get(0));
    Assert.assertEquals(1, plan.getNewFileUncompressionPlan().size());
    Assert.assertEquals(
        findRangeWithParams(newFile, ENTRY_A_LEVEL_9), plan.getNewFileUncompressionPlan().get(0));
    checkRecommendation(plan, new QualifiedRecommendation(
        findEntry(oldFile, ENTRY_A_LEVEL_6),
        findEntry(newFile, ENTRY_A_LEVEL_9),
        Recommendation.UNCOMPRESS_BOTH,
        RecommendationReason.COMPRESSED_BYTES_CHANGED));
  }

  @Test
  public void testGeneratePreDiffPlan_OneCompressedEntry_BytesChanged() throws IOException {
    // Test detection of compressed entry differences based on binary content mismatch where the
    // compressed lengths are exactly the same - i.e., force a byte-by-byte comparison of the
    // compressed data in the two entries.
    byte[] oldBytes =
        UnitTestZipArchive.makeTestZip(Collections.singletonList(FIXED_LENGTH_ENTRY_C1_LEVEL_6));
    byte[] newBytes =
        UnitTestZipArchive.makeTestZip(Collections.singletonList(FIXED_LENGTH_ENTRY_C2_LEVEL_6));
    File oldFile = storeAndMapArchive(oldBytes);
    File newFile = storeAndMapArchive(newBytes);
    PreDiffPlan plan = invokeGeneratePreDiffPlan(oldFile, newFile);
    Assert.assertNotNull(plan);
    // The plan should be to uncompress the entry in both the old and new archives.
    Assert.assertEquals(1, plan.getOldFileUncompressionPlan().size());
    Assert.assertEquals(1, plan.getNewFileUncompressionPlan().size());
    Assert.assertEquals(
        findRangeWithoutParams(oldFile, FIXED_LENGTH_ENTRY_C1_LEVEL_6),
        plan.getOldFileUncompressionPlan().get(0));
    Assert.assertEquals(
        findRangeWithParams(newFile, FIXED_LENGTH_ENTRY_C2_LEVEL_6),
        plan.getNewFileUncompressionPlan().get(0));
    checkRecommendation(plan, new QualifiedRecommendation(
        findEntry(oldFile, FIXED_LENGTH_ENTRY_C1_LEVEL_6),
        findEntry(newFile, FIXED_LENGTH_ENTRY_C2_LEVEL_6),
        Recommendation.UNCOMPRESS_BOTH,
        RecommendationReason.COMPRESSED_BYTES_CHANGED));
  }

  @Test
  public void testGeneratePreDiffPlan_OneUncompressedEntry() throws IOException {
    // Test with uncompressed old and new. It doesn't matter whether the bytes are changed or
    // unchanged in this case.
    byte[] oldBytes = UnitTestZipArchive.makeTestZip(Collections.singletonList(ENTRY_A_STORED));
    byte[] newBytes = UnitTestZipArchive.makeTestZip(Collections.singletonList(ENTRY_A_STORED));
    File oldFile = storeAndMapArchive(oldBytes);
    File newFile = storeAndMapArchive(newBytes);
    PreDiffPlan plan = invokeGeneratePreDiffPlan(oldFile, newFile);
    Assert.assertNotNull(plan);
    // The plan should be to do nothing because both entries are already uncompressed
    Assert.assertTrue(plan.getOldFileUncompressionPlan().isEmpty());
    Assert.assertTrue(plan.getNewFileUncompressionPlan().isEmpty());
    checkRecommendation(plan, new QualifiedRecommendation(
        findEntry(oldFile, ENTRY_A_STORED),
        findEntry(newFile, ENTRY_A_STORED),
        Recommendation.UNCOMPRESS_NEITHER,
        RecommendationReason.BOTH_ENTRIES_UNCOMPRESSED));
  }

  @Test
  public void testGeneratePreDiffPlan_OneEntry_CompressedToUncompressed() throws IOException {
    // Test the migration of an entry from compressed (old archive) to uncompressed (new archive).
    byte[] oldBytes = UnitTestZipArchive.makeTestZip(Collections.singletonList(ENTRY_A_LEVEL_9));
    byte[] newBytes = UnitTestZipArchive.makeTestZip(Collections.singletonList(ENTRY_A_STORED));
    File oldFile = storeAndMapArchive(oldBytes);
    File newFile = storeAndMapArchive(newBytes);
    PreDiffPlan plan = invokeGeneratePreDiffPlan(oldFile, newFile);
    Assert.assertNotNull(plan);
    // The plan should be to uncompress the entry in the old archive and do nothing in the new
    // archive (empty plan)
    Assert.assertEquals(1, plan.getOldFileUncompressionPlan().size());
    Assert.assertEquals(
        findRangeWithoutParams(oldFile, ENTRY_A_LEVEL_9),
        plan.getOldFileUncompressionPlan().get(0));
    Assert.assertTrue(plan.getNewFileUncompressionPlan().isEmpty());
    checkRecommendation(plan, new QualifiedRecommendation(
        findEntry(oldFile, ENTRY_A_LEVEL_9),
        findEntry(newFile, ENTRY_A_STORED),
        Recommendation.UNCOMPRESS_OLD,
        RecommendationReason.COMPRESSED_CHANGED_TO_UNCOMPRESSED));
  }

  @Test
  public void testGeneratePreDiffPlan_OneEntry_UncompressedToCompressed() throws IOException {
    // Test the migration of an entry from uncompressed (old archive) to compressed (new archive).
    byte[] oldBytes = UnitTestZipArchive.makeTestZip(Collections.singletonList(ENTRY_A_STORED));
    byte[] newBytes = UnitTestZipArchive.makeTestZip(Collections.singletonList(ENTRY_A_LEVEL_6));
    File oldFile = storeAndMapArchive(oldBytes);
    File newFile = storeAndMapArchive(newBytes);
    PreDiffPlan plan = invokeGeneratePreDiffPlan(oldFile, newFile);
    Assert.assertNotNull(plan);
    // The plan should be to do nothing in the old archive (empty plan) and uncompress the entry in
    // the new archive
    Assert.assertTrue(plan.getOldFileUncompressionPlan().isEmpty());
    Assert.assertEquals(1, plan.getNewFileUncompressionPlan().size());
    Assert.assertEquals(
        findRangeWithParams(newFile, ENTRY_A_LEVEL_6), plan.getNewFileUncompressionPlan().get(0));
    checkRecommendation(plan, new QualifiedRecommendation(
        findEntry(oldFile, ENTRY_A_STORED),
        findEntry(newFile, ENTRY_A_LEVEL_6),
        Recommendation.UNCOMPRESS_NEW,
        RecommendationReason.UNCOMPRESSED_CHANGED_TO_COMPRESSED));
  }

  @Test
  public void testGeneratePreDiffPlan_OneEntry_UncompressedToUndivinable() throws IOException {
    // Test the migration of an entry from uncompressed (old archive) to compressed (new archive),
    // but make the new entry un-divinable and therefore un-recompressible.
    byte[] oldBytes = UnitTestZipArchive.makeTestZip(Collections.singletonList(ENTRY_A_STORED));
    byte[] newBytes = UnitTestZipArchive.makeTestZip(Collections.singletonList(ENTRY_A_LEVEL_6));
    File oldFile = storeAndMapArchive(oldBytes);
    File newFile = storeAndMapArchive(newBytes);
    // Deliberately break the entry in the new file so that it will not be divinable
    corruptEntryData(newFile, ENTRY_A_LEVEL_6);
    PreDiffPlan plan = invokeGeneratePreDiffPlan(oldFile, newFile);
    Assert.assertNotNull(plan);
    // The plan WOULD be to do nothing in the old archive (empty plan) and uncompress the entry in
    // the new archive, but because the new entry is un-divinable it cannot be recompressed and so
    // the plan for the new archive should be empty as well.
    Assert.assertTrue(plan.getOldFileUncompressionPlan().isEmpty());
    Assert.assertTrue(plan.getNewFileUncompressionPlan().isEmpty());
    checkRecommendation(
        plan,
        new QualifiedRecommendation(
            findEntry(oldFile, ENTRY_A_STORED),
            findEntry(newFile, ENTRY_A_LEVEL_6),
            Recommendation.UNCOMPRESS_NEITHER,
            RecommendationReason.DEFLATE_UNSUITABLE));
  }

  @Test
  public void testGeneratePreDiffPlan_OneEntry_OldUncompressed_NewNonDeflate() throws IOException {
    // Test the case where the entry is compressed with something other than deflate in the new
    // archive; it is thus not reproducible, not divinable, and therefore cannot be uncompressed.
    byte[] oldBytes = UnitTestZipArchive.makeTestZip(Collections.singletonList(ENTRY_A_STORED));
    byte[] newBytes = UnitTestZipArchive.makeTestZip(Collections.singletonList(ENTRY_A_LEVEL_9));
    File oldFile = storeAndMapArchive(oldBytes);
    File newFile = storeAndMapArchive(newBytes);
    corruptCompressionMethod(newFile, ENTRY_A_LEVEL_9);
    PreDiffPlan plan = invokeGeneratePreDiffPlan(oldFile, newFile);
    Assert.assertNotNull(plan);
    // The plan should be to do nothing (empty plans) because the the entry in the old archive is
    // already uncompressed and the entry in the new archive is not compressed with deflate (i.e.,
    // cannot be recompressed so cannot be touched).
    Assert.assertTrue(plan.getOldFileUncompressionPlan().isEmpty());
    Assert.assertTrue(plan.getNewFileUncompressionPlan().isEmpty());
    checkRecommendation(plan, new QualifiedRecommendation(
        findEntry(oldFile, ENTRY_A_STORED),
        findEntry(newFile, ENTRY_A_LEVEL_9),
        Recommendation.UNCOMPRESS_NEITHER,
        RecommendationReason.UNSUITABLE));
  }

  @Test
  public void testGeneratePreDiffPlan_OneEntry_OldNonDeflate_NewUncompressed() throws IOException {
    // Test the case where the entry is compressed with something other than deflate in the old
    // archive; it can't be uncompressed, so there's no point in modifying the new entry either.
    byte[] oldBytes = UnitTestZipArchive.makeTestZip(Collections.singletonList(ENTRY_A_LEVEL_9));
    byte[] newBytes = UnitTestZipArchive.makeTestZip(Collections.singletonList(ENTRY_A_STORED));
    File oldFile = storeAndMapArchive(oldBytes);
    File newFile = storeAndMapArchive(newBytes);
    corruptCompressionMethod(oldFile, ENTRY_A_LEVEL_9);
    PreDiffPlan plan = invokeGeneratePreDiffPlan(oldFile, newFile);
    Assert.assertNotNull(plan);
    // The plan should be to do nothing (empty plans) because the the entry in the old archive is
    // not compressed with deflate, so there is no point in trying to do anything at all.
    Assert.assertTrue(plan.getOldFileUncompressionPlan().isEmpty());
    Assert.assertTrue(plan.getNewFileUncompressionPlan().isEmpty());
    checkRecommendation(plan, new QualifiedRecommendation(
        findEntry(oldFile, ENTRY_A_LEVEL_9),
        findEntry(newFile, ENTRY_A_STORED),
        Recommendation.UNCOMPRESS_NEITHER,
        RecommendationReason.UNSUITABLE));
  }

  @Test
  public void testGeneratePreDiffPlan_OneEntry_BothNonDeflate() throws IOException {
    // Test the case where the entry is compressed with something other than deflate; it is thus
    // not reproducible, not divinable, and therefore cannot be uncompressed.
    byte[] oldBytes = UnitTestZipArchive.makeTestZip(Collections.singletonList(ENTRY_A_LEVEL_6));
    byte[] newBytes = UnitTestZipArchive.makeTestZip(Collections.singletonList(ENTRY_A_LEVEL_9));
    File oldFile = storeAndMapArchive(oldBytes);
    File newFile = storeAndMapArchive(newBytes);
    corruptCompressionMethod(oldFile, ENTRY_A_LEVEL_6);
    corruptCompressionMethod(newFile, ENTRY_A_LEVEL_9);
    PreDiffPlan plan = invokeGeneratePreDiffPlan(oldFile, newFile);
    Assert.assertNotNull(plan);
    // The plan should be to do nothing (empty plans) because the entries are not compressed with
    // deflate
    Assert.assertTrue(plan.getOldFileUncompressionPlan().isEmpty());
    Assert.assertTrue(plan.getNewFileUncompressionPlan().isEmpty());
    checkRecommendation(plan, new QualifiedRecommendation(
        findEntry(oldFile, ENTRY_A_LEVEL_6),
        findEntry(newFile, ENTRY_A_LEVEL_9),
        Recommendation.UNCOMPRESS_NEITHER,
        RecommendationReason.UNSUITABLE));
  }

  @Test
  public void testGeneratePreDiffPlan_TwoDifferentEntries_DifferentPaths() throws IOException {
    // Test the case where file paths are different as well as content within those files, i.e. each
    // entry is exclusive to its archive and is not the same
    byte[] oldBytes = UnitTestZipArchive.makeTestZip(Collections.singletonList(ENTRY_A_LEVEL_6));
    byte[] newBytes = UnitTestZipArchive.makeTestZip(Collections.singletonList(ENTRY_B_LEVEL_6));
    File oldFile = storeAndMapArchive(oldBytes);
    File newFile = storeAndMapArchive(newBytes);
    PreDiffPlan plan = invokeGeneratePreDiffPlan(oldFile, newFile);
    Assert.assertNotNull(plan);
    // The plan should be to do nothing (empty plans) because entry A is only in the old archive and
    // entry B is only in the new archive, so there is nothing to diff.
    Assert.assertTrue(plan.getOldFileUncompressionPlan().isEmpty());
    Assert.assertTrue(plan.getNewFileUncompressionPlan().isEmpty());
    Assert.assertTrue(plan.getQualifiedRecommendations().isEmpty());
  }

  @Test
  public void testGeneratePreDiffPlan_TwoEntriesEachArchive_SwappingOrder() throws IOException {
    // Test the case where two entries in each archive have both changed, AND they have changed
    // places in the file. The plan is supposed to be in file order, so that streaming is possible;
    // check that it is so.
    byte[] oldBytes =
        UnitTestZipArchive.makeTestZip(Arrays.asList(ENTRY_A_LEVEL_6, ENTRY_B_LEVEL_6));
    byte[] newBytes =
        UnitTestZipArchive.makeTestZip(Arrays.asList(ENTRY_B_LEVEL_9, ENTRY_A_LEVEL_9));
    File oldFile = storeAndMapArchive(oldBytes);
    File newFile = storeAndMapArchive(newBytes);
    PreDiffPlan plan = invokeGeneratePreDiffPlan(oldFile, newFile);
    Assert.assertNotNull(plan);
    // The plan should be to uncompress both entries, but the order is important. File order should
    // be in both plans.
    Assert.assertEquals(2, plan.getOldFileUncompressionPlan().size());
    Assert.assertEquals(2, plan.getNewFileUncompressionPlan().size());
    Assert.assertEquals(
        findRangeWithoutParams(oldFile, ENTRY_A_LEVEL_6),
        plan.getOldFileUncompressionPlan().get(0));
    Assert.assertEquals(
        findRangeWithoutParams(oldFile, ENTRY_B_LEVEL_6),
        plan.getOldFileUncompressionPlan().get(1));
    Assert.assertEquals(
        findRangeWithParams(newFile, ENTRY_B_LEVEL_9), plan.getNewFileUncompressionPlan().get(0));
    Assert.assertEquals(
        findRangeWithParams(newFile, ENTRY_A_LEVEL_9), plan.getNewFileUncompressionPlan().get(1));
  }

  @Test
  public void testGeneratePreDiffPlan_SimpleRename_Unchanged() throws IOException {
    // Test the case where file paths are different but the uncompressed content is the same.
    // The compression method used for both entries is identical, as are the compressed bytes.
    byte[] oldBytes = UnitTestZipArchive.makeTestZip(Collections.singletonList(ENTRY_A_LEVEL_6));
    byte[] newBytes =
        UnitTestZipArchive.makeTestZip(Collections.singletonList(SHADOW_ENTRY_A_LEVEL_6));
    File oldFile = storeAndMapArchive(oldBytes);
    File newFile = storeAndMapArchive(newBytes);
    PreDiffPlan plan = invokeGeneratePreDiffPlan(oldFile, newFile);
    Assert.assertNotNull(plan);
    // The plan should be to do nothing (empty plans) because the bytes are identical in both files
    // so the entries should remain compressed. However, unlike the case where there was no match,
    // there is now a qualified recommendation in the returned list.
    Assert.assertTrue(plan.getOldFileUncompressionPlan().isEmpty());
    Assert.assertTrue(plan.getNewFileUncompressionPlan().isEmpty());
    checkRecommendation(
        plan,
        new QualifiedRecommendation(
            findEntry(oldFile, ENTRY_A_LEVEL_6),
            findEntry(newFile, SHADOW_ENTRY_A_LEVEL_6),
            Recommendation.UNCOMPRESS_NEITHER,
            RecommendationReason.COMPRESSED_BYTES_IDENTICAL));
  }

  @Test
  public void testGeneratePreDiffPlan_SimpleRename_CompressionLevelChanged() throws IOException {
    // Test the case where file paths are different but the uncompressed content is the same.
    // The compression method used for each entry is different but the CRC32 is still the same, so
    // unlike like the plan with identical entries this time the plan should be to uncompress both
    // entries, allowing a super-efficient delta.
    byte[] oldBytes = UnitTestZipArchive.makeTestZip(Collections.singletonList(ENTRY_A_LEVEL_6));
    byte[] newBytes =
        UnitTestZipArchive.makeTestZip(Collections.singletonList(SHADOW_ENTRY_A_LEVEL_9));
    File oldFile = storeAndMapArchive(oldBytes);
    File newFile = storeAndMapArchive(newBytes);
    PreDiffPlan plan = invokeGeneratePreDiffPlan(oldFile, newFile);
    Assert.assertNotNull(plan);
    // The plan should be to uncompress both entries so that a super-efficient delta can be done.
    Assert.assertEquals(1, plan.getOldFileUncompressionPlan().size());
    Assert.assertEquals(
        findRangeWithoutParams(oldFile, ENTRY_A_LEVEL_6),
        plan.getOldFileUncompressionPlan().get(0));
    Assert.assertEquals(1, plan.getNewFileUncompressionPlan().size());
    Assert.assertEquals(
        findRangeWithParams(newFile, SHADOW_ENTRY_A_LEVEL_9),
        plan.getNewFileUncompressionPlan().get(0));
    checkRecommendation(
        plan,
        new QualifiedRecommendation(
            findEntry(oldFile, ENTRY_A_LEVEL_6),
            findEntry(newFile, SHADOW_ENTRY_A_LEVEL_9),
            Recommendation.UNCOMPRESS_BOTH,
            RecommendationReason.COMPRESSED_BYTES_CHANGED));
  }

  @Test
  public void testGeneratePreDiffPlan_ClonedAndCompressionLevelChanged() throws IOException {
    // Test the case where an entry exists in both old and new APK with identical uncompressed
    // content but different compressed content ***AND*** additionally a new copy exists in the new
    // archive, also with identical uncompressed content and different compressed content, i.e.:
    //
    // OLD APK:                                NEW APK:
    // ------------------------------------    -----------------------------------------------
    // foo.xml (compressed level 6)            foo.xml (compressed level 9, content unchanged)
    //                                         bar.xml (copy of foo.xml, compressed level 1)
    //
    // This test ensures that in such cases the foo.xml from the old apk is only enqueued for
    // uncompression ONE TIME.
    byte[] oldBytes = UnitTestZipArchive.makeTestZip(Collections.singletonList(ENTRY_A_LEVEL_6));
    byte[] newBytes =
        UnitTestZipArchive.makeTestZip(
            Arrays.asList(SHADOW_ENTRY_A_LEVEL_1, SHADOW_ENTRY_A_LEVEL_9));
    File oldFile = storeAndMapArchive(oldBytes);
    File newFile = storeAndMapArchive(newBytes);
    PreDiffPlan plan = invokeGeneratePreDiffPlan(oldFile, newFile);
    Assert.assertNotNull(plan);
    // The plan should be to uncompress both entries so that a super-efficient delta can be done.
    // Critically there should only be ONE command for the old file uncompression step!
    Assert.assertEquals(1, plan.getOldFileUncompressionPlan().size());
    Assert.assertEquals(
        findRangeWithoutParams(oldFile, ENTRY_A_LEVEL_6),
        plan.getOldFileUncompressionPlan().get(0));
    Assert.assertEquals(2, plan.getNewFileUncompressionPlan().size());
    Assert.assertEquals(
        findRangeWithParams(newFile, SHADOW_ENTRY_A_LEVEL_1),
        plan.getNewFileUncompressionPlan().get(0));
    Assert.assertEquals(
        findRangeWithParams(newFile, SHADOW_ENTRY_A_LEVEL_9),
        plan.getNewFileUncompressionPlan().get(1));
    checkRecommendation(
        plan,
        new QualifiedRecommendation(
            findEntry(oldFile, ENTRY_A_LEVEL_6),
            findEntry(newFile, SHADOW_ENTRY_A_LEVEL_1),
            Recommendation.UNCOMPRESS_BOTH,
            RecommendationReason.COMPRESSED_BYTES_CHANGED),
        new QualifiedRecommendation(
            findEntry(oldFile, ENTRY_A_LEVEL_6),
            findEntry(newFile, SHADOW_ENTRY_A_LEVEL_9),
            Recommendation.UNCOMPRESS_BOTH,
            RecommendationReason.COMPRESSED_BYTES_CHANGED));
  }

  @Test
  public void testGeneratePreDiffPlan_SimpleRename_CompressedToUncompressed() throws IOException {
    // Test the case where file paths are different but the uncompressed content is the same.
    // The compression method is changed from compressed to uncompressed but the rename should still
    // be detected and the plan should be to uncompress the old entry only.
    byte[] oldBytes = UnitTestZipArchive.makeTestZip(Collections.singletonList(ENTRY_A_LEVEL_6));
    byte[] newBytes =
        UnitTestZipArchive.makeTestZip(Collections.singletonList(SHADOW_ENTRY_A_STORED));
    File oldFile = storeAndMapArchive(oldBytes);
    File newFile = storeAndMapArchive(newBytes);
    PreDiffPlan plan = invokeGeneratePreDiffPlan(oldFile, newFile);
    Assert.assertNotNull(plan);
    // The plan should be to uncompress the old entry so that a super-efficient delta can be done.
    // The new entry isn't touched because it is already uncompressed.
    Assert.assertEquals(1, plan.getOldFileUncompressionPlan().size());
    Assert.assertEquals(
        findRangeWithoutParams(oldFile, ENTRY_A_LEVEL_6),
        plan.getOldFileUncompressionPlan().get(0));
    Assert.assertTrue(plan.getNewFileUncompressionPlan().isEmpty());
    checkRecommendation(
        plan,
        new QualifiedRecommendation(
            findEntry(oldFile, ENTRY_A_LEVEL_6),
            findEntry(newFile, SHADOW_ENTRY_A_STORED),
            Recommendation.UNCOMPRESS_OLD,
            RecommendationReason.COMPRESSED_CHANGED_TO_UNCOMPRESSED));
  }

  @Test
  public void testGeneratePreDiffPlan_SimpleRename_UncompressedToCompressed() throws IOException {
    // Test the case where file paths are different but the uncompressed content is the same.
    // The compression method is changed from uncompressed to compressed but the rename should still
    // be detected and the plan should be to uncompress the new entry only.
    byte[] oldBytes = UnitTestZipArchive.makeTestZip(Collections.singletonList(ENTRY_A_STORED));
    byte[] newBytes =
        UnitTestZipArchive.makeTestZip(Collections.singletonList(SHADOW_ENTRY_A_LEVEL_6));
    File oldFile = storeAndMapArchive(oldBytes);
    File newFile = storeAndMapArchive(newBytes);
    PreDiffPlan plan = invokeGeneratePreDiffPlan(oldFile, newFile);
    Assert.assertNotNull(plan);
    // The plan should be to uncompress the new entry so that a super-efficient delta can be done.
    // The old entry isn't touched because it is already uncompressed.
    Assert.assertTrue(plan.getOldFileUncompressionPlan().isEmpty());
    Assert.assertEquals(1, plan.getNewFileUncompressionPlan().size());
    Assert.assertEquals(
        findRangeWithParams(newFile, SHADOW_ENTRY_A_LEVEL_6),
        plan.getNewFileUncompressionPlan().get(0));
    checkRecommendation(
        plan,
        new QualifiedRecommendation(
            findEntry(oldFile, ENTRY_A_STORED),
            findEntry(newFile, SHADOW_ENTRY_A_LEVEL_6),
            Recommendation.UNCOMPRESS_NEW,
            RecommendationReason.UNCOMPRESSED_CHANGED_TO_COMPRESSED));
  }

}
