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

package com.google.archivepatcher.generator.bsdiff;

import com.google.archivepatcher.shared.bytesource.ByteSource;
import java.io.IOException;
import java.io.InputStream;

/**
 * Implementation of matcher used by BsDiff. Exact matches between newData[a ... a + len - 1] and
 * oldData[b ... b + len - 1] are valid if:
 *
 * <ul>
 *   <li>|len| > minimumMatchLength
 *   <li>The number of matches between newData[a ... a + len - 1] and oldData[previous_b ...
 *       previous_b + len - 1] < |len| - minimumMatchLength where |previous_b| is the |b| value of
 *       the previous match if there was one and zero otherwise.
 * </ul>
 */
class BsDiffMatcher implements Matcher {
  private final ByteSource oldData;
  private final ByteSource newData;

  /**
   * Contains order of the sorted suffixes of |oldData|. The element at groupArray[i] contains the
   * position of oldData[i ... oldData.length - 1] in the sorted list of suffixes of |oldData|.
   */
  private final RandomAccessObject groupArray;

  /**
   * The index in |oldData| of the first byte of the match. Zero if no matches have been found yet.
   */
  private int oldPos;

  /**
   * The index in |newData| of the first byte of the match. Zero if no matches have been found yet.
   * The next match will be searched starting at |newPos| + |matchLen|.
   */
  private int newPos;

  /** Minimum match length in bytes. */
  private final int minimumMatchLength;

  /**
   * A limit on how many total match lengths encountered, to exit the match extension loop in next()
   * and prevent O(n^2) behavior.
   */
  private final long totalMatchLenBudget = 1L << 26; // ~64 million.

  /**
   * The number of bytes, |n|, which match between newData[newPos ... newPos + n] and oldData[oldPos
   * ... oldPos + n].
   */
  private int matchLen;

  /**
   * Create a standard BsDiffMatcher.
   *
   * @param oldData
   * @param newData
   * @param minimumMatchLength the minimum "match" (in bytes) for BsDiff to consider between the
   *     oldData and newData. This can have a significant effect on both the generated patch size
   *     and
   */
  BsDiffMatcher(
      ByteSource oldData,
      ByteSource newData,
      RandomAccessObject groupArray,
      int minimumMatchLength) {
    this.oldData = oldData;
    this.newData = newData;
    this.groupArray = groupArray;
    this.oldPos = 0;
    this.minimumMatchLength = minimumMatchLength;
  }

  @Override
  public Matcher.NextMatch next() throws IOException, InterruptedException {
    // The offset between between the indices in |oldData| and |newData|
    // of the previous match.
    int previousOldOffset = oldPos - newPos;

    // Look for a new match starting from the end of the previous match.
    newPos += matchLen;

    // The number of matching bytes in the forward extension of the previous match:
    // oldData[newPos + previousOldOffset ... newPos + previousOldOffset + matchLen - 1]
    // and newData[newPos ... newPos + matchLen - 1].
    int numMatches = 0;

    // The size of the range for which |numMatches| has been computed.
    int matchesCacheSize = 0;

    // Sum over all match lengths encountered, to exit loop if we take too long to compute.
    long totalMatchLen = 0;

    while (newPos < newData.length()) {
      if (Thread.interrupted()) {
        throw new InterruptedException();
      }
      BsDiff.Match match =
          BsDiff.searchForMatch(groupArray, oldData, newData, newPos, 0, (int) oldData.length());
      oldPos = match.start;
      matchLen = match.length;
      totalMatchLen += matchLen;

      // Update |numMatches| for the new value of |matchLen|.
      for (; matchesCacheSize < matchLen; ++matchesCacheSize) {
        int oldIndex = newPos + previousOldOffset + matchesCacheSize;
        int newIndex = newPos + matchesCacheSize;
        if (oldIndex < oldData.length()) {
          try (InputStream oldDataInputStream = oldData.sliceFrom(oldIndex).openStream();
              InputStream newDataInputStream = newData.sliceFrom(newIndex).openStream()) {
            if (oldDataInputStream.read() == newDataInputStream.read()) {
              ++numMatches;
            }
          }
        }
      }

      // Also return if we've been trying to extend a large match for a long time.
      if (matchLen > numMatches + minimumMatchLength || totalMatchLen >= totalMatchLenBudget) {
        return Matcher.NextMatch.of(true, oldPos, newPos);
      }

      if (matchLen == 0) {
        ++newPos;
      } else if (matchLen == numMatches) {
        // This seems to be an optimization because it is unlikely to find a valid match in the
        // range newPos = [ newPos ... newPos + matchLen - 1 ] especially for large values of
        // |matchLen|.
        newPos += numMatches;
        numMatches = 0;
        matchesCacheSize = 0;
      } else {
        // Update |numMatches| for the value of |newPos| in the next iteration of the loop. In the
        // next iteration of the loop, the new value of |numMatches| will be at least
        // |numMatches - 1| because
        // oldData[newPos + previousOldOffset + 1 ... newPos + previousOldOffset + matchLen - 1]
        // matches newData[newPos + 1 ... newPos + matchLen - 1].
        if (newPos + previousOldOffset < oldData.length()) {

          try (InputStream oldDataInputStream =
                  oldData.sliceFrom(newPos + previousOldOffset).openStream();
              InputStream newDataInputStream = newData.sliceFrom(newPos).openStream()) {
            if (oldDataInputStream.read() == newDataInputStream.read()) {
              --numMatches;
            }
          }
        }
        ++newPos;
        --matchesCacheSize;
      }
    }

    return Matcher.NextMatch.of(false, 0, 0);
  }
}
