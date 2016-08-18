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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link QualifiedRecommendation}. */
@RunWith(JUnit4.class)
@SuppressWarnings("javadoc")
public class QualifiedRecommendationTest {
  private static final byte[] FILENAME1 = {'f', 'o', 'o'};
  private static final byte[] FILENAME2 = {'b', 'a', 'r'};
  private static final MinimalZipEntry ENTRY1 = new MinimalZipEntry(0, 1, 2, 3, FILENAME1, true, 0);
  private static final MinimalZipEntry ENTRY2 = new MinimalZipEntry(1, 2, 3, 4, FILENAME2, true, 0);

  private static final QualifiedRecommendation DEFAULT_QUALIFIED_RECOMMENDATION =
      new QualifiedRecommendation(
          ENTRY1,
          ENTRY2,
          Recommendation.UNCOMPRESS_BOTH,
          RecommendationReason.COMPRESSED_BYTES_CHANGED);
  private static final QualifiedRecommendation CLONED_DEFAULT_QUALIFIED_RECOMMENDATION =
      new QualifiedRecommendation(
          ENTRY1,
          ENTRY2,
          Recommendation.UNCOMPRESS_BOTH,
          RecommendationReason.COMPRESSED_BYTES_CHANGED);
  private static final QualifiedRecommendation ALTERED_ENTRY1 =
      new QualifiedRecommendation(
          ENTRY2,
          ENTRY2,
          Recommendation.UNCOMPRESS_BOTH,
          RecommendationReason.COMPRESSED_BYTES_CHANGED);
  private static final QualifiedRecommendation ALTERED_ENTRY2 =
      new QualifiedRecommendation(
          ENTRY1,
          ENTRY1,
          Recommendation.UNCOMPRESS_BOTH,
          RecommendationReason.COMPRESSED_BYTES_CHANGED);
  private static final QualifiedRecommendation ALTERED_RECOMMENDATION =
      new QualifiedRecommendation(
          ENTRY1,
          ENTRY2,
          Recommendation.UNCOMPRESS_NEITHER,
          RecommendationReason.COMPRESSED_BYTES_CHANGED);
  private static final QualifiedRecommendation ALTERED_REASON =
      new QualifiedRecommendation(
          ENTRY1, ENTRY2, Recommendation.UNCOMPRESS_BOTH, RecommendationReason.UNSUITABLE);
  private static final List<QualifiedRecommendation> ALL_MUTATIONS =
      Collections.unmodifiableList(
          Arrays.asList(ALTERED_ENTRY1, ALTERED_ENTRY2, ALTERED_RECOMMENDATION, ALTERED_REASON));

  @Test
  @SuppressWarnings("EqualsIncompatibleType") // For ErrorProne
  public void testEquals() {
    Assert.assertEquals(DEFAULT_QUALIFIED_RECOMMENDATION, DEFAULT_QUALIFIED_RECOMMENDATION);
    Assert.assertEquals(DEFAULT_QUALIFIED_RECOMMENDATION, CLONED_DEFAULT_QUALIFIED_RECOMMENDATION);
    Assert.assertNotSame(DEFAULT_QUALIFIED_RECOMMENDATION, CLONED_DEFAULT_QUALIFIED_RECOMMENDATION);
    for (QualifiedRecommendation mutation : ALL_MUTATIONS) {
      Assert.assertNotEquals(DEFAULT_QUALIFIED_RECOMMENDATION, mutation);
    }
    Assert.assertFalse(DEFAULT_QUALIFIED_RECOMMENDATION.equals(null));
    Assert.assertFalse(DEFAULT_QUALIFIED_RECOMMENDATION.equals("foo"));
  }

  @Test
  public void testHashCode() {
    Set<QualifiedRecommendation> hashSet = new HashSet<>();
    hashSet.add(DEFAULT_QUALIFIED_RECOMMENDATION);
    hashSet.add(CLONED_DEFAULT_QUALIFIED_RECOMMENDATION);
    Assert.assertEquals(1, hashSet.size());
    hashSet.addAll(ALL_MUTATIONS);
    Assert.assertEquals(1 + ALL_MUTATIONS.size(), hashSet.size());
  }

  @Test
  public void testGetters() {
    Assert.assertEquals(ENTRY1, DEFAULT_QUALIFIED_RECOMMENDATION.getOldEntry());
    Assert.assertEquals(ENTRY2, DEFAULT_QUALIFIED_RECOMMENDATION.getNewEntry());
    Assert.assertEquals(
        Recommendation.UNCOMPRESS_BOTH, DEFAULT_QUALIFIED_RECOMMENDATION.getRecommendation());
    Assert.assertEquals(
        RecommendationReason.COMPRESSED_BYTES_CHANGED,
        DEFAULT_QUALIFIED_RECOMMENDATION.getReason());
  }
}
