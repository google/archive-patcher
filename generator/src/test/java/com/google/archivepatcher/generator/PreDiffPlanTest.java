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

import com.google.archivepatcher.shared.JreDeflateParameters;
import com.google.archivepatcher.shared.TypedRange;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Tests for {@link PreDiffPlan}.
 */
@RunWith(JUnit4.class)
@SuppressWarnings("javadoc")
public class PreDiffPlanTest {
  private static final List<TypedRange<Void>> SORTED_VOID_LIST =
      Collections.unmodifiableList(
          Arrays.asList(new TypedRange<Void>(0, 1, null), new TypedRange<Void>(1, 1, null)));
  private static final List<TypedRange<JreDeflateParameters>> SORTED_DEFLATE_LIST =
      Collections.unmodifiableList(
          Arrays.asList(
              new TypedRange<JreDeflateParameters>(0, 1, JreDeflateParameters.of(1, 0, true)),
              new TypedRange<JreDeflateParameters>(1, 1, JreDeflateParameters.of(1, 0, true))));

  private <T> List<T> reverse(List<T> list) {
    List<T> reversed = new ArrayList<T>(list);
    Collections.reverse(reversed);
    return reversed;
  }

  @Test
  public void testConstructor_OrderOK() {
    new PreDiffPlan(
        Collections.<QualifiedRecommendation>emptyList(), SORTED_VOID_LIST, SORTED_DEFLATE_LIST);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testConstructor_OldFileUncompressionOrderNotOK() {
    new PreDiffPlan(
        Collections.<QualifiedRecommendation>emptyList(),
        reverse(SORTED_VOID_LIST),
        SORTED_DEFLATE_LIST,
        SORTED_DEFLATE_LIST);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testConstructor_NewFileUncompressionOrderNotOK() {
    new PreDiffPlan(
        Collections.<QualifiedRecommendation>emptyList(),
        SORTED_VOID_LIST,
        reverse(SORTED_DEFLATE_LIST),
        SORTED_DEFLATE_LIST);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testConstructor_NewFileRecompressionOrderNotOK() {
    new PreDiffPlan(
        Collections.<QualifiedRecommendation>emptyList(),
        SORTED_VOID_LIST,
        SORTED_DEFLATE_LIST,
        reverse(SORTED_DEFLATE_LIST));
  }
}
