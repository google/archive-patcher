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
 * Implementation of matcher used by BsDiff. Exact matches between mNewData[a ... a + len - 1] and
 * mOldData[b ... b + len - 1] are valid if:
 *
 * <ul>
 *   <li>|len| > mMinimumMatchLength
 *   <li>The number of matches between mNewData[a ... a + len - 1] and mOldData[previous_b ...
 *       previous_b + len - 1] < |len| - mMinimumMatchLength where |previous_b| is the |b| value of
 *       the previous match if there was one and zero otherwise.
 * </ul>
 */
class BsDiffMatcher implements Matcher {
  private final ByteSource mOldData;
  private final ByteSource mNewData;

  /**
   * Contains order of the sorted suffixes of |mOldData|. The element at mGroupArray[i] contains the
   * position of mOldData[i ... mOldData.length - 1] in the sorted list of suffixes of |mOldData|.
   */
  private final RandomAccessObject mGroupArray;

  /**
   * The index in |mOldData| of the first byte of the match. Zero if no matches have been found yet.
   */
  private int mOldPos;

  /**
   * The index in |mNewData| of the first byte of the match. Zero if no matches have been found yet.
   * The next match will be searched starting at |mNewPos| + |mMatchLen|.
   */
  private int mNewPos;

  /** Minimum match length in bytes. */
  private final int mMinimumMatchLength;

  /**
   * A limit on how many total match lengths encountered, to exit the match extension loop in next()
   * and prevent O(n^2) behavior.
   */
  private final long mTotalMatchLenBudget = 1L << 26;  // ~64 million.

  /**
   * The number of bytes, |n|, which match between mNewData[mNewPos ... mNewPos + n] and
   * mOldData[mOldPos ... mOldPos + n].
   */
  private int mMatchLen;

  /**
   * Create a standard BsDiffMatcher.
   *
   * @param oldData
   * @param newData
   * @param minimumMatchLength the minimum "match" (in bytes) for BsDiff to consider between the
   *     mOldData and mNewData. This can have a significant effect on both the generated patch size
   *     and
   */
  BsDiffMatcher(
      ByteSource oldData,
      ByteSource newData,
      RandomAccessObject groupArray,
      int minimumMatchLength) {
    this.mOldData = oldData;
    this.mNewData = newData;
    this.mGroupArray = groupArray;
    this.mOldPos = 0;
    this.mMinimumMatchLength = minimumMatchLength;
  }

  @Override
  public Matcher.NextMatch next() throws IOException, InterruptedException {
    // The offset between between the indices in |mOldData| and |mNewData|
    // of the previous match.
    int previousOldOffset = mOldPos - mNewPos;

    // Look for a new match starting from the end of the previous match.
    mNewPos += mMatchLen;

    // The number of matching bytes in the forward extension of the previous match:
    // mOldData[mNewPos + previousOldOffset ... mNewPos + previousOldOffset + mMatchLen - 1]
    // and mNewData[mNewPos ... mNewPos + mMatchLen - 1].
    int numMatches = 0;

    // The size of the range for which |numMatches| has been computed.
    int matchesCacheSize = 0;

    // Sum over all match lengths encountered, to exit loop if we take too long to compute.
    long totalMatchLen = 0;

    while (mNewPos < mNewData.length()) {
      if (Thread.interrupted()) {
        throw new InterruptedException();
      }
      BsDiff.Match match =
          BsDiff.searchForMatch(
              mGroupArray, mOldData, mNewData, mNewPos, 0, (int) mOldData.length());
      mOldPos = match.start;
      mMatchLen = match.length;
      totalMatchLen += mMatchLen;

      // Update |numMatches| for the new value of |matchLen|.
      for (; matchesCacheSize < mMatchLen; ++matchesCacheSize) {
        int oldIndex = mNewPos + previousOldOffset + matchesCacheSize;
        int newIndex = mNewPos + matchesCacheSize;
        if (oldIndex < mOldData.length()) {
          try (InputStream oldDataInputStream = mOldData.sliceFrom(oldIndex).openStream();
              InputStream newDataInputStream = mNewData.sliceFrom(newIndex).openStream()) {
            if (oldDataInputStream.read() == newDataInputStream.read()) {
              ++numMatches;
            }
          }
        }
      }

      // Also return if we've been trying to extend a large match for a long time.
      if (mMatchLen > numMatches + mMinimumMatchLength || totalMatchLen >= mTotalMatchLenBudget) {
        return Matcher.NextMatch.of(true, mOldPos, mNewPos);
      }

      if (mMatchLen == 0) {
        ++mNewPos;
      } else if (mMatchLen == numMatches) {
        // This seems to be an optimization because it is unlikely to find a valid match in the
        // range mNewPos = [ mNewPos ... mNewPos + mMatchLen - 1 ] especially for large values of
        // |mMatchLen|.
        mNewPos += numMatches;
        numMatches = 0;
        matchesCacheSize = 0;
      } else {
        // Update |numMatches| for the value of |mNewPos| in the next iteration of the loop. In the
        // next iteration of the loop, the new value of |numMatches| will be at least
        // |numMatches - 1| because
        // mOldData[mNewPos + previousOldOffset + 1 ... mNewPos + previousOldOffset + mMatchLen - 1]
        // matches mNewData[mNewPos + 1 ... mNewPos + mMatchLen - 1].
        if (mNewPos + previousOldOffset < mOldData.length()) {

          try (InputStream oldDataInputStream =
                  mOldData.sliceFrom(mNewPos + previousOldOffset).openStream();
              InputStream newDataInputStream = mNewData.sliceFrom(mNewPos).openStream()) {
            if (oldDataInputStream.read() == newDataInputStream.read()) {
              --numMatches;
            }
          }
        }
        ++mNewPos;
        --matchesCacheSize;
      }
    }

    return Matcher.NextMatch.of(false, 0, 0);
  }
}
