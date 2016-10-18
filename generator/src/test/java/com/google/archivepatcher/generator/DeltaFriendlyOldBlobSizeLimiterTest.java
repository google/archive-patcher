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

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link DeltaFriendlyOldBlobSizeLimiter}. */
@RunWith(JUnit4.class)
@SuppressWarnings("javadoc")
public class DeltaFriendlyOldBlobSizeLimiterTest {
  private static final int DEFLATE_COMPRESSION_METHOD = 8;

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

  // First four recommendations are all ones where uncompression of the old resource is required.
  // Note that there is a mix of UNCOMPRESS_OLD and UNCOMPRESS_BOTH, both of which will have the
  // "old" entry flagged for uncompression (i.e., should be relevant to the filtering logic).
  private static final QualifiedRecommendation REC_A_100K =
      new QualifiedRecommendation(
          ENTRY_A_100K,
          UNIMPORTANT,
          Recommendation.UNCOMPRESS_BOTH,
          RecommendationReason.COMPRESSED_BYTES_CHANGED);
  private static final QualifiedRecommendation REC_B_200K =
      new QualifiedRecommendation(
          ENTRY_B_200K,
          UNIMPORTANT,
          Recommendation.UNCOMPRESS_OLD,
          RecommendationReason.UNCOMPRESSED_CHANGED_TO_COMPRESSED);
  private static final QualifiedRecommendation REC_C_300K =
      new QualifiedRecommendation(
          ENTRY_C_300K,
          UNIMPORTANT,
          Recommendation.UNCOMPRESS_BOTH,
          RecommendationReason.COMPRESSED_BYTES_CHANGED);
  private static final QualifiedRecommendation REC_D_400K =
      new QualifiedRecommendation(
          ENTRY_D_400K,
          UNIMPORTANT,
          Recommendation.UNCOMPRESS_BOTH,
          RecommendationReason.COMPRESSED_CHANGED_TO_UNCOMPRESSED);

  // Remaining recommendations are all ones where recompression is NOT required. Note the mixture of
  // UNCOMPRESS_NEITHER and UNCOMPRESS_OLD, neither of which will have the "new" entry flagged for
  // recompression (ie., must be ignored by the filtering logic).
  private static final QualifiedRecommendation REC_IGNORED_A_UNCHANGED =
      new QualifiedRecommendation(
          IGNORED_A,
          UNIMPORTANT,
          Recommendation.UNCOMPRESS_NEITHER,
          RecommendationReason.COMPRESSED_BYTES_IDENTICAL);
  private static final QualifiedRecommendation REC_IGNORED_B_BOTH_UNCOMPRESSED =
      new QualifiedRecommendation(
          IGNORED_B,
          UNIMPORTANT,
          Recommendation.UNCOMPRESS_NEITHER,
          RecommendationReason.BOTH_ENTRIES_UNCOMPRESSED);
  private static final QualifiedRecommendation REC_IGNORED_C_UNSUITABLE =
      new QualifiedRecommendation(
          IGNORED_C,
          UNIMPORTANT,
          Recommendation.UNCOMPRESS_NEITHER,
          RecommendationReason.UNSUITABLE);

  /** Convenience reference to all the recommendations that should be ignored by filtering. */
  private static final List<QualifiedRecommendation> ALL_IGNORED_RECS =
      Collections.unmodifiableList(
          Arrays.asList(
              REC_IGNORED_A_UNCHANGED, REC_IGNORED_B_BOTH_UNCOMPRESSED, REC_IGNORED_C_UNSUITABLE));

  /** Convenience reference to all the recommendations that are subject to filtering. */
  private static final List<QualifiedRecommendation> ALL_RECS =
      Collections.unmodifiableList(
          Arrays.asList(
              REC_IGNORED_A_UNCHANGED,
              REC_A_100K,
              REC_IGNORED_B_BOTH_UNCOMPRESSED,
              REC_D_400K,
              REC_IGNORED_C_UNSUITABLE,
              REC_B_200K,
              REC_C_300K));

  /**
   * Make a structurally valid but totally bogus {@link MinimalZipEntry} for the purpose of testing
   * the {@link RecommendationModifier}.
   *
   * @param path the path to set on the entry, to help with debugging
   * @param compressedSize the compressed size of the entry, in bytes
   * @param uncompressedSize the uncompressed size of the entry, in bytes
   */
  private static MinimalZipEntry makeFakeEntry(
      String path, long compressedSize, long uncompressedSize) {
    try {
      return new MinimalZipEntry(
          DEFLATE_COMPRESSION_METHOD, // == deflate
          0, // crc32OfUncompressedData (ignored for this test)
          compressedSize,
          uncompressedSize,
          path.getBytes("UTF8"),
          true, // generalPurposeFlagBit11 (true=UTF8)
          0 // fileOffsetOfLocalEntry (ignored for this test)
          );
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException(e); // Impossible on any modern system
    }
  }

  @Test
  public void testNegativeLimit() {
    try {
      new DeltaFriendlyOldBlobSizeLimiter(-1);
      Assert.fail("Set a negative limit");
    } catch (IllegalArgumentException expected) {
      // Pass
    }
  }

  /**
   * Asserts that the two collections contain exactly the same elements. This isn't as rigorous as
   * it should be, but is ok for this test scenario. Checks the contents but not the iteration order
   * of the collections handed in.
   */
  private static <T> void assertEquivalence(Collection<T> c1, Collection<T> c2) {
    String errorMessage = "Expected " + c1 + " but was " + c2;
    Assert.assertEquals(errorMessage, c1.size(), c2.size());
    Assert.assertTrue(errorMessage, c1.containsAll(c2));
    Assert.assertTrue(errorMessage, c2.containsAll(c1));
  }

  /**
   * Given {@link QualifiedRecommendation}s, manufacture equivalents altered in the way that the
   * {@link DeltaFriendlyOldBlobSizeLimiter} would.
   *
   * @param originals the original recommendations
   * @return the altered recommendations
   */
  private static final List<QualifiedRecommendation> suppressed(
      QualifiedRecommendation... originals) {
    List<QualifiedRecommendation> result = new ArrayList<>(originals.length);
    for (QualifiedRecommendation original : originals) {
      result.add(
          new QualifiedRecommendation(
              original.getOldEntry(),
              original.getNewEntry(),
              Recommendation.UNCOMPRESS_NEITHER,
              RecommendationReason.RESOURCE_CONSTRAINED));
    }
    return result;
  }

  private File tempFile = null;

  @Before
  public void setup() throws IOException {
    // Make an empty file to test the recommender's limitation logic
    tempFile = File.createTempFile("DeltaFriendlyOldBlobSizeLimiterTest", "test");
    tempFile.deleteOnExit();
  }

  @After
  public void tearDown() {
    tempFile.delete();
  }

  @Test
  public void testZeroLimit() {
    DeltaFriendlyOldBlobSizeLimiter limiter = new DeltaFriendlyOldBlobSizeLimiter(0);
    List<QualifiedRecommendation> expected = new ArrayList<QualifiedRecommendation>();
    expected.addAll(suppressed(REC_A_100K, REC_B_200K, REC_C_300K, REC_D_400K));
    expected.addAll(ALL_IGNORED_RECS);
    assertEquivalence(expected, limiter.getModifiedRecommendations(tempFile, tempFile, ALL_RECS));
  }

  @Test
  public void testMaxLimit() {
    DeltaFriendlyOldBlobSizeLimiter limiter = new DeltaFriendlyOldBlobSizeLimiter(Long.MAX_VALUE);
    assertEquivalence(ALL_RECS, limiter.getModifiedRecommendations(tempFile, tempFile, ALL_RECS));
  }

  @Test
  public void testLimit_ExactlySmallest() {
    long limit =
        REC_A_100K.getOldEntry().getUncompressedSize()
            - REC_A_100K.getOldEntry().getCompressedSize(); // Exactly large enough
    DeltaFriendlyOldBlobSizeLimiter limiter = new DeltaFriendlyOldBlobSizeLimiter(limit);
    List<QualifiedRecommendation> expected = new ArrayList<QualifiedRecommendation>();
    expected.add(REC_A_100K);
    expected.addAll(suppressed(REC_B_200K, REC_C_300K, REC_D_400K));
    expected.addAll(ALL_IGNORED_RECS);
    assertEquivalence(expected, limiter.getModifiedRecommendations(tempFile, tempFile, ALL_RECS));
  }

  @Test
  public void testLimit_EdgeUnderSmallest() {
    long limit =
        REC_A_100K.getOldEntry().getUncompressedSize()
            - REC_A_100K.getOldEntry().getCompressedSize()
            - 1; // 1 byte too small
    DeltaFriendlyOldBlobSizeLimiter limiter = new DeltaFriendlyOldBlobSizeLimiter(limit);
    List<QualifiedRecommendation> expected = new ArrayList<QualifiedRecommendation>();
    expected.addAll(suppressed(REC_A_100K, REC_B_200K, REC_C_300K, REC_D_400K));
    expected.addAll(ALL_IGNORED_RECS);
    assertEquivalence(expected, limiter.getModifiedRecommendations(tempFile, tempFile, ALL_RECS));
  }

  @Test
  public void testLimit_EdgeOverSmallest() {
    long limit =
        REC_A_100K.getOldEntry().getUncompressedSize()
            - REC_A_100K.getOldEntry().getCompressedSize()
            + 1; // 1 byte extra room
    DeltaFriendlyOldBlobSizeLimiter limiter = new DeltaFriendlyOldBlobSizeLimiter(limit);
    List<QualifiedRecommendation> expected = new ArrayList<QualifiedRecommendation>();
    expected.add(REC_A_100K);
    expected.addAll(suppressed(REC_B_200K, REC_C_300K, REC_D_400K));
    expected.addAll(ALL_IGNORED_RECS);
    assertEquivalence(expected, limiter.getModifiedRecommendations(tempFile, tempFile, ALL_RECS));
  }

  @Test
  public void testLimit_ExactlyLargest() {
    long limit =
        REC_D_400K.getOldEntry().getUncompressedSize()
            - REC_D_400K.getOldEntry().getCompressedSize(); // Exactly large enough
    DeltaFriendlyOldBlobSizeLimiter limiter = new DeltaFriendlyOldBlobSizeLimiter(limit);
    List<QualifiedRecommendation> expected = new ArrayList<QualifiedRecommendation>();
    expected.add(REC_D_400K);
    expected.addAll(suppressed(REC_A_100K, REC_B_200K, REC_C_300K));
    expected.addAll(ALL_IGNORED_RECS);
    assertEquivalence(expected, limiter.getModifiedRecommendations(tempFile, tempFile, ALL_RECS));
  }

  @Test
  public void testLimit_EdgeUnderLargest() {
    long limit =
        REC_D_400K.getOldEntry().getUncompressedSize()
            - REC_D_400K.getOldEntry().getCompressedSize()
            - 1; // 1 byte too small
    DeltaFriendlyOldBlobSizeLimiter limiter = new DeltaFriendlyOldBlobSizeLimiter(limit);
    List<QualifiedRecommendation> expected = new ArrayList<QualifiedRecommendation>();
    expected.add(REC_C_300K);
    expected.addAll(suppressed(REC_A_100K, REC_B_200K, REC_D_400K));
    expected.addAll(ALL_IGNORED_RECS);
    assertEquivalence(expected, limiter.getModifiedRecommendations(tempFile, tempFile, ALL_RECS));
  }

  @Test
  public void testLimit_EdgeOverLargest() {
    long limit =
        REC_D_400K.getOldEntry().getUncompressedSize()
            - REC_D_400K.getOldEntry().getCompressedSize()
            + 1; // 1 byte extra room
    DeltaFriendlyOldBlobSizeLimiter limiter = new DeltaFriendlyOldBlobSizeLimiter(limit);
    List<QualifiedRecommendation> expected = new ArrayList<QualifiedRecommendation>();
    expected.add(REC_D_400K);
    expected.addAll(suppressed(REC_A_100K, REC_B_200K, REC_C_300K));
    expected.addAll(ALL_IGNORED_RECS);
    assertEquivalence(expected, limiter.getModifiedRecommendations(tempFile, tempFile, ALL_RECS));
  }

  @Test
  public void testLimit_Complex() {
    // A more nuanced test. Here we set up a limit of 600k - big enough to get the largest and the
    // THIRD largest files. The second largest will fail because there isn't enough space after
    // adding the first largest, and the fourth largest will fail because there is not enough space
    // after adding the third largest. Tricky.
    long limit =
        (REC_D_400K.getOldEntry().getUncompressedSize()
                - REC_D_400K.getOldEntry().getCompressedSize())
            + (REC_B_200K.getOldEntry().getUncompressedSize()
                - REC_B_200K.getOldEntry().getCompressedSize());
    DeltaFriendlyOldBlobSizeLimiter limiter = new DeltaFriendlyOldBlobSizeLimiter(limit);
    List<QualifiedRecommendation> expected = new ArrayList<QualifiedRecommendation>();
    expected.add(REC_B_200K);
    expected.add(REC_D_400K);
    expected.addAll(suppressed(REC_A_100K, REC_C_300K));
    expected.addAll(ALL_IGNORED_RECS);
    assertEquivalence(expected, limiter.getModifiedRecommendations(tempFile, tempFile, ALL_RECS));
  }
}
