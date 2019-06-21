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

import static com.google.archivepatcher.generator.DeltaFormatExplanation.UNCHANGED;
import static com.google.archivepatcher.generator.PreDiffPlanEntryTestUtils.builderWithBothEntriesUncompressed;
import static com.google.archivepatcher.generator.PreDiffPlanEntryTestUtils.builderWithCompressedBytesChanged;
import static com.google.archivepatcher.generator.PreDiffPlanEntryTestUtils.builderWithCompressedBytesIdentical;
import static com.google.archivepatcher.generator.PreDiffPlanEntryTestUtils.builderWithCompressedToUncompressed;
import static com.google.archivepatcher.generator.PreDiffPlanEntryTestUtils.builderWithDeflateUnsuitable;
import static com.google.archivepatcher.generator.PreDiffPlanEntryTestUtils.builderWithUncompressedToCompressed;
import static com.google.archivepatcher.generator.PreDiffPlanEntryTestUtils.builderWithUnsuitable;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.archivepatcher.generator.DefaultDeflateCompressionDiviner.DivinationResult;
import com.google.archivepatcher.shared.DefaultDeflateCompatibilityWindow;
import com.google.archivepatcher.shared.JreDeflateParameters;
import com.google.archivepatcher.shared.PatchConstants.DeltaFormat;
import com.google.archivepatcher.shared.Range;
import com.google.archivepatcher.shared.TypedRange;
import com.google.archivepatcher.shared.UnitTestZipArchive;
import com.google.archivepatcher.shared.UnitTestZipEntry;
import com.google.archivepatcher.shared.bytesource.ByteSource;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import static org.junit.Assume.assumeTrue;
import com.google.archivepatcher.shared.DefaultDeflateCompatibilityWindow;

/** Tests for {@link PreDiffPlanner}. */
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

  private static final UnitTestZipEntry ENTRY_ZIP =
      UnitTestZipArchive.makeEmbeddedZipEntry(
          "/path.zip", 0, ImmutableList.of(ENTRY_A_LEVEL_6, ENTRY_B_LEVEL_6), null);
  private static final UnitTestZipEntry ENTRY_ZIP_LEVEL_6 =
      UnitTestZipArchive.makeEmbeddedZipEntry(
          "/path.zip", 6, ImmutableList.of(ENTRY_A_LEVEL_6, ENTRY_B_LEVEL_6), null);
  private static final UnitTestZipEntry ENTRY_ZIP_CHANGED =
      UnitTestZipArchive.makeEmbeddedZipEntry(
          "/path.zip", 0, ImmutableList.of(ENTRY_A_STORED, ENTRY_B_LEVEL_9), null);
  private static final UnitTestZipEntry SHADOW_ENTRY_ZIP =
      UnitTestZipArchive.makeEmbeddedZipEntry(
          "/new.path.zip", 0, ImmutableList.of(ENTRY_A_LEVEL_6, ENTRY_B_LEVEL_6), null);
  private static final UnitTestZipEntry ENTRY_ZIP_CORRUPTED =
      UnitTestZipArchive.makeUnitTestZipEntry("/path.zip", 0, "abc", null);

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

  private static final ImmutableList<PreDiffPlanEntryModifier> EMPTY_MODIFIERS = ImmutableList.of();
  private static final ImmutableSet<DeltaFormat> BSDIFF_ONLY = ImmutableSet.of(DeltaFormat.BSDIFF);
  private static final ImmutableSet<DeltaFormat> BSDIFF_FBF =
      ImmutableSet.of(DeltaFormat.BSDIFF, DeltaFormat.FILE_BY_FILE);

  private List<File> tempFilesCreated;
  private Map<File, Map<ByteArrayHolder, MinimalZipEntry>> entriesByPathByTempFile;

  @Before
  public void setup() {
    tempFilesCreated = new ArrayList<>();
    entriesByPathByTempFile = new HashMap<>();
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
      ByteArrayHolder key = new ByteArrayHolder(zipEntry.fileNameBytes());
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
    assertWithMessage("temp file not mapped").that(subMap).isNotNull();
    ByteArrayHolder key;
    key = new ByteArrayHolder(unitTestEntry.path.getBytes(StandardCharsets.UTF_8));
    return subMap.get(key);
  }

  /**
   * Finds the {@link TypedRange} corresponding to the compressed data for the specified unit test
   * entry in the specified temp file.
   *
   * @param tempFile the archive to search within
   * @param unitTestEntry the unit test entry to look up
   * @return the {@link TypedRange} for the unit test entry's compressed data
   */
  private Range findRangeWithoutParams(File tempFile, UnitTestZipEntry unitTestEntry) {
    MinimalZipEntry found = findEntry(tempFile, unitTestEntry);
    assertWithMessage("entry not found in temp file").that(found).isNotNull();
    return found.compressedDataRange();
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
    assertWithMessage("entry not found in temp file").that(found).isNotNull();
    return found
        .compressedDataRange()
        .withMetadata(JreDeflateParameters.of(unitTestEntry.level, 0, true));
  }

  /**
   * Deliberately introduce an error into the specified entry. This will make the entry impossible
   * to divine the settings for, because it is broken.
   * @param tempFile the archive to search within
   * @param unitTestEntry the unit test entry to deliberately corrupt
   */
  private void corruptEntryData(File tempFile, UnitTestZipEntry unitTestEntry) throws IOException {
    Range range = findRangeWithoutParams(tempFile, unitTestEntry);
    assertWithMessage("range too short to corrupt with 'junk'").that(range.length() >= 4).isTrue();
    try (RandomAccessFile raf = new RandomAccessFile(tempFile, "rw")) {
      raf.seek(range.offset());
      raf.write("junk".getBytes(StandardCharsets.UTF_8));
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
    try (ByteSource byteSource = ByteSource.fromFile(tempFile)) {
      long startOfEocd = MinimalZipParser.locateStartOfEocd(byteSource, 32768);
      MinimalCentralDirectoryMetadata centralDirectoryMetadata;
      int numEntries;
      try (InputStream sliceIn = byteSource.sliceFrom(startOfEocd).openStream()) {
        centralDirectoryMetadata = MinimalZipParser.parseEocd(sliceIn);
        numEntries = centralDirectoryMetadata.getNumEntriesInCentralDirectory();
      }

      try (InputStream sliceIn =
          byteSource
              .slice(
                  centralDirectoryMetadata.getOffsetOfCentralDirectory(),
                  centralDirectoryMetadata.getLengthOfCentralDirectory())
              .openStream()) {
        for (int x = 0; x < numEntries; x++) {
          // Here we compute the offset to the start of the file by computing offset to start of
          // sliceFrom (i.e. start of central directory) and adding it to the central directory
          // offset.
          long offsetToStartOfCentralDir =
              centralDirectoryMetadata.getLengthOfCentralDirectory() - sliceIn.available();
          long offsetToStartOfFile =
              centralDirectoryMetadata.getOffsetOfCentralDirectory() + offsetToStartOfCentralDir;
          MinimalZipEntry candidate =
              MinimalZipParser.parseCentralDirectoryEntry(sliceIn)
                  .fileOffsetOfCompressedData(0)
                  .lengthOfLocalEntry(0)
                  .build();
          if (candidate.getFileName().equals(unitTestEntry.path)) {
            // Located! Track offset and bail out.
            centralDirectoryRecordOffset = offsetToStartOfFile;
            x = numEntries;
          }
        }
      }
    }

    assertWithMessage("Entry not found").that(centralDirectoryRecordOffset).isNotEqualTo(-1L);
    try (RandomAccessFile raf = new RandomAccessFile(tempFile, "rw")) {
      // compression method is a 2 byte field stored 10 bytes into the record
      raf.seek(centralDirectoryRecordOffset + 10);
      raf.write(7);
      raf.write(7);
    }
  }

  private PreDiffPlan invokeGeneratePreDiffPlan(
      File oldFile,
      File newFile,
      List<PreDiffPlanEntryModifier> preDiffPlanEntryModifiers,
      Set<DeltaFormat> supportedDeltaFormats)
      throws IOException {
    Map<ByteArrayHolder, MinimalZipEntry> originalOldArchiveZipEntriesByPath =
        new LinkedHashMap<>();
    Map<ByteArrayHolder, MinimalZipEntry> originalNewArchiveZipEntriesByPath =
        new LinkedHashMap<>();
    Map<ByteArrayHolder, JreDeflateParameters> originalNewArchiveJreDeflateParametersByPath =
        new LinkedHashMap<>();

    for (MinimalZipEntry zipEntry : MinimalZipArchive.listEntries(oldFile)) {
      ByteArrayHolder key = new ByteArrayHolder(zipEntry.fileNameBytes());
      originalOldArchiveZipEntriesByPath.put(key, zipEntry);
    }

    try (ByteSource oldBlob = ByteSource.fromFile(oldFile);
        ByteSource newBlob = ByteSource.fromFile(newFile)) {
      for (DivinationResult divinationResult :
          DefaultDeflateCompressionDiviner.divineDeflateParameters(newBlob)) {
        ByteArrayHolder key = new ByteArrayHolder(divinationResult.minimalZipEntry.fileNameBytes());
      originalNewArchiveZipEntriesByPath.put(key, divinationResult.minimalZipEntry);
      originalNewArchiveJreDeflateParametersByPath.put(key, divinationResult.divinedParameters);
    }

      PreDiffPlanner preDiffPlanner =
          new PreDiffPlanner(
              oldBlob,
              originalOldArchiveZipEntriesByPath,
              newBlob,
              originalNewArchiveZipEntriesByPath,
              originalNewArchiveJreDeflateParametersByPath,
              preDiffPlanEntryModifiers,
              supportedDeltaFormats);
    return preDiffPlanner.generatePreDiffPlan();
  }
  }

  private void checkPreDiffPlanEntry(PreDiffPlan plan, PreDiffPlanEntry... expected) {
    assertThat(plan.getPreDiffPlanEntries()).isNotNull();
    assertThat(plan.getPreDiffPlanEntries()).hasSize(expected.length);
    for (int x = 0; x < expected.length; x++) {
      PreDiffPlanEntry actual = plan.getPreDiffPlanEntries().get(x);
      assertThat(actual.oldEntry().getFileName()).isEqualTo(expected[x].oldEntry().getFileName());
      assertThat(actual.newEntry().getFileName()).isEqualTo(expected[x].newEntry().getFileName());
      assertThat(actual.zipEntryUncompressionOption())
          .isEqualTo(expected[x].zipEntryUncompressionOption());
      assertThat(actual.uncompressionOptionExplanation())
          .isEqualTo(expected[x].uncompressionOptionExplanation());
      assertThat(actual.deltaFormat()).isEqualTo(expected[x].deltaFormat());
      assertThat(actual.deltaFormatExplanation()).isEqualTo(expected[x].deltaFormatExplanation());
    }
  }

  @Test
  public void testGeneratePreDiffPlan_OneCompressedEntry_Unchanged() throws IOException {
    byte[] bytes = UnitTestZipArchive.makeTestZip(ImmutableList.of(ENTRY_A_LEVEL_6));
    File oldFile = storeAndMapArchive(bytes);
    File newFile = storeAndMapArchive(bytes);
    PreDiffPlan plan = invokeGeneratePreDiffPlan(oldFile, newFile, EMPTY_MODIFIERS, BSDIFF_ONLY);
    assertThat(plan).isNotNull();
    // The plan should be to leave the entry alone in both the old and new archives (empty plans).
    assertThat(plan.getOldFileUncompressionPlan()).isEmpty();
    assertThat(plan.getNewFileUncompressionPlan()).isEmpty();
    checkPreDiffPlanEntry(
        plan,
        builderWithCompressedBytesIdentical()
            .oldEntry(findEntry(oldFile, ENTRY_A_LEVEL_6))
            .newEntry(findEntry(newFile, ENTRY_A_LEVEL_6))
            .build());
  }

  @Test
  public void testGeneratePreDiffPlan_OneCompressedEntry_LengthsChanged() throws IOException {
    // Test detection of compressed entry differences based on length mismatch.
    byte[] oldBytes = UnitTestZipArchive.makeTestZip(ImmutableList.of(ENTRY_A_LEVEL_6));
    byte[] newBytes = UnitTestZipArchive.makeTestZip(ImmutableList.of(ENTRY_A_LEVEL_9));
    File oldFile = storeAndMapArchive(oldBytes);
    File newFile = storeAndMapArchive(newBytes);
    PreDiffPlan plan = invokeGeneratePreDiffPlan(oldFile, newFile, EMPTY_MODIFIERS, BSDIFF_ONLY);
    assertThat(plan).isNotNull();
    // The plan should be to uncompress the entry in both the old and new archives.
    assertThat(plan.getOldFileUncompressionPlan()).hasSize(1);
    assertThat(plan.getOldFileUncompressionPlan().get(0))
        .isEqualTo(findRangeWithoutParams(oldFile, ENTRY_A_LEVEL_6));
    assertThat(plan.getNewFileUncompressionPlan()).hasSize(1);
    assertThat(plan.getNewFileUncompressionPlan().get(0))
        .isEqualTo(findRangeWithParams(newFile, ENTRY_A_LEVEL_9));
    checkPreDiffPlanEntry(
        plan,
        builderWithCompressedBytesChanged()
            .oldEntry(findEntry(oldFile, ENTRY_A_LEVEL_6))
            .newEntry(findEntry(newFile, ENTRY_A_LEVEL_9))
            .deltaFormatExplanation(UNCHANGED)
            .build());
  }

  @Test
  public void testGeneratePreDiffPlan_OneCompressedEntry_BytesChanged() throws IOException {
    // Test detection of compressed entry differences based on binary content mismatch where the
    // compressed lengths are exactly the same - i.e., force a byte-by-byte comparison of the
    // compressed data in the two entries.
    byte[] oldBytes =
        UnitTestZipArchive.makeTestZip(ImmutableList.of(FIXED_LENGTH_ENTRY_C1_LEVEL_6));
    byte[] newBytes =
        UnitTestZipArchive.makeTestZip(ImmutableList.of(FIXED_LENGTH_ENTRY_C2_LEVEL_6));
    File oldFile = storeAndMapArchive(oldBytes);
    File newFile = storeAndMapArchive(newBytes);
    PreDiffPlan plan = invokeGeneratePreDiffPlan(oldFile, newFile, EMPTY_MODIFIERS, BSDIFF_ONLY);
    assertThat(plan).isNotNull();
    // The plan should be to uncompress the entry in both the old and new archives.
    assertThat(plan.getOldFileUncompressionPlan()).hasSize(1);
    assertThat(plan.getNewFileUncompressionPlan()).hasSize(1);
    assertThat(plan.getOldFileUncompressionPlan().get(0))
        .isEqualTo(findRangeWithoutParams(oldFile, FIXED_LENGTH_ENTRY_C1_LEVEL_6));
    assertThat(plan.getNewFileUncompressionPlan().get(0))
        .isEqualTo(findRangeWithParams(newFile, FIXED_LENGTH_ENTRY_C2_LEVEL_6));
    checkPreDiffPlanEntry(
        plan,
        builderWithCompressedBytesChanged()
            .oldEntry(findEntry(oldFile, FIXED_LENGTH_ENTRY_C1_LEVEL_6))
            .newEntry(findEntry(newFile, FIXED_LENGTH_ENTRY_C2_LEVEL_6))
            .build());
  }

  @Test
  public void testGeneratePreDiffPlan_OneUncompressedEntry() throws IOException {
    // Test with uncompressed old and new. It doesn't matter whether the bytes are changed or
    // unchanged in this case.
    byte[] oldBytes = UnitTestZipArchive.makeTestZip(ImmutableList.of(ENTRY_A_STORED));
    byte[] newBytes = UnitTestZipArchive.makeTestZip(ImmutableList.of(ENTRY_A_STORED));
    File oldFile = storeAndMapArchive(oldBytes);
    File newFile = storeAndMapArchive(newBytes);
    PreDiffPlan plan = invokeGeneratePreDiffPlan(oldFile, newFile, EMPTY_MODIFIERS, BSDIFF_ONLY);
    assertThat(plan).isNotNull();
    // The plan should be to do nothing because both entries are already uncompressed
    assertThat(plan.getOldFileUncompressionPlan()).isEmpty();
    assertThat(plan.getNewFileUncompressionPlan()).isEmpty();
    checkPreDiffPlanEntry(
        plan,
        builderWithBothEntriesUncompressed()
            .oldEntry(findEntry(oldFile, ENTRY_A_STORED))
            .newEntry(findEntry(newFile, ENTRY_A_STORED))
            .deltaFormatExplanation(UNCHANGED)
            .build());
  }

  @Test
  public void testGeneratePreDiffPlan_OneEntry_CompressedToUncompressed() throws IOException {
    // Test the migration of an entry from compressed (old archive) to uncompressed (new archive).
    byte[] oldBytes = UnitTestZipArchive.makeTestZip(ImmutableList.of(ENTRY_A_LEVEL_9));
    byte[] newBytes = UnitTestZipArchive.makeTestZip(ImmutableList.of(ENTRY_A_STORED));
    File oldFile = storeAndMapArchive(oldBytes);
    File newFile = storeAndMapArchive(newBytes);
    PreDiffPlan plan = invokeGeneratePreDiffPlan(oldFile, newFile, EMPTY_MODIFIERS, BSDIFF_ONLY);
    assertThat(plan).isNotNull();
    // The plan should be to uncompress the entry in the old archive and do nothing in the new
    // archive (empty plan)
    assertThat(plan.getOldFileUncompressionPlan()).hasSize(1);
    assertThat(plan.getOldFileUncompressionPlan().get(0))
        .isEqualTo(findRangeWithoutParams(oldFile, ENTRY_A_LEVEL_9));
    assertThat(plan.getNewFileUncompressionPlan()).isEmpty();
    checkPreDiffPlanEntry(
        plan,
        builderWithCompressedToUncompressed()
            .oldEntry(findEntry(oldFile, ENTRY_A_LEVEL_9))
            .newEntry(findEntry(newFile, ENTRY_A_STORED))
            .deltaFormatExplanation(UNCHANGED)
            .build());
  }

  @Test
  public void testGeneratePreDiffPlan_OneEntry_UncompressedToCompressed() throws IOException {
    // Test the migration of an entry from uncompressed (old archive) to compressed (new archive).
    byte[] oldBytes = UnitTestZipArchive.makeTestZip(ImmutableList.of(ENTRY_A_STORED));
    byte[] newBytes = UnitTestZipArchive.makeTestZip(ImmutableList.of(ENTRY_A_LEVEL_6));
    File oldFile = storeAndMapArchive(oldBytes);
    File newFile = storeAndMapArchive(newBytes);
    PreDiffPlan plan = invokeGeneratePreDiffPlan(oldFile, newFile, EMPTY_MODIFIERS, BSDIFF_ONLY);
    assertThat(plan).isNotNull();
    // The plan should be to do nothing in the old archive (empty plan) and uncompress the entry in
    // the new archive
    assertThat(plan.getOldFileUncompressionPlan()).isEmpty();
    assertThat(plan.getNewFileUncompressionPlan()).hasSize(1);
    assertThat(plan.getNewFileUncompressionPlan().get(0))
        .isEqualTo(findRangeWithParams(newFile, ENTRY_A_LEVEL_6));
    checkPreDiffPlanEntry(
        plan,
        builderWithUncompressedToCompressed()
            .oldEntry(findEntry(oldFile, ENTRY_A_STORED))
            .newEntry(findEntry(newFile, ENTRY_A_LEVEL_6))
            .deltaFormatExplanation(UNCHANGED)
            .build());
  }

  @Test
  public void testGeneratePreDiffPlan_OneEntry_UncompressedToUndivinable() throws IOException {
    // Test the migration of an entry from uncompressed (old archive) to compressed (new archive),
    // but make the new entry un-divinable and therefore un-recompressible.
    byte[] oldBytes = UnitTestZipArchive.makeTestZip(ImmutableList.of(ENTRY_A_STORED));
    byte[] newBytes = UnitTestZipArchive.makeTestZip(ImmutableList.of(ENTRY_A_LEVEL_6));
    File oldFile = storeAndMapArchive(oldBytes);
    File newFile = storeAndMapArchive(newBytes);
    // Deliberately break the entry in the new file so that it will not be divinable
    corruptEntryData(newFile, ENTRY_A_LEVEL_6);
    PreDiffPlan plan = invokeGeneratePreDiffPlan(oldFile, newFile, EMPTY_MODIFIERS, BSDIFF_ONLY);
    assertThat(plan).isNotNull();
    // The plan WOULD be to do nothing in the old archive (empty plan) and uncompress the entry in
    // the new archive, but because the new entry is un-divinable it cannot be recompressed and so
    // the plan for the new archive should be empty as well.
    assertThat(plan.getOldFileUncompressionPlan()).isEmpty();
    assertThat(plan.getNewFileUncompressionPlan()).isEmpty();
    checkPreDiffPlanEntry(
        plan,
        builderWithDeflateUnsuitable()
            .oldEntry(findEntry(oldFile, ENTRY_A_STORED))
            .newEntry(findEntry(newFile, ENTRY_A_LEVEL_6))
            .build());
  }

  @Test
  public void testGeneratePreDiffPlan_OneEntry_OldUncompressed_NewNonDeflate() throws IOException {
    // Test the case where the entry is compressed with something other than deflate in the new
    // archive; it is thus not reproducible, not divinable, and therefore cannot be uncompressed.
    byte[] oldBytes = UnitTestZipArchive.makeTestZip(ImmutableList.of(ENTRY_A_STORED));
    byte[] newBytes = UnitTestZipArchive.makeTestZip(ImmutableList.of(ENTRY_A_LEVEL_9));
    File oldFile = storeAndMapArchive(oldBytes);
    File newFile = storeAndMapArchive(newBytes);
    corruptCompressionMethod(newFile, ENTRY_A_LEVEL_9);
    PreDiffPlan plan = invokeGeneratePreDiffPlan(oldFile, newFile, EMPTY_MODIFIERS, BSDIFF_ONLY);
    assertThat(plan).isNotNull();
    // The plan should be to do nothing (empty plans) because the the entry in the old archive is
    // already uncompressed and the entry in the new archive is not compressed with deflate (i.e.,
    // cannot be recompressed so cannot be touched).
    assertThat(plan.getOldFileUncompressionPlan()).isEmpty();
    assertThat(plan.getNewFileUncompressionPlan()).isEmpty();
    checkPreDiffPlanEntry(
        plan,
        builderWithUnsuitable()
            .oldEntry(findEntry(oldFile, ENTRY_A_STORED))
            .newEntry(findEntry(newFile, ENTRY_A_LEVEL_9))
            .build());
  }

  @Test
  public void testGeneratePreDiffPlan_OneEntry_OldNonDeflate_NewUncompressed() throws IOException {
    // Test the case where the entry is compressed with something other than deflate in the old
    // archive; it can't be uncompressed, so there's no point in modifying the new entry either.
    byte[] oldBytes = UnitTestZipArchive.makeTestZip(ImmutableList.of(ENTRY_A_LEVEL_9));
    byte[] newBytes = UnitTestZipArchive.makeTestZip(ImmutableList.of(ENTRY_A_STORED));
    File oldFile = storeAndMapArchive(oldBytes);
    File newFile = storeAndMapArchive(newBytes);
    corruptCompressionMethod(oldFile, ENTRY_A_LEVEL_9);
    PreDiffPlan plan = invokeGeneratePreDiffPlan(oldFile, newFile, EMPTY_MODIFIERS, BSDIFF_ONLY);
    assertThat(plan).isNotNull();
    // The plan should be to do nothing (empty plans) because the the entry in the old archive is
    // not compressed with deflate, so there is no point in trying to do anything at all.
    assertThat(plan.getOldFileUncompressionPlan()).isEmpty();
    assertThat(plan.getNewFileUncompressionPlan()).isEmpty();
    checkPreDiffPlanEntry(
        plan,
        builderWithUnsuitable()
            .oldEntry(findEntry(oldFile, ENTRY_A_LEVEL_9))
            .newEntry(findEntry(newFile, ENTRY_A_STORED))
            .build());
  }

  @Test
  public void testGeneratePreDiffPlan_OneEntry_BothNonDeflate() throws IOException {
    // Test the case where the entry is compressed with something other than deflate; it is thus
    // not reproducible, not divinable, and therefore cannot be uncompressed.
    byte[] oldBytes = UnitTestZipArchive.makeTestZip(ImmutableList.of(ENTRY_A_LEVEL_6));
    byte[] newBytes = UnitTestZipArchive.makeTestZip(ImmutableList.of(ENTRY_A_LEVEL_9));
    File oldFile = storeAndMapArchive(oldBytes);
    File newFile = storeAndMapArchive(newBytes);
    corruptCompressionMethod(oldFile, ENTRY_A_LEVEL_6);
    corruptCompressionMethod(newFile, ENTRY_A_LEVEL_9);
    PreDiffPlan plan = invokeGeneratePreDiffPlan(oldFile, newFile, EMPTY_MODIFIERS, BSDIFF_ONLY);
    assertThat(plan).isNotNull();
    // The plan should be to do nothing (empty plans) because the entries are not compressed with
    // deflate
    assertThat(plan.getOldFileUncompressionPlan()).isEmpty();
    assertThat(plan.getNewFileUncompressionPlan()).isEmpty();
    checkPreDiffPlanEntry(
        plan,
        builderWithUnsuitable()
            .oldEntry(findEntry(oldFile, ENTRY_A_LEVEL_6))
            .newEntry(findEntry(newFile, ENTRY_A_LEVEL_9))
            .build());
  }

  @Test
  public void testGeneratePreDiffPlan_TwoDifferentEntries_DifferentPaths() throws IOException {
    // Test the case where file paths are different as well as content within those files, i.e. each
    // entry is exclusive to its archive and is not the same
    byte[] oldBytes = UnitTestZipArchive.makeTestZip(ImmutableList.of(ENTRY_A_LEVEL_6));
    byte[] newBytes = UnitTestZipArchive.makeTestZip(ImmutableList.of(ENTRY_B_LEVEL_6));
    File oldFile = storeAndMapArchive(oldBytes);
    File newFile = storeAndMapArchive(newBytes);
    PreDiffPlan plan = invokeGeneratePreDiffPlan(oldFile, newFile, EMPTY_MODIFIERS, BSDIFF_ONLY);
    assertThat(plan).isNotNull();
    // The plan should be to do nothing (empty plans) because entry A is only in the old archive and
    // entry B is only in the new archive, so there is nothing to diff.
    assertThat(plan.getOldFileUncompressionPlan()).isEmpty();
    assertThat(plan.getNewFileUncompressionPlan()).isEmpty();
    assertThat(plan.getPreDiffPlanEntries()).isEmpty();
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
    PreDiffPlan plan = invokeGeneratePreDiffPlan(oldFile, newFile, EMPTY_MODIFIERS, BSDIFF_ONLY);
    assertThat(plan).isNotNull();
    // The plan should be to uncompress both entries, but the order is important. File order should
    // be in both plans.
    assertThat(plan.getOldFileUncompressionPlan()).hasSize(2);
    assertThat(plan.getNewFileUncompressionPlan()).hasSize(2);
    assertThat(plan.getOldFileUncompressionPlan().get(0))
        .isEqualTo(findRangeWithoutParams(oldFile, ENTRY_A_LEVEL_6));
    assertThat(plan.getOldFileUncompressionPlan().get(1))
        .isEqualTo(findRangeWithoutParams(oldFile, ENTRY_B_LEVEL_6));
    assertThat(plan.getNewFileUncompressionPlan().get(0))
        .isEqualTo(findRangeWithParams(newFile, ENTRY_B_LEVEL_9));
    assertThat(plan.getNewFileUncompressionPlan().get(1))
        .isEqualTo(findRangeWithParams(newFile, ENTRY_A_LEVEL_9));
  }

  @Test
  public void testGeneratePreDiffPlan_SimpleRename_Unchanged() throws IOException {
    // Test the case where file paths are different but the uncompressed content is the same.
    // The compression method used for both entries is identical, as are the compressed bytes.
    byte[] oldBytes = UnitTestZipArchive.makeTestZip(ImmutableList.of(ENTRY_A_LEVEL_6));
    byte[] newBytes = UnitTestZipArchive.makeTestZip(ImmutableList.of(SHADOW_ENTRY_A_LEVEL_6));
    File oldFile = storeAndMapArchive(oldBytes);
    File newFile = storeAndMapArchive(newBytes);
    PreDiffPlan plan = invokeGeneratePreDiffPlan(oldFile, newFile, EMPTY_MODIFIERS, BSDIFF_ONLY);
    assertThat(plan).isNotNull();
    // The plan should be to do nothing (empty plans) because the bytes are identical in both files
    // so the entries should remain compressed. However, unlike the case where there was no match,
    // there is now a PreDiffPlanEntry in the returned list.
    assertThat(plan.getOldFileUncompressionPlan()).isEmpty();
    assertThat(plan.getNewFileUncompressionPlan()).isEmpty();
    checkPreDiffPlanEntry(
        plan,
        builderWithCompressedBytesIdentical()
            .oldEntry(findEntry(oldFile, ENTRY_A_LEVEL_6))
            .newEntry(findEntry(newFile, SHADOW_ENTRY_A_LEVEL_6))
            .build());
  }

  @Test
  public void testGeneratePreDiffPlan_SimpleRename_CompressionLevelChanged() throws IOException {
    // Test the case where file paths are different but the uncompressed content is the same.
    // The compression method used for each entry is different but the CRC32 is still the same, so
    // unlike like the plan with identical entries this time the plan should be to uncompress both
    // entries, allowing a super-efficient delta.
    byte[] oldBytes = UnitTestZipArchive.makeTestZip(ImmutableList.of(ENTRY_A_LEVEL_6));
    byte[] newBytes = UnitTestZipArchive.makeTestZip(ImmutableList.of(SHADOW_ENTRY_A_LEVEL_9));
    File oldFile = storeAndMapArchive(oldBytes);
    File newFile = storeAndMapArchive(newBytes);
    PreDiffPlan plan = invokeGeneratePreDiffPlan(oldFile, newFile, EMPTY_MODIFIERS, BSDIFF_ONLY);
    assertThat(plan).isNotNull();
    // The plan should be to uncompress both entries so that a super-efficient delta can be done.
    assertThat(plan.getOldFileUncompressionPlan()).hasSize(1);
    assertThat(plan.getOldFileUncompressionPlan().get(0))
        .isEqualTo(findRangeWithoutParams(oldFile, ENTRY_A_LEVEL_6));
    assertThat(plan.getNewFileUncompressionPlan()).hasSize(1);
    assertThat(plan.getNewFileUncompressionPlan().get(0))
        .isEqualTo(findRangeWithParams(newFile, SHADOW_ENTRY_A_LEVEL_9));
    checkPreDiffPlanEntry(
        plan,
        builderWithCompressedBytesChanged()
            .oldEntry(findEntry(oldFile, ENTRY_A_LEVEL_6))
            .newEntry(findEntry(newFile, SHADOW_ENTRY_A_LEVEL_9))
            .deltaFormatExplanation(UNCHANGED)
            .build());
  }

  @Test
  public void testGeneratePreDiffPlan_ClonedAndCompressionLevelChanged() throws IOException {
    // TODO: fix compatibility in OpenJDK 1.8 (or higher)
    assumeTrue(new DefaultDeflateCompatibilityWindow().isCompatible());

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
    byte[] oldBytes = UnitTestZipArchive.makeTestZip(ImmutableList.of(ENTRY_A_LEVEL_6));
    byte[] newBytes =
        UnitTestZipArchive.makeTestZip(
            Arrays.asList(SHADOW_ENTRY_A_LEVEL_1, SHADOW_ENTRY_A_LEVEL_9));
    File oldFile = storeAndMapArchive(oldBytes);
    File newFile = storeAndMapArchive(newBytes);
    PreDiffPlan plan = invokeGeneratePreDiffPlan(oldFile, newFile, EMPTY_MODIFIERS, BSDIFF_ONLY);
    assertThat(plan).isNotNull();
    // The plan should be to uncompress both entries so that a super-efficient delta can be done.
    // Critically there should only be ONE command for the old file uncompression step!
    assertThat(plan.getOldFileUncompressionPlan()).hasSize(1);
    assertThat(plan.getOldFileUncompressionPlan().get(0))
        .isEqualTo(findRangeWithoutParams(oldFile, ENTRY_A_LEVEL_6));
    assertThat(plan.getNewFileUncompressionPlan()).hasSize(2);
    assertThat(plan.getNewFileUncompressionPlan().get(0))
        .isEqualTo(findRangeWithParams(newFile, SHADOW_ENTRY_A_LEVEL_1));
    assertThat(plan.getNewFileUncompressionPlan().get(1))
        .isEqualTo(findRangeWithParams(newFile, SHADOW_ENTRY_A_LEVEL_9));
    checkPreDiffPlanEntry(
        plan,
        builderWithCompressedBytesChanged()
            .oldEntry(findEntry(oldFile, ENTRY_A_LEVEL_6))
            .newEntry(findEntry(newFile, SHADOW_ENTRY_A_LEVEL_1))
            .deltaFormatExplanation(UNCHANGED)
            .build(),
        builderWithCompressedBytesChanged()
            .oldEntry(findEntry(oldFile, ENTRY_A_LEVEL_6))
            .newEntry(findEntry(newFile, SHADOW_ENTRY_A_LEVEL_9))
            .deltaFormatExplanation(UNCHANGED)
            .build());
  }

  @Test
  public void testGeneratePreDiffPlan_SimpleRename_CompressedToUncompressed() throws IOException {
    // Test the case where file paths are different but the uncompressed content is the same.
    // The compression method is changed from compressed to uncompressed but the rename should still
    // be detected and the plan should be to uncompress the old entry only.
    byte[] oldBytes = UnitTestZipArchive.makeTestZip(ImmutableList.of(ENTRY_A_LEVEL_6));
    byte[] newBytes = UnitTestZipArchive.makeTestZip(ImmutableList.of(SHADOW_ENTRY_A_STORED));
    File oldFile = storeAndMapArchive(oldBytes);
    File newFile = storeAndMapArchive(newBytes);
    PreDiffPlan plan = invokeGeneratePreDiffPlan(oldFile, newFile, EMPTY_MODIFIERS, BSDIFF_ONLY);
    assertThat(plan).isNotNull();
    // The plan should be to uncompress the old entry so that a super-efficient delta can be done.
    // The new entry isn't touched because it is already uncompressed.
    assertThat(plan.getOldFileUncompressionPlan()).hasSize(1);
    assertThat(plan.getOldFileUncompressionPlan().get(0))
        .isEqualTo(findRangeWithoutParams(oldFile, ENTRY_A_LEVEL_6));
    assertThat(plan.getNewFileUncompressionPlan()).isEmpty();
    checkPreDiffPlanEntry(
        plan,
        builderWithCompressedToUncompressed()
            .oldEntry(findEntry(oldFile, ENTRY_A_LEVEL_6))
            .newEntry(findEntry(newFile, SHADOW_ENTRY_A_STORED))
            .deltaFormatExplanation(UNCHANGED)
            .build());
  }

  @Test
  public void testGeneratePreDiffPlan_SimpleRename_UncompressedToCompressed() throws IOException {
    // Test the case where file paths are different but the uncompressed content is the same.
    // The compression method is changed from uncompressed to compressed but the rename should still
    // be detected and the plan should be to uncompress the new entry only.
    byte[] oldBytes = UnitTestZipArchive.makeTestZip(ImmutableList.of(ENTRY_A_STORED));
    byte[] newBytes = UnitTestZipArchive.makeTestZip(ImmutableList.of(SHADOW_ENTRY_A_LEVEL_6));
    File oldFile = storeAndMapArchive(oldBytes);
    File newFile = storeAndMapArchive(newBytes);
    PreDiffPlan plan = invokeGeneratePreDiffPlan(oldFile, newFile, EMPTY_MODIFIERS, BSDIFF_ONLY);
    assertThat(plan).isNotNull();
    // The plan should be to uncompress the new entry so that a super-efficient delta can be done.
    // The old entry isn't touched because it is already uncompressed.
    assertThat(plan.getOldFileUncompressionPlan()).isEmpty();
    assertThat(plan.getNewFileUncompressionPlan()).hasSize(1);
    assertThat(plan.getNewFileUncompressionPlan().get(0))
        .isEqualTo(findRangeWithParams(newFile, SHADOW_ENTRY_A_LEVEL_6));
    checkPreDiffPlanEntry(
        plan,
        builderWithUncompressedToCompressed()
            .oldEntry(findEntry(oldFile, ENTRY_A_STORED))
            .newEntry(findEntry(newFile, SHADOW_ENTRY_A_LEVEL_6))
            .deltaFormatExplanation(UNCHANGED)
            .build());
  }

  @Test
  public void generatePreDiffPlan_zipEntry_unchanged() throws Exception {
    byte[] oldBytes = UnitTestZipArchive.makeTestZip(ImmutableList.of(ENTRY_ZIP));
    byte[] newBytes = UnitTestZipArchive.makeTestZip(ImmutableList.of(ENTRY_ZIP));
    File oldFile = storeAndMapArchive(oldBytes);
    File newFile = storeAndMapArchive(newBytes);

    PreDiffPlan plan = invokeGeneratePreDiffPlan(oldFile, newFile, EMPTY_MODIFIERS, BSDIFF_FBF);

    assertThat(plan).isNotNull();
    assertThat(plan.getOldFileUncompressionPlan()).isEmpty();
    assertThat(plan.getNewFileUncompressionPlan()).isEmpty();
    checkPreDiffPlanEntry(
        plan,
        builderWithBothEntriesUncompressed()
            .oldEntry(findEntry(oldFile, ENTRY_ZIP))
            .newEntry(findEntry(newFile, ENTRY_ZIP))
            .deltaFormatExplanation(UNCHANGED)
            .build());
  }

  @Test
  public void generatePreDiffPlan_zipEntry_unsuitable() throws Exception {
    byte[] oldBytes = UnitTestZipArchive.makeTestZip(ImmutableList.of(ENTRY_ZIP));
    byte[] newBytes = UnitTestZipArchive.makeTestZip(ImmutableList.of(ENTRY_ZIP));
    File oldFile = storeAndMapArchive(oldBytes);
    File newFile = storeAndMapArchive(newBytes);
    corruptCompressionMethod(oldFile, ENTRY_ZIP);
    corruptCompressionMethod(newFile, ENTRY_ZIP);

    PreDiffPlan plan = invokeGeneratePreDiffPlan(oldFile, newFile, EMPTY_MODIFIERS, BSDIFF_FBF);

    assertThat(plan).isNotNull();
    assertThat(plan.getOldFileUncompressionPlan()).isEmpty();
    assertThat(plan.getNewFileUncompressionPlan()).isEmpty();
    checkPreDiffPlanEntry(
        plan,
        builderWithUnsuitable()
            .oldEntry(findEntry(oldFile, ENTRY_ZIP))
            .newEntry(findEntry(newFile, ENTRY_ZIP))
            .build());
  }

  @Test
  public void generatePreDiffPlan_zipEntry_deflateUnsuitable() throws Exception {
    byte[] oldBytes = UnitTestZipArchive.makeTestZip(ImmutableList.of(ENTRY_ZIP));
    byte[] newBytes = UnitTestZipArchive.makeTestZip(ImmutableList.of(ENTRY_ZIP_LEVEL_6));
    File oldFile = storeAndMapArchive(oldBytes);
    File newFile = storeAndMapArchive(newBytes);
    corruptEntryData(newFile, ENTRY_ZIP_LEVEL_6);

    PreDiffPlan plan = invokeGeneratePreDiffPlan(oldFile, newFile, EMPTY_MODIFIERS, BSDIFF_FBF);

    assertThat(plan).isNotNull();
    assertThat(plan.getOldFileUncompressionPlan()).isEmpty();
    assertThat(plan.getNewFileUncompressionPlan()).isEmpty();
    checkPreDiffPlanEntry(
        plan,
        builderWithDeflateUnsuitable()
            .oldEntry(findEntry(oldFile, ENTRY_ZIP))
            .newEntry(findEntry(newFile, ENTRY_ZIP_LEVEL_6))
            .build());
  }

  @Test
  public void generatePreDiffPlan_zipEntry_changed() throws Exception {
    byte[] oldBytes = UnitTestZipArchive.makeTestZip(ImmutableList.of(ENTRY_ZIP));
    byte[] newBytes = UnitTestZipArchive.makeTestZip(ImmutableList.of(ENTRY_ZIP_CHANGED));
    File oldFile = storeAndMapArchive(oldBytes);
    File newFile = storeAndMapArchive(newBytes);

    PreDiffPlan plan = invokeGeneratePreDiffPlan(oldFile, newFile, EMPTY_MODIFIERS, BSDIFF_FBF);

    assertThat(plan).isNotNull();
    assertThat(plan.getOldFileUncompressionPlan()).isEmpty();
    assertThat(plan.getNewFileUncompressionPlan()).isEmpty();
    checkPreDiffPlanEntry(
        plan,
        builderWithBothEntriesUncompressed()
            .oldEntry(findEntry(oldFile, ENTRY_ZIP))
            .newEntry(findEntry(newFile, ENTRY_ZIP_CHANGED))
            .deltaFormat(DeltaFormat.FILE_BY_FILE)
            .deltaFormatExplanation(DeltaFormatExplanation.FILE_TYPE)
            .build());
  }

  @Test
  public void generatePreDiffPlan_zipEntry_changed_bsdiffOnly() throws Exception {
    byte[] oldBytes = UnitTestZipArchive.makeTestZip(ImmutableList.of(ENTRY_ZIP));
    byte[] newBytes = UnitTestZipArchive.makeTestZip(ImmutableList.of(ENTRY_ZIP_CHANGED));
    File oldFile = storeAndMapArchive(oldBytes);
    File newFile = storeAndMapArchive(newBytes);

    PreDiffPlan plan = invokeGeneratePreDiffPlan(oldFile, newFile, EMPTY_MODIFIERS, BSDIFF_ONLY);

    assertThat(plan).isNotNull();
    assertThat(plan.getOldFileUncompressionPlan()).isEmpty();
    assertThat(plan.getNewFileUncompressionPlan()).isEmpty();
    checkPreDiffPlanEntry(
        plan,
        builderWithBothEntriesUncompressed()
            .oldEntry(findEntry(oldFile, ENTRY_ZIP))
            .newEntry(findEntry(newFile, ENTRY_ZIP_CHANGED))
            .build());
  }

  @Test
  public void generatePreDiffPlan_zipEntry_renamed() throws Exception {
    byte[] oldBytes = UnitTestZipArchive.makeTestZip(ImmutableList.of(ENTRY_ZIP));
    byte[] newBytes = UnitTestZipArchive.makeTestZip(ImmutableList.of(SHADOW_ENTRY_ZIP));
    File oldFile = storeAndMapArchive(oldBytes);
    File newFile = storeAndMapArchive(newBytes);

    PreDiffPlan plan = invokeGeneratePreDiffPlan(oldFile, newFile, EMPTY_MODIFIERS, BSDIFF_FBF);

    assertThat(plan).isNotNull();
    assertThat(plan.getOldFileUncompressionPlan()).isEmpty();
    assertThat(plan.getNewFileUncompressionPlan()).isEmpty();
    checkPreDiffPlanEntry(
        plan,
        builderWithBothEntriesUncompressed()
            .oldEntry(findEntry(oldFile, ENTRY_ZIP))
            .newEntry(findEntry(newFile, SHADOW_ENTRY_ZIP))
            .deltaFormatExplanation(UNCHANGED)
            .build());
  }

  @Test
  public void generatePreDiffPlan_zipEntry_corrupted() throws Exception {
    byte[] oldBytes = UnitTestZipArchive.makeTestZip(ImmutableList.of(ENTRY_ZIP));
    byte[] newBytes = UnitTestZipArchive.makeTestZip(ImmutableList.of(ENTRY_ZIP_CORRUPTED));
    File oldFile = storeAndMapArchive(oldBytes);
    File newFile = storeAndMapArchive(newBytes);

    PreDiffPlan plan = invokeGeneratePreDiffPlan(oldFile, newFile, EMPTY_MODIFIERS, BSDIFF_FBF);

    assertThat(plan).isNotNull();
    assertThat(plan.getOldFileUncompressionPlan()).isEmpty();
    assertThat(plan.getNewFileUncompressionPlan()).isEmpty();
    checkPreDiffPlanEntry(
        plan,
        builderWithBothEntriesUncompressed()
            .oldEntry(findEntry(oldFile, ENTRY_ZIP))
            .newEntry(findEntry(newFile, ENTRY_ZIP_CORRUPTED))
            .deltaFormat(DeltaFormat.BSDIFF)
            .deltaFormatExplanation(DeltaFormatExplanation.DEFAULT)
            .build());
  }
}
