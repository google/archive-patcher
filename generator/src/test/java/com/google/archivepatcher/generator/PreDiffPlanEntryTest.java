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

import static com.google.archivepatcher.generator.PreDiffPlanEntryTestUtils.builderWithCompressedBytesChanged;
import static com.google.common.truth.Truth.assertThat;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link PreDiffPlanEntry}. */
@RunWith(JUnit4.class)
@SuppressWarnings("javadoc")
public class PreDiffPlanEntryTest {
  private static final byte[] FILENAME1 = {'f', 'o', 'o'};
  private static final byte[] FILENAME2 = {'b', 'a', 'r'};
  private static final MinimalZipEntry ENTRY1 = new MinimalZipEntry(0, 1, 2, 3, FILENAME1, true, 0);
  private static final MinimalZipEntry ENTRY2 = new MinimalZipEntry(1, 2, 3, 4, FILENAME2, true, 0);

  private static final PreDiffPlanEntry DEFAULT_QUALIFIED_RECOMMENDATION =
      builderWithCompressedBytesChanged().setZipEntries(ENTRY1, ENTRY2).build();
  private static final PreDiffPlanEntry CLONED_DEFAULT_QUALIFIED_RECOMMENDATION =
      DEFAULT_QUALIFIED_RECOMMENDATION.toBuilder().build();
  private static final PreDiffPlanEntry ALTERED_ENTRY1 =
      builderWithCompressedBytesChanged().setZipEntries(ENTRY2, ENTRY2).build();
  private static final PreDiffPlanEntry ALTERED_ENTRY2 =
      builderWithCompressedBytesChanged().setZipEntries(ENTRY1, ENTRY1).build();
  private static final PreDiffPlanEntry ALTERED_RECOMMENDATION =
      PreDiffPlanEntry.builder()
          .setZipEntries(ENTRY1, ENTRY2)
          .setUncompressionOption(
              ZipEntryUncompressionOption.UNCOMPRESS_NEITHER,
              UncompressionOptionExplanation.COMPRESSED_BYTES_CHANGED)
          .build();
  private static final PreDiffPlanEntry ALTERED_REASON =
      PreDiffPlanEntry.builder()
          .setZipEntries(ENTRY1, ENTRY2)
          .setUncompressionOption(
              ZipEntryUncompressionOption.UNCOMPRESS_BOTH,
              UncompressionOptionExplanation.UNSUITABLE)
          .build();
  private static final List<PreDiffPlanEntry> ALL_MUTATIONS =
      Collections.unmodifiableList(
          Arrays.asList(ALTERED_ENTRY1, ALTERED_ENTRY2, ALTERED_RECOMMENDATION, ALTERED_REASON));

  @Test
  @SuppressWarnings({"EqualsIncompatibleType", "TruthSelfEquals"}) // For ErrorProne
  public void testEquals() {
    assertThat(DEFAULT_QUALIFIED_RECOMMENDATION).isEqualTo(DEFAULT_QUALIFIED_RECOMMENDATION);
    assertThat(CLONED_DEFAULT_QUALIFIED_RECOMMENDATION).isEqualTo(DEFAULT_QUALIFIED_RECOMMENDATION);
    assertThat(DEFAULT_QUALIFIED_RECOMMENDATION)
        .isNotSameAs(CLONED_DEFAULT_QUALIFIED_RECOMMENDATION);
    for (PreDiffPlanEntry mutation : ALL_MUTATIONS) {
      assertThat(mutation).isNotEqualTo(DEFAULT_QUALIFIED_RECOMMENDATION);
    }
    assertThat(DEFAULT_QUALIFIED_RECOMMENDATION.equals(null)).isFalse();
    assertThat(DEFAULT_QUALIFIED_RECOMMENDATION.equals("foo")).isFalse();
  }

  @Test
  public void testHashCode() {
    Set<PreDiffPlanEntry> hashSet = new HashSet<>();
    hashSet.add(DEFAULT_QUALIFIED_RECOMMENDATION);
    hashSet.add(CLONED_DEFAULT_QUALIFIED_RECOMMENDATION);
    assertThat(hashSet).hasSize(1);
    hashSet.addAll(ALL_MUTATIONS);
    assertThat(hashSet).hasSize(1 + ALL_MUTATIONS.size());
  }

  @Test
  public void testGetters() {
    assertThat(DEFAULT_QUALIFIED_RECOMMENDATION.getOldEntry()).isEqualTo(ENTRY1);
    assertThat(DEFAULT_QUALIFIED_RECOMMENDATION.getNewEntry()).isEqualTo(ENTRY2);
    assertThat(DEFAULT_QUALIFIED_RECOMMENDATION.getZipEntryUncompressionOption())
        .isEqualTo(ZipEntryUncompressionOption.UNCOMPRESS_BOTH);
    assertThat(DEFAULT_QUALIFIED_RECOMMENDATION.getUncompressionOptionExplanation())
        .isEqualTo(UncompressionOptionExplanation.COMPRESSED_BYTES_CHANGED);
  }
}
