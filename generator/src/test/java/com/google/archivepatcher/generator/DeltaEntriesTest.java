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

import static com.google.archivepatcher.generator.FileByFileDeltaGenerator.DEFAULT_DELTA_FORMAT;
import static com.google.common.truth.Truth.assertThat;

import com.google.archivepatcher.shared.PatchConstants.DeltaFormat;
import com.google.archivepatcher.shared.Range;
import com.google.archivepatcher.shared.bytesource.ByteSource;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test for {@link DeltaEntries}. */
@RunWith(JUnit4.class)
public class DeltaEntriesTest {

  @Test
  public void fillGaps() {
    // The behaviour of this method should not depend on the data itself.
    byte[] data = new byte[256];
    ByteSource newBlob = ByteSource.wrap(data);
    ByteSource oldBlob = ByteSource.wrap(data);
    List<DeltaEntry> initialEntries =
        ImmutableList.of(
            // [100,200)
            entryWithNewBlobRange(Range.of(100, 100)),
            // [2, 5)
            entryWithNewBlobRange(Range.of(2, 3)),
            // [210,220)
            entryWithNewBlobRange(Range.of(210, 10)));

    List<DeltaEntry> entriesWithNoGaps = DeltaEntries.fillGaps(initialEntries, oldBlob, newBlob);
    assertThat(entriesWithNoGaps).isNotEmpty();

    // We only verify the post condition instead of the entries themselves.
    // There are two conditions:
    // 1. Entries sorted in newBlobRange order
    // 2. No gaps
    long nextExpectedOffset = 0;
    for (DeltaEntry entry : entriesWithNoGaps) {
      assertThat(entry.newBlobRange().offset()).isEqualTo(nextExpectedOffset);
      nextExpectedOffset = entry.newBlobRange().endOffset();
    }
    DeltaEntry lastEntry = entriesWithNoGaps.get(entriesWithNoGaps.size() - 1);
    assertThat(lastEntry.newBlobRange().endOffset()).isEqualTo(data.length);
  }

  @Test
  public void fillGaps_emptyInput() throws Exception {
    // The behaviour of this method should not depend on the data itself.
    byte[] data = new byte[256];
    ByteSource newBlob = ByteSource.wrap(data);
    ByteSource oldBlob = ByteSource.wrap(data);
    DeltaEntry expectedEntry =
        DeltaEntry.builder()
            .oldBlobRange(Range.of(0, oldBlob.length()))
            .newBlobRange(Range.of(0, newBlob.length()))
            .deltaFormat(DEFAULT_DELTA_FORMAT)
            .build();

    List<DeltaEntry> entriesWithNoGaps =
        DeltaEntries.fillGaps(ImmutableList.of(), oldBlob, newBlob);

    assertThat(entriesWithNoGaps).containsExactly(expectedEntry);
  }

  @Test
  public void combineEntries() {
    byte[] data = new byte[256];
    ByteSource oldBlob = ByteSource.wrap(data);
    // As of the time of writing this test, there is only one delta format. Hence this test is kind
    // of trivial (all entries will be combined into one). When more delta formats are added, this
    // test should be amended.
    List<DeltaEntry> initialEntries =
        ImmutableList.of(
            // [0,50)
            entryWithNewBlobRange(Range.of(0, 50)),
            // [50,100)
            fbfEntryWithNewBlobRange(Range.of(50, 50)),
            // [100, 200)
            entryWithNewBlobRange(Range.of(100, 100)),
            // [200,256)
            entryWithNewBlobRange(Range.of(200, 56)));

    List<DeltaEntry> combinedEntries = DeltaEntries.combineEntries(initialEntries, oldBlob);
    assertThat(combinedEntries).isNotEmpty();

    // Similar to fillGaps, we only verify the post conditions instead of the exact returned
    // entries.
    // At this time, the condition on this method is pretty simple: no adjacent entries should have
    // the same delta format with supportsMultipleEntries = True
    // The conditions for fillGaps also apply here:
    // 1. Entries sorted in newBlobRange order
    // 2. No gaps
    long nextExpectedOffset = 0;
    DeltaEntry previousEntry = null;
    for (DeltaEntry entry : combinedEntries) {
      assertThat(entry.newBlobRange().offset()).isEqualTo(nextExpectedOffset);
      nextExpectedOffset = entry.newBlobRange().endOffset();

      // check that the result could not be combined further.
      if (previousEntry != null) {
        boolean couldCombine =
            previousEntry.deltaFormat() == entry.deltaFormat()
                && entry.deltaFormat().supportsMultiEntryDelta;
        assertThat(couldCombine).isFalse();
      }
      previousEntry = entry;
    }
    assertThat(previousEntry.newBlobRange().endOffset()).isEqualTo(data.length);
  }

  private static DeltaEntry entryWithNewBlobRange(Range newBlobRange) {
    return DeltaEntry.builder()
        .newBlobRange(newBlobRange)
        .deltaFormat(DeltaFormat.BSDIFF)
        .oldBlobRange(Range.of(0, 0))
        .build();
  }

  private static DeltaEntry fbfEntryWithNewBlobRange(Range newBlobRange) {
    return DeltaEntry.builder()
        .newBlobRange(newBlobRange)
        .deltaFormat(DeltaFormat.BSDIFF)
        .oldBlobRange(Range.of(0, 0))
        .build();
  }
}
