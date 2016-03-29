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

package com.google.archivepatcher.generator.bsdiff;

import java.io.IOException;
import java.util.Deque;
import java.util.LinkedList;

/**
 * A Java implementation of the "bsdiff" algorithm based on the BSD-2 licensed source code available
 * here: https://github.com/mendsley/bsdiff.
 * <p>
 * A canonical description of the bsdiff algorithm can be found at the following URL:
 * http://www.daemonology.net/bsdiff/
 * <p>
 * Since Java only supports "int" for array indexing, the maximum size of files that this
 * implementation can handle is 2^31, or 2 gibibytes.
 */
class BsDiff {
  /**
   * Base case for the recursive split(), below.
   */
  // Visible for testing only
  static void splitBaseCase(
      final RandomAccessObject groupArray,
      final RandomAccessObject inverseArray,
      final int start,
      final int length,
      final int inverseOffset)
      throws IOException {
    int step = 0;

    for (int outer = start; outer < start + length; outer += step) {
      step = 1;
      groupArray.seekToIntAligned(outer);
      inverseArray.seekToIntAligned(groupArray.readInt() + inverseOffset);
      int x = inverseArray.readInt();

      for (int inner = 1; outer + inner < start + length; inner++) {
        groupArray.seekToIntAligned(outer + inner);
        inverseArray.seekToIntAligned(groupArray.readInt() + inverseOffset);
        final int tempX = inverseArray.readInt();
        if (tempX < x) {
          x = tempX;
          step = 0;
        }

        if (tempX == x) {
          groupArray.seekToIntAligned(outer + step);
          final int temp = groupArray.readInt();
          groupArray.seekToIntAligned(outer + inner);
          final int outerInner = groupArray.readInt();
          groupArray.seekToIntAligned(outer + step);
          groupArray.writeInt(outerInner);
          groupArray.seekToIntAligned(outer + inner);
          groupArray.writeInt(temp);
          step++;
        }
      }

      groupArray.seekToIntAligned(outer);
      for (int innerIndex = 0; innerIndex < step; innerIndex++) {
        inverseArray.seekToIntAligned(groupArray.readInt());
        inverseArray.writeInt(outer + step - 1);
      }

      if (step == 1) {
        groupArray.seekToIntAligned(outer);
        groupArray.writeInt(-1);
      }
    }
  }

  /**
   * Part of the quick suffix sort algorithm.
   */
  // Visible for testing only
  static void split(
      final RandomAccessObject groupArray,
      final RandomAccessObject inverseArray,
      final int start,
      final int length,
      final int inverseOffset)
      throws IOException {
    Deque<SplitTask> taskStack = new LinkedList<>();
    taskStack.add(new SplitTaskStage1(start, length, inverseOffset));
    while (!taskStack.isEmpty()) {
      taskStack.removeFirst().run(groupArray, inverseArray, taskStack);
    }
  }

  /**
   * An interface for split tasks. Split tasks are executed on a stack. A SplitTask can produce
   * other SplitTasks that will be pushed onto the stack and immediately executed.
   */
  private static interface SplitTask {
    /**
     * Execute the task, optionally adding more tasks to be executed.
     */
    public void run(
        final RandomAccessObject groupArray,
        final RandomAccessObject inverseArray,
        Deque<SplitTask> taskStack)
        throws IOException;
  }

  private static class SplitTaskStage1 implements SplitTask {
    private final int start;
    private final int length;
    private final int inverseOffset;

    public SplitTaskStage1(final int start, final int length, final int inverseOffset) {
      this.start = start;
      this.length = length;
      this.inverseOffset = inverseOffset;
    }

    @Override
    public void run(
        final RandomAccessObject groupArray,
        final RandomAccessObject inverseArray,
        final Deque<SplitTask> taskStack)
        throws IOException {
      if (length < 16) {
        // Length is too short to bother recursing.
        splitBaseCase(groupArray, inverseArray, start, length, inverseOffset);
        return;
      }

      // Else, length >= 16
      groupArray.seekToIntAligned(start + length / 2);
      inverseArray.seekToIntAligned(groupArray.readInt() + inverseOffset);
      final int x = inverseArray.readInt();
      int jj = 0;
      int kk = 0;

      groupArray.seekToIntAligned(start);
      for (int index = start; index < start + length; index++) {
        inverseArray.seekToIntAligned(groupArray.readInt() + inverseOffset);
        final int i = inverseArray.readInt();

        if (i < x) {
          jj++;
        } else if (i == x) {
          kk++;
        }
      }

      jj += start;
      kk += jj;

      { // scoping block
        int j = 0;
        int k = 0;
        {
          int i = start;
          while (i < jj) {
            groupArray.seekToIntAligned(i);
            final int groupArrayInt = groupArray.readInt();
            inverseArray.seekToIntAligned(groupArrayInt + inverseOffset);
            final int inverseInt = inverseArray.readInt();

            if (inverseInt < x) {
              i++;
            } else if (inverseInt == x) {
              groupArray.seekToIntAligned(jj + j);
              final int temp = groupArray.readInt();
              groupArray.seekToIntAligned(i);
              groupArray.writeInt(temp);
              groupArray.seekToIntAligned(jj + j);
              groupArray.writeInt(groupArrayInt);
              j++;
            } else { // >x
              groupArray.seekToIntAligned(kk + k);
              final int temp = groupArray.readInt();
              groupArray.seekToIntAligned(i);
              groupArray.writeInt(temp);
              groupArray.seekToIntAligned(kk + k);
              groupArray.writeInt(groupArrayInt);
              k++;
            }
          }
        }

        while (jj + j < kk) {
          groupArray.seekToIntAligned(jj + j);
          final int temp = groupArray.readInt();
          inverseArray.seekToIntAligned(temp + inverseOffset);
          if (inverseArray.readInt() == x) {
            j++;
          } else { // != x
            groupArray.seekToIntAligned(kk + k);
            final int tempkk = groupArray.readInt();
            groupArray.seekToIntAligned(jj + j);
            groupArray.writeInt(tempkk);
            groupArray.seekToIntAligned(kk + k);
            groupArray.writeInt(temp);
            k++;
          }
        }
      }

      // Enqueue tasks to finish all remaining work.
      if (start + length > kk) {
        taskStack.addFirst(new SplitTaskStage1(kk, start + length - kk, inverseOffset));
      }
      taskStack.addFirst(new SplitTaskStage2(jj, kk));
      if (jj > start) {
        taskStack.addFirst(new SplitTaskStage1(start, jj - start, inverseOffset));
      }
    }
  }

  private static class SplitTaskStage2 implements SplitTask {
    private final int jj;
    private final int kk;

    public SplitTaskStage2(final int jj, final int kk) {
      this.jj = jj;
      this.kk = kk;
    }

    @Override
    public void run(
        final RandomAccessObject groupArray,
        final RandomAccessObject inverseArray,
        final Deque<SplitTask> taskStack)
        throws IOException {
      groupArray.seekToIntAligned(jj);
      for (int i = 0; i < kk - jj; i++) {
        inverseArray.seekToIntAligned(groupArray.readInt());
        inverseArray.writeInt(kk - 1);
      }

      if (jj == kk - 1) {
        groupArray.seekToIntAligned(jj);
        groupArray.writeInt(-1);
      }
    }
  }

  /**
   * Initialize a quick suffix sort. Note: the returned {@link RandomAccessObject} should be closed
   * by the caller.
   */
  // Visible for testing only
  static RandomAccessObject quickSuffixSortInit(
      final RandomAccessObject data,
      final RandomAccessObject inverseArray,
      final RandomAccessObjectFactory randomAccessObjectFactory)
      throws IOException {
    // Generate a histogram of the counts of each byte in the old data:
    // 1. Initialize buckets 0-255 to zero
    // 2. Read each byte and count the number of occurrences of each byte
    // 3. For each bucket, add the previous bucket's value.
    // 4. For each bucket past the first, set the value to the previous
    //    bucket's value
    final int[] buckets = new int[256];

    data.seek(0);
    for (int i = 0; i < data.length(); i++) {
      buckets[data.readUnsignedByte()]++;
    }

    for (int i = 1; i < 256; i++) {
      buckets[i] += buckets[i - 1];
    }

    for (int i = 255; i > 0; i--) {
      buckets[i] = buckets[i - 1];
    }

    buckets[0] = 0;

    if (4 * (data.length() + 1) >= Integer.MAX_VALUE) {
      throw new IllegalArgumentException("Input too large");
    }
    final RandomAccessObject groupArray =
        randomAccessObjectFactory.create(((int) data.length() + 1) * 4);

    try {
      data.seek(0);
      for (int i = 0; i < data.length(); i++) {
        groupArray.seekToIntAligned(++buckets[data.readUnsignedByte()]);
        groupArray.writeInt(i);
      }

      data.seek(0);
      groupArray.seekToIntAligned(0);
      groupArray.writeInt((int) data.length());
      inverseArray.seekToIntAligned(0);
      for (int i = 0; i < data.length(); i++) {
        inverseArray.writeInt(buckets[data.readUnsignedByte()]);
      }

      inverseArray.seekToIntAligned((int) data.length());
      inverseArray.writeInt(0);
      for (int i = 1; i < 256; i++) {
        if (buckets[i] == buckets[i - 1] + 1) {
          groupArray.seekToIntAligned(buckets[i]);
          groupArray.writeInt(-1);
        }
      }

      groupArray.seekToIntAligned(0);
      groupArray.writeInt(-1);
    } catch (IOException e) {
      groupArray.close();
      throw new IOException("Unable to init suffix sorting on groupArray", e);
    }

    return groupArray;
  }

  /**
   * Perform a "quick suffix sort". Note: the returned {@link RandomAccessObject} should be closed
   * by the caller.
   * @param data the data to sort
   * @param randomAccessObjectFactory factory to create {@link RandomAccessObject} instances for
   * groupArray and inverseArray.
   */
  // Visible for testing only
  static RandomAccessObject quickSuffixSort(
      final RandomAccessObject data, final RandomAccessObjectFactory randomAccessObjectFactory)
      throws IOException {
    if (4 * (data.length() + 1) >= Integer.MAX_VALUE) {
      throw new IllegalArgumentException("Input too large");
    }
    RandomAccessObject groupArray = null;

    try (final RandomAccessObject inverseArray =
        randomAccessObjectFactory.create(((int) data.length() + 1) * 4)) {
      groupArray = quickSuffixSortInit(data, inverseArray, randomAccessObjectFactory);

      int h = 1;
      while (true) {
        groupArray.seekToIntAligned(0);
        if (groupArray.readInt() == -(data.length() + 1)) {
          break;
        }

        int length = 0;
        int i = 0;

        while (i < data.length() + 1) {
          groupArray.seekToIntAligned(i);
          final int groupArrayIndex = groupArray.readInt();
          if (groupArrayIndex < 0) {
            length -= groupArrayIndex;
            i -= groupArrayIndex;
          } else {
            if (length > 0) {
              groupArray.seekToIntAligned(i - length);
              groupArray.writeInt(-length);
            }

            inverseArray.seekToIntAligned(groupArrayIndex);
            length = inverseArray.readInt() + 1 - i;
            split(groupArray, inverseArray, i, length, h);
            i += length;
            length = 0;
          }
        }

        if (length > 0) {
          groupArray.seekToIntAligned(i - length);
          groupArray.writeInt(-length);
        }

        h *= 2;
      }

      inverseArray.seekToIntAligned(0);
      for (int i = 0; i < data.length() + 1; i++) {
        groupArray.seekToIntAligned(inverseArray.readInt());
        groupArray.writeInt(i);
      }
    } catch (Exception e) {
      if (groupArray != null) {
        groupArray.close();
      }
      throw new IOException("Unable to finish suffix sorting groupArray", e);
    }

    return groupArray;
  }

  /**
   * Search the specified arrays for a contiguous sequence of identical bytes, starting at the
   * specified "start" offsets and scanning as far ahead as possible till one or the other of the
   * arrays ends or a non-matching byte is found. Returns the length of the matching sequence of
   * bytes, which may be zero.
   *
   * @param oldData the old data to scan
   * @param oldStart the position in the old data at which to start the scan
   * @param newData the new data to scan
   * @param newStart the position in the new data at which to start the scan
   * @return the number of matching bytes in the two arrays starting at the specified indices; zero
   * if the first byte fails to match
   */
  // Visible for testing only
  static int lengthOfMatch(
      final RandomAccessObject oldData,
      final int oldStart,
      final RandomAccessObject newData,
      final int newStart)
      throws IOException {
    final int max = Math.min((int) oldData.length() - oldStart, (int) newData.length() - newStart);
    if (max > 0) {
      // If max is 0, it's sometimes possible for this seek to seek to length + 1 and throw an
      // exception unnecessarily.
      oldData.seek(oldStart);
      newData.seek(newStart);
      for (int offset = 0; offset < max; offset++) {
        if (oldData.readByte() != newData.readByte()) {
          return offset;
        }
      }
    }

    return max;
  }

  // Visible for testing only
  static Match searchForMatchBaseCase(
      final RandomAccessObject groupArray,
      final RandomAccessObject oldData,
      final RandomAccessObject newData,
      final int newStart,
      final int oldDataRangeStartA,
      final int oldDataRangeStartB)
      throws IOException {
    // Located the start of a matching range (no further search required) or the size of the range
    // has shrunk to one byte (no further search possible).
    groupArray.seekToIntAligned(oldDataRangeStartA);
    final int groupArrayOldDataRangeStartA = groupArray.readInt();
    final int lengthOfMatchA =
        lengthOfMatch(oldData, groupArrayOldDataRangeStartA, newData, newStart);
    groupArray.seekToIntAligned(oldDataRangeStartB);
    final int groupArrayOldDataRangeStartB = groupArray.readInt();
    final int lengthOfMatchB =
        lengthOfMatch(oldData, groupArrayOldDataRangeStartB, newData, newStart);

    if (lengthOfMatchA > lengthOfMatchB) {
      return Match.of(groupArrayOldDataRangeStartA, lengthOfMatchA);
    }

    return Match.of(groupArrayOldDataRangeStartB, lengthOfMatchB);
  }

  /**
   * Locates the run of bytes in |oldData| which matches the longest prefix of
   * newData[newStart ... newData.length - 1].
   * @param groupArray
   * @param oldData the old data to scan
   * @param newData the new data to scan
   * @param newStart the position of the first byte in newData to consider
   * @param oldDataRangeStartA
   * @param oldDataRangeStartB
   * @return a Match containing the length of the matching range, and the position at which the
   * matching range begins.
   */
  // Visible for testing only
  static Match searchForMatch(
      final RandomAccessObject groupArray,
      final RandomAccessObject oldData,
      final RandomAccessObject newData,
      final int newStart,
      final int oldDataRangeStartA,
      final int oldDataRangeStartB)
      throws IOException {
    if (oldDataRangeStartB - oldDataRangeStartA < 2) {
      return searchForMatchBaseCase(
          groupArray, oldData, newData, newStart, oldDataRangeStartA, oldDataRangeStartB);
    }

    // Cut range in half and search again
    final int rangeLength = oldDataRangeStartB - oldDataRangeStartA;
    final int pivot = oldDataRangeStartA + (rangeLength / 2);
    groupArray.seekToIntAligned(pivot);
    final int groupArrayPivot = groupArray.readInt();
    final int compareLength =
        Math.min((int) oldData.length() - groupArrayPivot, (int) newData.length() - newStart);
    if (BsUtil.memcmp(oldData, groupArrayPivot, newData, newStart, compareLength) < 0) {
      return searchForMatch(groupArray, oldData, newData, newStart, pivot, oldDataRangeStartB);
    }
    return searchForMatch(groupArray, oldData, newData, newStart, oldDataRangeStartA, pivot);
  }

  static class Match {
    final int start;
    final int length;

    static Match of(int start, int length) {
      return new Match(start, length);
    }

    private Match(int start, int length) {
      this.start = start;
      this.length = length;
    }
  }
}
