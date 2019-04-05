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
import static com.google.archivepatcher.generator.PreDiffPlanEntryTestUtils.builderWithUncompressedToCompressed;
import static com.google.archivepatcher.generator.PreDiffPlanEntryTestUtils.builderWithUnsuitable;
import static com.google.archivepatcher.generator.PreDiffPlanEntryTestUtils.suppressed;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.archivepatcher.shared.bytesource.ByteSource;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link TotalRecompressionLimiter}. */
@RunWith(JUnit4.class)
@SuppressWarnings("javadoc")
public class TotalRecompressionLimiterTest {

  private static final ByteSource OLD_FILE = null;
  private static final ByteSource NEW_FILE = null;

  private static final MinimalZipEntry UNIMPORTANT = makeFakeEntry("/unimportant", 1337);
  private static final MinimalZipEntry ENTRY_A_100K = makeFakeEntry("/a/100k", 100 * 1024);
  private static final MinimalZipEntry ENTRY_B_200K = makeFakeEntry("/b/200k", 200 * 1024);
  private static final MinimalZipEntry ENTRY_C_300K = makeFakeEntry("/c/300k", 300 * 1024);
  private static final MinimalZipEntry ENTRY_D_400K = makeFakeEntry("/d/400k", 400 * 1024);
  private static final MinimalZipEntry IGNORED_A = makeFakeEntry("/ignored/a", 1234);
  private static final MinimalZipEntry IGNORED_B = makeFakeEntry("/ignored/b", 5678);
  private static final MinimalZipEntry IGNORED_C = makeFakeEntry("/ignored/c", 9101112);
  private static final MinimalZipEntry IGNORED_D = makeFakeEntry("/ignored/d", 13141516);

  // First four entries are all ones where recompression is required. Note that there is a
  // mix of UNCOMPRESS_NEW and UNCOMPRESS_BOTH, both of which will have the "new" entry flagged for
  // recompression (i.e., should be relevant to the filtering logic).
  private static final PreDiffPlanEntry PRE_DIFF_PLAN_ENTRY_A_100K =
      builderWithCompressedBytesChanged().oldEntry(UNIMPORTANT).newEntry(ENTRY_A_100K).build();
  private static final PreDiffPlanEntry PRE_DIFF_PLAN_ENTRY_B_200K =
      builderWithUncompressedToCompressed().oldEntry(UNIMPORTANT).newEntry(ENTRY_B_200K).build();
  private static final PreDiffPlanEntry PRE_DIFF_PLAN_ENTRY_C_300K =
      builderWithCompressedBytesChanged().oldEntry(UNIMPORTANT).newEntry(ENTRY_C_300K).build();
  private static final PreDiffPlanEntry PRE_DIFF_PLAN_ENTRY_D_400K =
      builderWithCompressedBytesChanged().oldEntry(UNIMPORTANT).newEntry(ENTRY_D_400K).build();

  // Remaining entries are all ones where recompression is NOT required. Note the mixture of
  // UNCOMPRESS_NEITHER and UNCOMPRESS_OLD, neither of which will have the "new" entry flagged for
  // recompression (ie., must be ignored by the filtering logic).
  private static final PreDiffPlanEntry PRE_DIFF_PLAN_ENTRY_IGNORED_A_UNCHANGED =
      builderWithCompressedBytesIdentical().oldEntry(UNIMPORTANT).newEntry(IGNORED_A).build();
  private static final PreDiffPlanEntry PRE_DIFF_PLAN_ENTRY_IGNORED_B_BOTH_UNCOMPRESSED =
      builderWithBothEntriesUncompressed().oldEntry(UNIMPORTANT).newEntry(IGNORED_B).build();
  private static final PreDiffPlanEntry PRE_DIFF_PLAN_ENTRY_IGNORED_C_UNSUITABLE =
      builderWithUnsuitable().oldEntry(UNIMPORTANT).newEntry(IGNORED_C).build();
  private static final PreDiffPlanEntry PRE_DIFF_PLAN_ENTRY_IGNORED_D_CHANGED_TO_UNCOMPRESSED =
      builderWithCompressedToUncompressed().oldEntry(UNIMPORTANT).newEntry(IGNORED_D).build();

  /** Convenience reference to all the entries that should be ignored by filtering. */
  private static final List<PreDiffPlanEntry> ALL_IGNORED_PRE_DIFF_PLAN_ENTRIES =
      Collections.unmodifiableList(
          Arrays.asList(
              PRE_DIFF_PLAN_ENTRY_IGNORED_A_UNCHANGED,
              PRE_DIFF_PLAN_ENTRY_IGNORED_B_BOTH_UNCOMPRESSED,
              PRE_DIFF_PLAN_ENTRY_IGNORED_C_UNSUITABLE,
              PRE_DIFF_PLAN_ENTRY_IGNORED_D_CHANGED_TO_UNCOMPRESSED));

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
              PRE_DIFF_PLAN_ENTRY_IGNORED_D_CHANGED_TO_UNCOMPRESSED,
              PRE_DIFF_PLAN_ENTRY_C_300K));

  /**
   * Make a structurally valid but totally bogus {@link MinimalZipEntry} for the purpose of testing
   * the {@link PreDiffPlanEntryModifier}.
   *
   * @param path the path to set on the entry, to help with debugging
   * @param uncompressedSize the uncompressed size of the entry, in bytes
   * @return the entry
   */
  private static MinimalZipEntry makeFakeEntry(String path, long uncompressedSize) {
    try {
      return getFakeBuilder()
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
      new TotalRecompressionLimiter(-1);
      assertWithMessage("Set a negative limit").fail();
    } catch (IllegalArgumentException expected) {
      // Pass
    }
  }

  @Test
  public void testZeroLimit() {
    TotalRecompressionLimiter limiter = new TotalRecompressionLimiter(0);
    List<PreDiffPlanEntry> expected = new ArrayList<>();
    expected.addAll(
        suppressed(
            PRE_DIFF_PLAN_ENTRY_A_100K,
            PRE_DIFF_PLAN_ENTRY_B_200K,
            PRE_DIFF_PLAN_ENTRY_C_300K,
            PRE_DIFF_PLAN_ENTRY_D_400K));
    expected.addAll(ALL_IGNORED_PRE_DIFF_PLAN_ENTRIES);
    assertThat(limiter.getModifiedPreDiffPlanEntries(OLD_FILE, NEW_FILE, ALL_PRE_DIFF_PLAN_ENTRIES))
        .containsExactlyElementsIn(expected);
  }

  @Test
  public void testMaxLimit() {
    TotalRecompressionLimiter limiter = new TotalRecompressionLimiter(Long.MAX_VALUE);
    assertThat(limiter.getModifiedPreDiffPlanEntries(OLD_FILE, NEW_FILE, ALL_PRE_DIFF_PLAN_ENTRIES))
        .containsExactlyElementsIn(ALL_PRE_DIFF_PLAN_ENTRIES);
  }

  @Test
  public void testLimit_ExactlySmallest() {
    long limit = PRE_DIFF_PLAN_ENTRY_A_100K.newEntry().uncompressedSize(); // Exactly large enough
    TotalRecompressionLimiter limiter = new TotalRecompressionLimiter(limit);
    List<PreDiffPlanEntry> expected = new ArrayList<>();
    expected.add(PRE_DIFF_PLAN_ENTRY_A_100K);
    expected.addAll(
        suppressed(
            PRE_DIFF_PLAN_ENTRY_B_200K, PRE_DIFF_PLAN_ENTRY_C_300K, PRE_DIFF_PLAN_ENTRY_D_400K));
    expected.addAll(ALL_IGNORED_PRE_DIFF_PLAN_ENTRIES);
    assertThat(limiter.getModifiedPreDiffPlanEntries(OLD_FILE, NEW_FILE, ALL_PRE_DIFF_PLAN_ENTRIES))
        .containsExactlyElementsIn(expected);
  }

  @Test
  public void testLimit_EdgeUnderSmallest() {
    long limit = PRE_DIFF_PLAN_ENTRY_A_100K.newEntry().uncompressedSize() - 1; // 1 byte too small
    TotalRecompressionLimiter limiter = new TotalRecompressionLimiter(limit);
    List<PreDiffPlanEntry> expected = new ArrayList<>();
    expected.addAll(
        suppressed(
            PRE_DIFF_PLAN_ENTRY_A_100K,
            PRE_DIFF_PLAN_ENTRY_B_200K,
            PRE_DIFF_PLAN_ENTRY_C_300K,
            PRE_DIFF_PLAN_ENTRY_D_400K));
    expected.addAll(ALL_IGNORED_PRE_DIFF_PLAN_ENTRIES);
    assertThat(limiter.getModifiedPreDiffPlanEntries(OLD_FILE, NEW_FILE, ALL_PRE_DIFF_PLAN_ENTRIES))
        .containsExactlyElementsIn(expected);
  }

  @Test
  public void testLimit_EdgeOverSmallest() {
    long limit = PRE_DIFF_PLAN_ENTRY_A_100K.newEntry().uncompressedSize() + 1; // 1 byte extra room
    TotalRecompressionLimiter limiter = new TotalRecompressionLimiter(limit);
    List<PreDiffPlanEntry> expected = new ArrayList<>();
    expected.add(PRE_DIFF_PLAN_ENTRY_A_100K);
    expected.addAll(
        suppressed(
            PRE_DIFF_PLAN_ENTRY_B_200K, PRE_DIFF_PLAN_ENTRY_C_300K, PRE_DIFF_PLAN_ENTRY_D_400K));
    expected.addAll(ALL_IGNORED_PRE_DIFF_PLAN_ENTRIES);
    assertThat(limiter.getModifiedPreDiffPlanEntries(OLD_FILE, NEW_FILE, ALL_PRE_DIFF_PLAN_ENTRIES))
        .containsExactlyElementsIn(expected);
  }

  @Test
  public void testLimit_ExactlyLargest() {
    long limit = PRE_DIFF_PLAN_ENTRY_D_400K.newEntry().uncompressedSize(); // Exactly large enough
    TotalRecompressionLimiter limiter = new TotalRecompressionLimiter(limit);
    List<PreDiffPlanEntry> expected = new ArrayList<>();
    expected.add(PRE_DIFF_PLAN_ENTRY_D_400K);
    expected.addAll(
        suppressed(
            PRE_DIFF_PLAN_ENTRY_A_100K, PRE_DIFF_PLAN_ENTRY_B_200K, PRE_DIFF_PLAN_ENTRY_C_300K));
    expected.addAll(ALL_IGNORED_PRE_DIFF_PLAN_ENTRIES);
    assertThat(limiter.getModifiedPreDiffPlanEntries(OLD_FILE, NEW_FILE, ALL_PRE_DIFF_PLAN_ENTRIES))
        .containsExactlyElementsIn(expected);
  }

  @Test
  public void testLimit_EdgeUnderLargest() {
    long limit = PRE_DIFF_PLAN_ENTRY_D_400K.newEntry().uncompressedSize() - 1; // 1 byte too small
    TotalRecompressionLimiter limiter = new TotalRecompressionLimiter(limit);
    List<PreDiffPlanEntry> expected = new ArrayList<>();
    expected.add(PRE_DIFF_PLAN_ENTRY_C_300K);
    expected.addAll(
        suppressed(
            PRE_DIFF_PLAN_ENTRY_A_100K, PRE_DIFF_PLAN_ENTRY_B_200K, PRE_DIFF_PLAN_ENTRY_D_400K));
    expected.addAll(ALL_IGNORED_PRE_DIFF_PLAN_ENTRIES);
    assertThat(limiter.getModifiedPreDiffPlanEntries(OLD_FILE, NEW_FILE, ALL_PRE_DIFF_PLAN_ENTRIES))
        .containsExactlyElementsIn(expected);
  }

  @Test
  public void testLimit_EdgeOverLargest() {
    long limit = PRE_DIFF_PLAN_ENTRY_D_400K.newEntry().uncompressedSize() + 1; // 1 byte extra room
    TotalRecompressionLimiter limiter = new TotalRecompressionLimiter(limit);
    List<PreDiffPlanEntry> expected = new ArrayList<>();
    expected.add(PRE_DIFF_PLAN_ENTRY_D_400K);
    expected.addAll(
        suppressed(
            PRE_DIFF_PLAN_ENTRY_A_100K, PRE_DIFF_PLAN_ENTRY_B_200K, PRE_DIFF_PLAN_ENTRY_C_300K));
    expected.addAll(ALL_IGNORED_PRE_DIFF_PLAN_ENTRIES);
    assertThat(limiter.getModifiedPreDiffPlanEntries(OLD_FILE, NEW_FILE, ALL_PRE_DIFF_PLAN_ENTRIES))
        .containsExactlyElementsIn(expected);
  }

  @Test
  public void testLimit_Complex() {
    // A more nuanced test. Here we set up a limit of 600k - big enough to get the largest and the
    // THIRD largest files. The second largest will fail because there isn't enough space after
    // adding the first largest, and the fourth largest will fail because there is not enough space
    // after adding the third largest. Tricky.
    long limit =
        PRE_DIFF_PLAN_ENTRY_D_400K.newEntry().uncompressedSize()
            + PRE_DIFF_PLAN_ENTRY_B_200K.newEntry().uncompressedSize();
    TotalRecompressionLimiter limiter = new TotalRecompressionLimiter(limit);
    List<PreDiffPlanEntry> expected = new ArrayList<>();
    expected.add(PRE_DIFF_PLAN_ENTRY_B_200K);
    expected.add(PRE_DIFF_PLAN_ENTRY_D_400K);
    expected.addAll(suppressed(PRE_DIFF_PLAN_ENTRY_A_100K, PRE_DIFF_PLAN_ENTRY_C_300K));
    expected.addAll(ALL_IGNORED_PRE_DIFF_PLAN_ENTRIES);
    assertThat(limiter.getModifiedPreDiffPlanEntries(OLD_FILE, NEW_FILE, ALL_PRE_DIFF_PLAN_ENTRIES))
        .containsExactlyElementsIn(expected);
  }
}
