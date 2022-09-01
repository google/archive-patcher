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

import com.google.archivepatcher.shared.bytesource.ByteSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Limits the size of the delta-friendly old blob, which is an implicit limitation on the amount of
 * temp space required to apply a patch.
 *
 * <p>This class implements the following algorithm:
 *
 * <ol>
 *   <li>Check the size of the old archive and subtract it from the maximum size, this is the number
 *       of bytes that can be used to uncompress entries in the delta-friendly old file.
 *   <li>Identify all of the {@link PreDiffPlanEntry}s that have {@link
 *       ZipEntryUncompressionOption#uncompressOldEntry} set to <code>true</code>. These identify
 *       all the entries that would be uncompressed in the delta-friendly old file.
 *   <li>Sort those {@link PreDiffPlanEntry}s in order of decreasing uncompressed size.
 *   <li>Iterate over the list in order. For each entry, calculate the difference between the
 *       uncompressed size and the compressed size; this is the number of bytes that would be
 *       consumed to transform the data from compressed to uncompressed in the delta-friendly old
 *       file. If the number of bytes that would be consumed is less than the number of bytes
 *       remaining before hitting the cap, retain it; else, discard it.
 *   <li>Return the resulting list of the retained entries. Note that the order of this list may not
 *       be the same as the input order (i.e., it has been sorted in order of decreasing compressed
 *       size).
 * </ol>
 */
public class DeltaFriendlyOldBlobSizeLimiter implements PreDiffPlanEntryModifier {

  /** The maximum size of the delta-friendly old blob. */
  private final long maxSizeBytes;

  private static final Comparator<PreDiffPlanEntry> COMPARATOR =
      new UncompressedOldEntrySizeComparator();

  /**
   * Create a new limiter that will restrict the total size of the delta-friendly old blob.
   *
   * @param maxSizeBytes the maximum size of the delta-friendly old blob
   */
  public DeltaFriendlyOldBlobSizeLimiter(long maxSizeBytes) {
    if (maxSizeBytes < 0) {
      throw new IllegalArgumentException("maxSizeBytes must be non-negative: " + maxSizeBytes);
    }
    this.maxSizeBytes = maxSizeBytes;
  }

  @Override
  public List<PreDiffPlanEntry> getModifiedPreDiffPlanEntries(
      ByteSource oldFile, ByteSource newFile, List<PreDiffPlanEntry> originalEntries) {

    List<PreDiffPlanEntry> sorted = sortPreDiffPlanEntries(originalEntries);

    List<PreDiffPlanEntry> result = new ArrayList<>(sorted.size());
    long bytesRemaining = maxSizeBytes - oldFile.length();
    for (PreDiffPlanEntry originalEntry : sorted) {
      if (!originalEntry.zipEntryUncompressionOption().uncompressOldEntry) {
        // Keep the original entry, no need to track size since it won't be uncompressed.
        result.add(originalEntry);
      } else {
        long extraBytesConsumed =
            originalEntry.oldEntry().uncompressedSize()
                - originalEntry.oldEntry().compressedDataRange().length();
        if (bytesRemaining - extraBytesConsumed >= 0) {
          // Keep the original entry, but also subtract from the remaining space.
          result.add(originalEntry);
          bytesRemaining -= extraBytesConsumed;
        } else {
          // Update the entry to prevent uncompressing this tuple.
          result.add(
              originalEntry.toBuilder()
                  .zipEntryUncompressionOption(ZipEntryUncompressionOption.UNCOMPRESS_NEITHER)
                  .uncompressionOptionExplanation(
                      UncompressionOptionExplanation.RESOURCE_CONSTRAINED)
                  .deltaFormat(PreDiffPlanner.DEFAULT_DELTA_FORMAT)
                  .deltaFormatExplanation(DeltaFormatExplanation.RESOURCE_CONSTRAINED)
                  .build());
        }
      }
    }
    return result;
  }
  /**
   * Returns a copy of the given {@code PreDiffPlanEntry} ordered descending by the uncompressed
   * size of the old entry.
   */
  private static List<PreDiffPlanEntry> sortPreDiffPlanEntries(
      List<PreDiffPlanEntry> originalEntries) {
    List<PreDiffPlanEntry> sorted = new ArrayList<PreDiffPlanEntry>(originalEntries);
    Collections.sort(sorted, Collections.reverseOrder(COMPARATOR));
    return sorted;
  }

  /** Helper class implementing the sort order described in the class documentation. */
  private static class UncompressedOldEntrySizeComparator implements Comparator<PreDiffPlanEntry> {
    @Override
    public int compare(PreDiffPlanEntry e1, PreDiffPlanEntry e2) {
      return Long.compare(e1.oldEntry().uncompressedSize(), e2.oldEntry().uncompressedSize());
    }
  }
}
