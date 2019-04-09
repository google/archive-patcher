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

import static com.google.archivepatcher.shared.TestUtils.assertThrows;

import com.google.archivepatcher.shared.JreDeflateParameters;
import com.google.archivepatcher.shared.Range;
import com.google.archivepatcher.shared.TypedRange;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link PreDiffPlan}.
 */
@RunWith(JUnit4.class)
@SuppressWarnings("javadoc")
public class PreDiffPlanTest {
  private static final List<Range> SORTED_VOID_LIST =
      Collections.unmodifiableList(Arrays.asList(Range.of(0, 1), Range.of(1, 1)));
  private static final List<TypedRange<JreDeflateParameters>> SORTED_DEFLATE_LIST =
      Collections.unmodifiableList(
          Arrays.asList(
              TypedRange.of(0, 1, JreDeflateParameters.of(1, 0, true)),
              TypedRange.of(1, 1, JreDeflateParameters.of(1, 0, true))));

  private <T> List<T> reverse(List<T> list) {
    List<T> reversed = new ArrayList<T>(list);
    Collections.reverse(reversed);
    return reversed;
  }

  @Test
  public void testConstructor_OrderOK() {
    new PreDiffPlan(Collections.emptyList(), SORTED_VOID_LIST, SORTED_DEFLATE_LIST);
  }

  @Test
  public void testConstructor_OldFileUncompressionOrderNotOK() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new PreDiffPlan(
                Collections.emptyList(),
                reverse(SORTED_VOID_LIST),
                SORTED_DEFLATE_LIST,
                SORTED_DEFLATE_LIST));
  }

  @Test
  public void testConstructor_NewFileUncompressionOrderNotOK() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new PreDiffPlan(
                Collections.emptyList(),
                SORTED_VOID_LIST,
                reverse(SORTED_DEFLATE_LIST),
                SORTED_DEFLATE_LIST));
  }

  @Test
  public void testConstructor_NewFileRecompressionOrderNotOK() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new PreDiffPlan(
                Collections.emptyList(),
                SORTED_VOID_LIST,
                SORTED_DEFLATE_LIST,
                reverse(SORTED_DEFLATE_LIST)));
  }
}
