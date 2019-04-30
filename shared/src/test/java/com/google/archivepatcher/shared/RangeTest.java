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

import static com.google.archivepatcher.shared.Range.combine;
import static com.google.archivepatcher.shared.TestUtils.assertThrows;
import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test for {@link Range}. */
@RunWith(JUnit4.class)
public class RangeTest {

  @Test
  public void endOffset() {
    Range range = Range.of(/* offset= */ 100, /* length= */ 100);
    assertThat(range.endOffset()).isEqualTo(200);
  }

  @Test
  public void combine_nonAdjacent() {
    Range range1 = Range.of(/* offset= */ 100, /* length= */ 100);
    Range range2 = Range.of(/* offset= */ 300, /* length= */ 100);

    assertThrows(IllegalArgumentException.class, () -> combine(range1, range2));
    assertThrows(IllegalArgumentException.class, () -> combine(range2, range1));
  }

  @Test
  public void combine_adjacent() {
    Range range1 = Range.of(/* offset= */ 100, /* length= */ 100);
    Range range2 = Range.of(/* offset= */ 200, /* length= */ 100);
    Range rangeExpected = Range.of(/* offset= */ 100, /* length= */ 200);

    assertThat(combine(range1, range2)).isEqualTo(rangeExpected);
    assertThat(combine(range2, range1)).isEqualTo(rangeExpected);
  }
}
