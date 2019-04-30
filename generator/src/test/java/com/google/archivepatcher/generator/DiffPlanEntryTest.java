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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.archivepatcher.shared.PatchConstants.DeltaFormat;
import com.google.archivepatcher.shared.Range;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test for {@link DiffPlanEntry}. */
@RunWith(JUnit4.class)
public class DiffPlanEntryTest {
  @Test
  public void asDeltaEntry() {
    DeltaFormat deltaFormat = DeltaFormat.BSDIFF;
    Range oldRange = Range.of(100, 200);
    Range newRange = Range.of(300, 400);
    PreDiffPlanEntry mockPreDiffPlanEntry = mock(PreDiffPlanEntry.class);
    when(mockPreDiffPlanEntry.deltaFormat()).thenReturn(deltaFormat);
    DeltaEntry expectedEntry =
        DeltaEntry.builder()
            .deltaFormat(deltaFormat)
            .oldBlobRange(oldRange)
            .newBlobRange(newRange)
            .build();
    DiffPlanEntry diffPlanEntry = DiffPlanEntry.create(mockPreDiffPlanEntry, oldRange, newRange);

    assertThat(diffPlanEntry.asDeltaEntry()).isEqualTo(expectedEntry);
  }
}
