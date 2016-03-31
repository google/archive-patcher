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

package com.google.archivepatcher.explainer;

import com.google.archivepatcher.generator.ByteArrayHolder;
import com.google.archivepatcher.generator.RecommendationReason;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Tests for {@link PatchExplanation}.
 */
@RunWith(JUnit4.class)
@SuppressWarnings("javadoc")
public final class PatchExplanationTest {

  // Construct 6 entries:
  //   1 and 2 are classified as new (they don't exist in the old archive).
  //   3 and 4 are changed entries that are not free (there is delta to be calculated)
  //   5 and 6 are unchanged or free (identical uncompressed or compressed bytes)
  // This is enough to populate each of the three lists in the PatchExplanation with 2 entries, and
  // check that ordering and summing is working properly.
  private static final EntryExplanation EXPLANATION_1_NEW =
      makeExplanation("/path1", true, null, 1000);
  private static final EntryExplanation EXPLANATION_2_NEW =
      makeExplanation("/path2", true, null, 2000);
  private static final EntryExplanation EXPLANATION_3_CHANGED_NOT_FREE =
      makeExplanation("/path3", false, RecommendationReason.COMPRESSED_BYTES_CHANGED, 3000);
  private static final EntryExplanation EXPLANATION_4_CHANGED_NOT_FREE =
      makeExplanation("/path4", false, RecommendationReason.BOTH_ENTRIES_UNCOMPRESSED, 4000);
  private static final EntryExplanation EXPLANATION_5_CHANGED_BUT_FREE =
      makeExplanation("/path5", false, RecommendationReason.COMPRESSED_BYTES_CHANGED, 0);
  private static final EntryExplanation EXPLANATION_6_UNCHANGED =
      makeExplanation("/path6", false, RecommendationReason.COMPRESSED_BYTES_IDENTICAL, 0);

  private static final List<EntryExplanation> ALL_EXPLANATIONS =
      Collections.unmodifiableList(
          Arrays.asList(
              EXPLANATION_1_NEW,
              EXPLANATION_2_NEW,
              EXPLANATION_3_CHANGED_NOT_FREE,
              EXPLANATION_4_CHANGED_NOT_FREE,
              EXPLANATION_5_CHANGED_BUT_FREE,
              EXPLANATION_6_UNCHANGED));

  private static final List<EntryExplanation> EXPECTED_NEW_EXPLANATIONS =
      Collections.unmodifiableList(Arrays.asList(EXPLANATION_1_NEW, EXPLANATION_2_NEW));
  private static final long EXPECTED_NEW_SIZE =
      EXPLANATION_1_NEW.getCompressedSizeInPatch() + EXPLANATION_2_NEW.getCompressedSizeInPatch();

  private static final List<EntryExplanation> EXPECTED_CHANGED_EXPLANATIONS =
      Collections.unmodifiableList(
          Arrays.asList(EXPLANATION_3_CHANGED_NOT_FREE, EXPLANATION_4_CHANGED_NOT_FREE));
  private static final long EXPECTED_CHANGED_SIZE =
      EXPLANATION_3_CHANGED_NOT_FREE.getCompressedSizeInPatch()
          + EXPLANATION_4_CHANGED_NOT_FREE.getCompressedSizeInPatch();

  private static final List<EntryExplanation> EXPECTED_UNCHANGED_OR_FREE_EXPLANATIONS =
      Collections.unmodifiableList(
          Arrays.asList(EXPLANATION_5_CHANGED_BUT_FREE, EXPLANATION_6_UNCHANGED));

  private static EntryExplanation makeExplanation(
      String path,
      boolean isNew,
      RecommendationReason reasonIncludedIfNotNew,
      long compressedSizeInPatch) {
    try {
      ByteArrayHolder pathHolder = new ByteArrayHolder(path.getBytes("UTF-8"));
      return new EntryExplanation(pathHolder, isNew, reasonIncludedIfNotNew, compressedSizeInPatch);
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException("System doesn't support UTF-8", e);
    }
  }

  @Test
  public void testConstructor_Simple() {
    PatchExplanation patchExplanation = new PatchExplanation(ALL_EXPLANATIONS);
    Assert.assertEquals(EXPECTED_NEW_EXPLANATIONS, patchExplanation.getExplainedAsNew());
    Assert.assertEquals(EXPECTED_CHANGED_EXPLANATIONS, patchExplanation.getExplainedAsChanged());
    Assert.assertEquals(
        EXPECTED_UNCHANGED_OR_FREE_EXPLANATIONS, patchExplanation.getExplainedAsUnchangedOrFree());
    Assert.assertEquals(EXPECTED_NEW_SIZE, patchExplanation.getEstimatedNewSize());
    Assert.assertEquals(EXPECTED_CHANGED_SIZE, patchExplanation.getEstimatedChangedSize());
  }

  @Test
  public void testConstructor_Reversed() {
    List<EntryExplanation> reversed = new ArrayList<>(ALL_EXPLANATIONS);
    Collections.reverse(reversed);
    PatchExplanation patchExplanation = new PatchExplanation(reversed);
    // Order should remaining the same despite reversing the inputs.
    Assert.assertEquals(EXPECTED_NEW_EXPLANATIONS, patchExplanation.getExplainedAsNew());
    Assert.assertEquals(EXPECTED_CHANGED_EXPLANATIONS, patchExplanation.getExplainedAsChanged());
    Assert.assertEquals(
        EXPECTED_UNCHANGED_OR_FREE_EXPLANATIONS, patchExplanation.getExplainedAsUnchangedOrFree());
    Assert.assertEquals(EXPECTED_NEW_SIZE, patchExplanation.getEstimatedNewSize());
    Assert.assertEquals(EXPECTED_CHANGED_SIZE, patchExplanation.getEstimatedChangedSize());
  }

  @Test
  public void testToJson() throws IOException {
    // We lack a proper JSON parser in the vanilla JRE so short of string matching there's nothing
    // that can be done here other than to ensure the output is non-null and looks kind of sane.
    PatchExplanation patchExplanation = new PatchExplanation(ALL_EXPLANATIONS);
    try (ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(buffer)) {
      patchExplanation.writeJson(writer);
      writer.flush();
      String asString = buffer.toString();
      Assert.assertTrue(asString.startsWith("{"));
      Assert.assertFalse(asString.isEmpty());
      for (EntryExplanation explanation : ALL_EXPLANATIONS) {
        Assert.assertTrue(asString.contains(new String(explanation.getPath().getData(), "UTF-8")));
      }
      Assert.assertTrue(asString.endsWith("}"));
    }
  }
}
