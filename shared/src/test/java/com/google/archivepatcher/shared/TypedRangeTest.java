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

package com.google.archivepatcher.shared;

import static com.google.common.truth.Truth.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link TypedRange}.
 */
@RunWith(JUnit4.class)
@SuppressWarnings("javadoc")
public class TypedRangeTest {

  @Test
  public void testGetters() {
    String text = "hello";
    TypedRange<String> range = TypedRange.of(555, 777, text);
    assertThat(range.offset()).isEqualTo(555);
    assertThat(range.length()).isEqualTo(777);
    assertThat(text).isSameInstanceAs(range.getMetadata());
  }

  @Test
  public void testToString() {
    // Just make sure this doesn't crash.
    TypedRange<String> range = TypedRange.of(555, 777, "woohoo");
    assertThat(range.toString()).isNotNull();
    assertThat(range.toString()).isNotEmpty();
  }

  @Test
  @SuppressWarnings("SelfComparison") // self comparison is intentional here for testing compareTo.
  public void testOffsetComparator() {
    TypedRange<String> range1 = TypedRange.of(1, 777, null);
    TypedRange<String> range2 = TypedRange.of(2, 777, null);
    assertThat(Range.offsetComparator().compare(range1, range2)).isLessThan(0);
    assertThat(Range.offsetComparator().compare(range2, range1)).isGreaterThan(0);
    assertThat(Range.offsetComparator().compare(range1, range1)).isEqualTo(0);
  }

  @Test
  public void testHashCode() {
    TypedRange<String> range1a = TypedRange.of(123, 456, "hi mom");
    TypedRange<String> range1b = TypedRange.of(123, 456, "hi mom");
    assertThat(range1b.hashCode()).isEqualTo(range1a.hashCode());
    Set<Integer> hashCodes = new HashSet<>();
    hashCodes.add(range1a.hashCode());
    hashCodes.add(TypedRange.of(123 + 1, 456, "hi mom").hashCode()); // offset changed
    hashCodes.add(TypedRange.of(123, 456 + 1, "hi mom").hashCode()); // length changed
    hashCodes.add(TypedRange.of(123 + 1, 456, "x").hashCode()); // metadata changed
    hashCodes.add(TypedRange.of(123 + 1, 456, null).hashCode()); // no metadata at all
    // Assert that all 4 hash codes are unique
    assertThat(hashCodes).hasSize(5);
  }

  @Test
  public void testEquals() {
    TypedRange<String> range1a = TypedRange.of(123, 456, "hi mom");
    TypedRange<String> range1b = TypedRange.of(123, 456, "hi mom");
    assertThat(range1b).isEqualTo(range1a); // equality case
    assertThat(range1a).isNotEqualTo(TypedRange.of(123 + 1, 456, "hi mom")); // offset
    assertThat(range1a).isNotEqualTo(TypedRange.of(123, 456 + 1, "hi mom")); // length
    assertThat(range1a).isNotEqualTo(TypedRange.of(123, 456, "foo")); // metadata
    assertThat(range1a).isNotEqualTo(TypedRange.of(123, 456, null)); // no metadata
    assertThat(TypedRange.of(123, 456, null)).isNotEqualTo(range1a); // other code branch
    assertThat(TypedRange.of(123, 456, null))
        .isEqualTo(TypedRange.of(123, 456, null)); // both with null metadata
  }
}
