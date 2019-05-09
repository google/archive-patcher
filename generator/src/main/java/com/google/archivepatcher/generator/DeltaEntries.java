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

import com.google.archivepatcher.shared.Range;
import com.google.archivepatcher.shared.bytesource.ByteSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Helper class containing methods related to {@link DeltaEntry}. */
public class DeltaEntries {
  /**
   * Given a list of {@link DeltaEntry}s, delta-friendly old and new blob, return a list of {@link
   * DeltaEntry}s such that all the {@link DeltaEntry#newBlobRange()} in the result concatenated
   * together will cover all the bytes in the new delta-friendly blob. In other words, there will be
   * no "gaps" in the delta-friendly new blob.
   *
   * <p>The "base" of diffing for the gaps and the {@link DeltaFormat} to use is up to the
   * implementation.
   *
   * @param entriesWithGaps the input {@link DeltaEntry}s
   * @return a list of non-empty non-overlapping {@link DeltaEntry}s that covers the entire newBlob
   *     and sorted using offset into delta-friendly new file.
   */
  public static List<DeltaEntry> fillGaps(
      List<DeltaEntry> entriesWithGaps, ByteSource oldBlob, ByteSource newBlob) {
    if (entriesWithGaps.isEmpty()) {
      return Arrays.asList(
          DeltaEntry.builder()
              .oldBlobRange(Range.of(0, oldBlob.length()))
              .newBlobRange(Range.of(0, newBlob.length()))
              .deltaFormat(DEFAULT_DELTA_FORMAT)
              .build());
    }

    // Make a copy so we can sort it later.
    entriesWithGaps = new ArrayList<>(entriesWithGaps);

    // Sort them based on the offset into the new file. This is convenient for later processing.
    Collections.sort(
        entriesWithGaps,
        (entry1, entry2) ->
            Range.offsetComparator().compare(entry1.newBlobRange(), entry2.newBlobRange()));

    List<DeltaEntry> entriesWithoutGaps = new ArrayList<>(entriesWithGaps.size());

    // convert "gaps" in new archive into delta entries.
    // The old blob range for this entry doesn't matter and we will just use a zero-length range
    // at the next entry's start offset.
    // The new blob range is just the gap.
    // A default delta format is used. In the future, if this turns out to be inefficient, we
    // can set it to either the previous/next entry's delta format if they support multi-diffing.
    long currentOffset = 0;
    for (DeltaEntry entry : entriesWithGaps) {
      if (entry.newBlobRange().offset() != currentOffset) {
        // there is a gap from currentOffset to the start of the current entry's range.
        DeltaEntry gapEntry =
            DeltaEntry.builder()
                .oldBlobRange(Range.of(entry.oldBlobRange().offset(), 0))
                .newBlobRange(
                    Range.of(currentOffset, entry.newBlobRange().offset() - currentOffset))
                .deltaFormat(DEFAULT_DELTA_FORMAT)
                .build();
        entriesWithoutGaps.add(gapEntry);
      }
      entriesWithoutGaps.add(entry);
      currentOffset = entry.newBlobRange().endOffset();
    }

    // Handle the remaining data after the last entry.
    if (currentOffset != newBlob.length()) {
      entriesWithoutGaps.add(
          DeltaEntry.builder()
              .oldBlobRange(
                  Range.of(
                      entriesWithoutGaps
                          .get(entriesWithoutGaps.size() - 1)
                          .oldBlobRange()
                          .endOffset(),
                      0))
              .newBlobRange(Range.of(currentOffset, newBlob.length() - currentOffset))
              .deltaFormat(DEFAULT_DELTA_FORMAT)
              .build());
    }
    return entriesWithoutGaps;
  }

  /**
   * Given a list of {@link DeltaEntry}s, delta-friendly old and new blob, return a list of {@link
   * DeltaEntry}s such that adjacent {@link DeltaEntry}s that can be combined together are combined.
   *
   * @param entriesWithoutGaps the input {@link DeltaEntry}s. Must be sorted using offset into
   *     delta-friendly new file and ranges into delta-friendly new blob for adjacent entries must
   *     be adjacent. In other words, the entries must "cover" the entire new delta-friendly blob.
   * @return a list of non-overlapping {@link DeltaEntry}s that covers the entire newBlob and sorted
   *     using offset into delta-friendly new file.
   */
  public static List<DeltaEntry> combineEntries(
      List<DeltaEntry> entriesWithoutGaps, ByteSource oldBlob) {
    List<DeltaEntry> combinedEntries = new ArrayList<>(entriesWithoutGaps.size());

    // Combine adjacent entries if possible.
    DeltaEntry currentEntry = null;
    for (DeltaEntry entry : entriesWithoutGaps) {
      if (currentEntry == null) {
        currentEntry = entry;
      } else if (shouldCombineEntry(currentEntry, entry)) {
        currentEntry = combineEntry(currentEntry, entry, oldBlob);
      } else {
        combinedEntries.add(currentEntry);
        currentEntry = entry;
      }
    }
    // handle the last entry
    combinedEntries.add(currentEntry);

    return combinedEntries;
  }

  /**
   * Returns if two {@link DeltaEntry}s should be combined together.
   *
   * <p>This method assumes that the two entries' {@link DeltaEntry#newBlobRange()}s are adjacent.
   */
  private static boolean shouldCombineEntry(DeltaEntry entry1, DeltaEntry entry2) {
    return entry1.deltaFormat() == entry2.deltaFormat()
        && entry1.deltaFormat().supportsMultiEntryDelta;
  }

  /**
   * Returns a new {@link DeltaEntry} by combining the two {@link DeltaEntry}s passed in.
   *
   * <p>This method assumes that the two entries should be combined based on {@link
   * #shouldCombineEntry(DeltaEntry, DeltaEntry)}.
   */
  private static DeltaEntry combineEntry(DeltaEntry entry1, DeltaEntry entry2, ByteSource oldBlob) {
    // Here we greedily diff the range against the entire old archive.
    // This will not make a difference in V1 as we will always be diffing the entire file. In V2,
    // if this turns out to be too expensive on the patch application, we can reduce this range.
    return DeltaEntry.builder()
        .deltaFormat(entry1.deltaFormat())
        .oldBlobRange(Range.of(0, oldBlob.length()))
        .newBlobRange(Range.combine(entry1.newBlobRange(), entry2.newBlobRange()))
        .build();
  }
}
