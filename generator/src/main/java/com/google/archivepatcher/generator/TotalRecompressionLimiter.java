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

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Limits the total amount of recompression to be performed as part of a patch via the {@link
 * RecommendationModifier} interface.
 *
 * <p>This class is useful for helping to establish an upper bound on the amount of work that needs
 * to be done to apply a patch. For example, if the patch is to be applied on a device that can
 * recompress at about 100K/sec and the desire is to keep recompression time to 10 seconds or less,
 * an upper bound of 1000K would be appropriate.
 *
 * <p>Please note that there are many factors involved in the total patch-apply time including, but
 * not limited to, things like the I/O speed of the device applying the patch and the time that is
 * required to apply the delta to the uncompressed content prior to recompressing.
 *
 * <p>This class implements the following algorithm:
 *
 * <ol>
 *   <li>Identify all of the {@link QualifiedRecommendation}s that have {@link
 *       Recommendation#uncompressNewEntry} set to <code>true</code>. These identify all the entries
 *       that have changed and that require recompression.
 *   <li>Sort those {@link QualifiedRecommendation}s in order of decreasing uncompressed size.
 *   <li>Iterate over the list in order. For each entry, if the uncompressed size is less than the
 *       number of uncompressed bytes remaining before hitting the cap, retain it; else, discard it.
 *   <li>Return the resulting list of the retained entries. Note that the order of this list may not
 *       be the same as the input order (i.e., it has been sorted in order of decreasing compressed
 *       size).
 * </ol>
 *
 * This algorithm attempts to preserve the largest changed resources needing recompression, assuming
 * that these are the most likely to be delta-friendly and therefore represent the best patch size
 * savings. This may not be true in <em>all cases</em> but is likely in practice.
 *
 * <p>Please note that this algorithm does <em>not</em> limit the size of the temporary files needed
 * to apply a patch. In particular it does <em>not</em> limit the size of the "delta-friendly old
 * blob" that is generated during the patch-apply step, since that blob may contain an arbitrary
 * amount of compressed resources that are not considered here. To limit the size of the
 * delta-friendly old blob, use a {@link DeltaFriendlyOldBlobSizeLimiter}.
 */
public class TotalRecompressionLimiter implements RecommendationModifier {

  /** The maximum number of bytes to allow to be recompressed. */
  private final long maxBytesToRecompress;

  private static final Comparator<QualifiedRecommendation> COMPARATOR =
      new UncompressedNewEntrySizeComparator();

  /**
   * Create a new limiter that will restrict the total number of bytes that need to be recompressed
   * to the specified quantity.
   *
   * @param maxBytesToRecompress the maximum number of bytes to allow to be recompressed; must be
   *     greater than or equal to zero
   */
  public TotalRecompressionLimiter(long maxBytesToRecompress) {
    if (maxBytesToRecompress < 0) {
      throw new IllegalArgumentException(
          "maxBytesToRecompress must be non-negative: " + maxBytesToRecompress);
    }
    this.maxBytesToRecompress = maxBytesToRecompress;
  }

  @Override
  public List<QualifiedRecommendation> getModifiedRecommendations(
      File oldFile, File newFile, List<QualifiedRecommendation> originalRecommendations) {

    List<QualifiedRecommendation> sorted =
        new ArrayList<QualifiedRecommendation>(originalRecommendations);
    Collections.sort(sorted, COMPARATOR);
    Collections.reverse(sorted);

    List<QualifiedRecommendation> result = new ArrayList<>(sorted.size());
    long recompressibleBytesRemaining = maxBytesToRecompress;
    for (QualifiedRecommendation originalRecommendation : sorted) {
      if (!originalRecommendation.getRecommendation().uncompressNewEntry) {
        // Keep the original recommendation, no need to track size since it won't be uncompressed.
        result.add(originalRecommendation);
      } else {
        long bytesToRecompress = originalRecommendation.getNewEntry().getUncompressedSize();
        if (recompressibleBytesRemaining - bytesToRecompress >= 0) {
          // Keep the original recommendation, but also subtract from the remaining space.
          result.add(originalRecommendation);
          recompressibleBytesRemaining -= bytesToRecompress;
        } else {
          // Update the recommendation to prevent uncompressing this tuple.
          result.add(
              new QualifiedRecommendation(
                  originalRecommendation.getOldEntry(),
                  originalRecommendation.getNewEntry(),
                  Recommendation.UNCOMPRESS_NEITHER,
                  RecommendationReason.RESOURCE_CONSTRAINED));
        }
      }
    }
    return result;
  }

  /** Helper class implementing the sort order described in the class documentation. */
  private static class UncompressedNewEntrySizeComparator
      implements Comparator<QualifiedRecommendation> {
    @Override
    public int compare(QualifiedRecommendation qr1, QualifiedRecommendation qr2) {
      return Long.compare(
          qr1.getNewEntry().getUncompressedSize(), qr2.getNewEntry().getUncompressedSize());
    }
  }
}
