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

import static com.google.archivepatcher.generator.MinimalZipEntryUtils.getFakeBuilder;
import static com.google.archivepatcher.generator.PreDiffPlanEntryTestUtils.builderWithBothEntriesUncompressed;
import static com.google.archivepatcher.generator.PreDiffPlanEntryTestUtils.builderWithCompressedBytesChanged;
import static com.google.archivepatcher.generator.PreDiffPlanEntryTestUtils.builderWithCompressedBytesIdentical;
import static com.google.archivepatcher.generator.PreDiffPlanEntryTestUtils.builderWithCompressedToUncompressed;
import static com.google.archivepatcher.generator.PreDiffPlanEntryTestUtils.builderWithUnsuitable;
import static com.google.archivepatcher.generator.PreDiffPlanEntryTestUtils.suppressed;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.archivepatcher.shared.bytesource.ByteSource;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link DeltaFriendlyOldBlobSizeLimiter}. */
@RunWith(JUnit4.class)
@SuppressWarnings("javadoc")
public class DeltaFriendlyOldBlobSizeLimiterTest {
  private static final MinimalZipEntry UNIMPORTANT = makeFakeEntry("/unimportant", 1337, 1337);
  private static final MinimalZipEntry ENTRY_A_100K =
      makeFakeEntry("/a/100k", 100 * 1024, 200 * 1024);
  private static final MinimalZipEntry ENTRY_B_200K =
      makeFakeEntry("/b/200k", 100 * 1024, 300 * 1024);
  private static final MinimalZipEntry ENTRY_C_300K =
      makeFakeEntry("/c/300k", 100 * 1024, 400 * 1024);
  private static final MinimalZipEntry ENTRY_D_400K =
      makeFakeEntry("/d/400k", 100 * 1024, 500 * 1024);
  private static final MinimalZipEntry IGNORED_A = makeFakeEntry("/ignored/a", 1234, 5678);
  private static final MinimalZipEntry IGNORED_B = makeFakeEntry("/ignored/b", 5678, 9101112);
  private static final MinimalZipEntry IGNORED_C = makeFakeEntry("/ignored/c", 9101112, 13141516);

  // First four entries are all ones where uncompression of the old resource is required.
  // Note that there is a mix of UNCOMPRESS_OLD and UNCOMPRESS_BOTH, both of which will have the
  // "old" entry flagged for uncompression (i.e., should be relevant to the filtering logic).
  private static final PreDiffPlanEntry PRE_DIFF_PLAN_ENTRY_A_100K =
      builderWithCompressedBytesChanged().oldEntry(ENTRY_A_100K).newEntry(UNIMPORTANT).build();
  private static final PreDiffPlanEntry PRE_DIFF_PLAN_ENTRY_B_200K =
      builderWithCompressedToUncompressed().oldEntry(ENTRY_B_200K).newEntry(UNIMPORTANT).build();
  private static final PreDiffPlanEntry PRE_DIFF_PLAN_ENTRY_C_300K =
      builderWithCompressedBytesChanged().oldEntry(ENTRY_C_300K).newEntry(UNIMPORTANT).build();
  // Here we deliberately use UNCOMPRESS_BOTH to test that it has the same effect as UNCOMPRESS_OLD.
  private static final PreDiffPlanEntry PRE_DIFF_PLAN_ENTRY_D_400K =
      PreDiffPlanEntry.builder()
          .oldEntry(ENTRY_D_400K)
          .newEntry(UNIMPORTANT)
          .zipEntryUncompressionOption(ZipEntryUncompressionOption.UNCOMPRESS_BOTH)
          .uncompressionOptionExplanation(
              UncompressionOptionExplanation.COMPRESSED_CHANGED_TO_UNCOMPRESSED)
          .build();

  // Remaining entries are all ones where recompression is NOT required. Note the mixture of
  // UNCOMPRESS_NEITHER and UNCOMPRESS_OLD, neither of which will have the "new" entry flagged for
  // recompression (ie., must be ignored by the filtering logic).
  private static final PreDiffPlanEntry PRE_DIFF_PLAN_ENTRY_IGNORED_A_UNCHANGED =
      builderWithCompressedBytesIdentical().oldEntry(IGNORED_A).newEntry(UNIMPORTANT).build();
  private static final PreDiffPlanEntry PRE_DIFF_PLAN_ENTRY_IGNORED_B_BOTH_UNCOMPRESSED =
      builderWithBothEntriesUncompressed().oldEntry(IGNORED_B).newEntry(UNIMPORTANT).build();
  private static final PreDiffPlanEntry PRE_DIFF_PLAN_ENTRY_IGNORED_C_UNSUITABLE =
      builderWithUnsuitable().oldEntry(IGNORED_C).newEntry(UNIMPORTANT).build();

  /** Convenience reference to all the entries that should be ignored by filtering. */
  private static final List<PreDiffPlanEntry> ALL_IGNORED_PRE_DIFF_PLAN_ENTRIES =
      Collections.unmodifiableList(
          Arrays.asList(
              PRE_DIFF_PLAN_ENTRY_IGNORED_A_UNCHANGED,
              PRE_DIFF_PLAN_ENTRY_IGNORED_B_BOTH_UNCOMPRESSED,
              PRE_DIFF_PLAN_ENTRY_IGNORED_C_UNSUITABLE));

  /** Convenience reference to all the entries that are subject to filtering. */
  private static final List<PreDiffPlanEntry> ALL_PRE_DIFF_PLAN_ENTRIES =
      Collections.unmodifiableList(
          Arrays.asList(
              PRE_DIFF_PLAN_ENTRY_IGNORED_A_UNCHANGED,
              PRE_DIFF_PLAN_ENTRY_A_100K,
              PRE_DIFF_PLAN_ENTRY_IGNORED_B_BOTH_UNCOMPRESSED,
              PRE_DIFF_PLAN_ENTRY_D_400K,
              PRE_DIFF_PLAN_ENTRY_IGNORED_C_UNSUITABLE,
              PRE_DIFF_PLAN_ENTRY_B_200K,
              PRE_DIFF_PLAN_ENTRY_C_300K));

  /**
   * Make a structurally valid but totally bogus {@link MinimalZipEntry} for the purpose of testing
   * the {@link PreDiffPlanEntryModifier}.
   *
   * @param path the path to set on the entry, to help with debugging
   * @param compressedSize the compressed size of the entry, in bytes
   * @param uncompressedSize the uncompressed size of the entry, in bytes
   */
  private static MinimalZipEntry makeFakeEntry(
      String path, long compressedSize, long uncompressedSize) {
    try {
      return getFakeBuilder()
          .compressedSize(compressedSize)
          .uncompressedSize(uncompressedSize)
          .fileNameBytes(path.getBytes("UTF8"))
          .build();
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException(e); // Impossible on any modern system
    }
  }

  @Test
  public void testNegativeLimit() {
    try {
      new DeltaFriendlyOldBlobSizeLimiter(-1);
      assertWithMessage("Set a negative limit").fail();
    } catch (IllegalArgumentException expected) {
      // Pass
    }
  }

  private File tempFile;
  private ByteSource tempFileBlob = null;

  @Before
  public void setup() throws IOException {
    // Make an empty file to test the recommender's limitation logic
    tempFile = File.createTempFile("DeltaFriendlyOldBlobSizeLimiterTest", "test");
    tempFile.deleteOnExit();
    tempFileBlob = ByteSource.fromFile(tempFile);
  }

  @After
  public void tearDown() throws Exception {
    tempFileBlob.close();
    tempFile.delete();
  }

  @Test
  public void testZeroLimit() {
    DeltaFriendlyOldBlobSizeLimiter limiter = new DeltaFriendlyOldBlobSizeLimiter(0);
    List<PreDiffPlanEntry> expected = new ArrayList<>();
    expected.addAll(
        suppressed(
            PRE_DIFF_PLAN_ENTRY_A_100K,
            PRE_DIFF_PLAN_ENTRY_B_200K,
            PRE_DIFF_PLAN_ENTRY_C_300K,
            PRE_DIFF_PLAN_ENTRY_D_400K));
    expected.addAll(ALL_IGNORED_PRE_DIFF_PLAN_ENTRIES);
    assertThat(
            limiter.getModifiedPreDiffPlanEntries(
                tempFileBlob, tempFileBlob, ALL_PRE_DIFF_PLAN_ENTRIES))
        .containsExactlyElementsIn(expected);
  }

  @Test
  public void testMaxLimit() {
    DeltaFriendlyOldBlobSizeLimiter limiter = new DeltaFriendlyOldBlobSizeLimiter(Long.MAX_VALUE);
    assertThat(
            limiter.getModifiedPreDiffPlanEntries(
                tempFileBlob, tempFileBlob, ALL_PRE_DIFF_PLAN_ENTRIES))
        .containsExactlyElementsIn(ALL_PRE_DIFF_PLAN_ENTRIES);
  }

  @Test
  public void testLimit_ExactlySmallest() {
    long limit =
        PRE_DIFF_PLAN_ENTRY_A_100K.oldEntry().uncompressedSize()
            - PRE_DIFF_PLAN_ENTRY_A_100K
                .oldEntry()
                .compressedDataRange()
                .length(); // Exactly large enough
    DeltaFriendlyOldBlobSizeLimiter limiter = new DeltaFriendlyOldBlobSizeLimiter(limit);
    List<PreDiffPlanEntry> expected = new ArrayList<>();
    expected.add(PRE_DIFF_PLAN_ENTRY_A_100K);
    expected.addAll(
        suppressed(
            PRE_DIFF_PLAN_ENTRY_B_200K, PRE_DIFF_PLAN_ENTRY_C_300K, PRE_DIFF_PLAN_ENTRY_D_400K));
    expected.addAll(ALL_IGNORED_PRE_DIFF_PLAN_ENTRIES);
    assertThat(
            limiter.getModifiedPreDiffPlanEntries(
                tempFileBlob, tempFileBlob, ALL_PRE_DIFF_PLAN_ENTRIES))
        .containsExactlyElementsIn(expected);
  }

  @Test
  public void testLimit_EdgeUnderSmallest() {
    long limit =
        PRE_DIFF_PLAN_ENTRY_A_100K.oldEntry().uncompressedSize()
            - PRE_DIFF_PLAN_ENTRY_A_100K.oldEntry().compressedDataRange().length()
            - 1; // 1 byte too small
    DeltaFriendlyOldBlobSizeLimiter limiter = new DeltaFriendlyOldBlobSizeLimiter(limit);
    List<PreDiffPlanEntry> expected = new ArrayList<>();
    expected.addAll(
        suppressed(
            PRE_DIFF_PLAN_ENTRY_A_100K,
            PRE_DIFF_PLAN_ENTRY_B_200K,
            PRE_DIFF_PLAN_ENTRY_C_300K,
            PRE_DIFF_PLAN_ENTRY_D_400K));
    expected.addAll(ALL_IGNORED_PRE_DIFF_PLAN_ENTRIES);
    assertThat(
            limiter.getModifiedPreDiffPlanEntries(
                tempFileBlob, tempFileBlob, ALL_PRE_DIFF_PLAN_ENTRIES))
        .containsExactlyElementsIn(expected);
  }

  @Test
  public void testLimit_EdgeOverSmallest() {
    long limit =
        PRE_DIFF_PLAN_ENTRY_A_100K.oldEntry().uncompressedSize()
            - PRE_DIFF_PLAN_ENTRY_A_100K.oldEntry().compressedDataRange().length()
            + 1; // 1 byte extra room
    DeltaFriendlyOldBlobSizeLimiter limiter = new DeltaFriendlyOldBlobSizeLimiter(limit);
    List<PreDiffPlanEntry> expected = new ArrayList<>();
    expected.add(PRE_DIFF_PLAN_ENTRY_A_100K);
    expected.addAll(
        suppressed(
            PRE_DIFF_PLAN_ENTRY_B_200K, PRE_DIFF_PLAN_ENTRY_C_300K, PRE_DIFF_PLAN_ENTRY_D_400K));
    expected.addAll(ALL_IGNORED_PRE_DIFF_PLAN_ENTRIES);
    assertThat(
            limiter.getModifiedPreDiffPlanEntries(
                tempFileBlob, tempFileBlob, ALL_PRE_DIFF_PLAN_ENTRIES))
        .containsExactlyElementsIn(expected);
  }

  @Test
  public void testLimit_ExactlyLargest() {
    long limit =
        PRE_DIFF_PLAN_ENTRY_D_400K.oldEntry().uncompressedSize()
            - PRE_DIFF_PLAN_ENTRY_D_400K
                .oldEntry()
                .compressedDataRange()
                .length(); // Exactly large enough
    DeltaFriendlyOldBlobSizeLimiter limiter = new DeltaFriendlyOldBlobSizeLimiter(limit);
    List<PreDiffPlanEntry> expected = new ArrayList<>();
    expected.add(PRE_DIFF_PLAN_ENTRY_D_400K);
    expected.addAll(
        suppressed(
            PRE_DIFF_PLAN_ENTRY_A_100K, PRE_DIFF_PLAN_ENTRY_B_200K, PRE_DIFF_PLAN_ENTRY_C_300K));
    expected.addAll(ALL_IGNORED_PRE_DIFF_PLAN_ENTRIES);
    assertThat(
            limiter.getModifiedPreDiffPlanEntries(
                tempFileBlob, tempFileBlob, ALL_PRE_DIFF_PLAN_ENTRIES))
        .containsExactlyElementsIn(expected);
  }

  @Test
  public void testLimit_EdgeUnderLargest() {
    long limit =
        PRE_DIFF_PLAN_ENTRY_D_400K.oldEntry().uncompressedSize()
            - PRE_DIFF_PLAN_ENTRY_D_400K.oldEntry().compressedDataRange().length()
            - 1; // 1 byte too small
    DeltaFriendlyOldBlobSizeLimiter limiter = new DeltaFriendlyOldBlobSizeLimiter(limit);
    List<PreDiffPlanEntry> expected = new ArrayList<>();
    expected.add(PRE_DIFF_PLAN_ENTRY_C_300K);
    expected.addAll(
        suppressed(
            PRE_DIFF_PLAN_ENTRY_A_100K, PRE_DIFF_PLAN_ENTRY_B_200K, PRE_DIFF_PLAN_ENTRY_D_400K));
    expected.addAll(ALL_IGNORED_PRE_DIFF_PLAN_ENTRIES);
    assertThat(
            limiter.getModifiedPreDiffPlanEntries(
                tempFileBlob, tempFileBlob, ALL_PRE_DIFF_PLAN_ENTRIES))
        .containsExactlyElementsIn(expected);
  }

  @Test
  public void testLimit_EdgeOverLargest() {
    long limit =
        PRE_DIFF_PLAN_ENTRY_D_400K.oldEntry().uncompressedSize()
            - PRE_DIFF_PLAN_ENTRY_D_400K.oldEntry().compressedDataRange().length()
            + 1; // 1 byte extra room
    DeltaFriendlyOldBlobSizeLimiter limiter = new DeltaFriendlyOldBlobSizeLimiter(limit);
    List<PreDiffPlanEntry> expected = new ArrayList<>();
    expected.add(PRE_DIFF_PLAN_ENTRY_D_400K);
    expected.addAll(
        suppressed(
            PRE_DIFF_PLAN_ENTRY_A_100K, PRE_DIFF_PLAN_ENTRY_B_200K, PRE_DIFF_PLAN_ENTRY_C_300K));
    expected.addAll(ALL_IGNORED_PRE_DIFF_PLAN_ENTRIES);
    assertThat(
            limiter.getModifiedPreDiffPlanEntries(
                tempFileBlob, tempFileBlob, ALL_PRE_DIFF_PLAN_ENTRIES))
        .containsExactlyElementsIn(expected);
  }

  @Test
  public void testLimit_Complex() {
    // A more nuanced test. Here we set up a limit of 600k - big enough to get the largest and the
    // THIRD largest files. The second largest will fail because there isn't enough space after
    // adding the first largest, and the fourth largest will fail because there is not enough space
    // after adding the third largest. Tricky.
    long limit =
        (PRE_DIFF_PLAN_ENTRY_D_400K.oldEntry().uncompressedSize()
                - PRE_DIFF_PLAN_ENTRY_D_400K.oldEntry().compressedDataRange().length())
            + (PRE_DIFF_PLAN_ENTRY_B_200K.oldEntry().uncompressedSize()
                - PRE_DIFF_PLAN_ENTRY_B_200K.oldEntry().compressedDataRange().length());
    DeltaFriendlyOldBlobSizeLimiter limiter = new DeltaFriendlyOldBlobSizeLimiter(limit);
    List<PreDiffPlanEntry> expected = new ArrayList<>();
    expected.add(PRE_DIFF_PLAN_ENTRY_B_200K);
    expected.add(PRE_DIFF_PLAN_ENTRY_D_400K);
    expected.addAll(suppressed(PRE_DIFF_PLAN_ENTRY_A_100K, PRE_DIFF_PLAN_ENTRY_C_300K));
    expected.addAll(ALL_IGNORED_PRE_DIFF_PLAN_ENTRIES);
    assertThat(
            limiter.getModifiedPreDiffPlanEntries(
                tempFileBlob, tempFileBlob, ALL_PRE_DIFF_PLAN_ENTRIES))
        .containsExactlyElementsIn(expected);
  }
}
