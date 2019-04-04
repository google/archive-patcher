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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.archivepatcher.shared.bytesource.ByteSource;
import java.io.InputStream;
import java.util.Random;
import org.junit.Test;

/**
 * Base class for suffix sorter tests with common tests for a suffix sorter algorithm.
 */
public abstract class SuffixSorterTestBase {

  public abstract SuffixSorter getSuffixSorter();

  @Test
  public void suffixSortEmptyDataTest() throws Exception {
    checkSuffixSort( new int[] {0}, new byte[] {});
  }

  @Test
  public void suffixSortShortDataTest() throws Exception {
    checkSuffixSort(new int[] {1, 0}, new byte[] {23});
    checkSuffixSort(new int[] {2, 1, 0}, new byte[] {23, 20});
    checkSuffixSort(new int[] {2, 0, 1}, new byte[] {0, 127});
    checkSuffixSort(new int[] {2, 1, 0}, new byte[] {42, 42});
  }

  private void checkSuffixSort(int[] expectedSuffixArray, byte[] inputBytes) throws Exception {
    ByteSource input = ByteSource.wrap(inputBytes);
    RandomAccessObject groupArray = getSuffixSorter().suffixSort(input);

    assertSorted(groupArray, input);
    assertThat(randomAccessObjectToIntArray(groupArray)).isEqualTo(expectedSuffixArray);
  }

  @Test
  public void suffixSortLongDataTest() throws Exception {
    RandomAccessObject groupArrayRO = getSuffixSorter().suffixSort(BsDiffTestData.LONG_DATA_99_RO);

    assertSorted(groupArrayRO, BsDiffTestData.LONG_DATA_99_RO);

    assertThat(randomAccessObjectToIntArray(groupArrayRO))
        .isEqualTo(BsDiffTestData.QUICK_SUFFIX_SORT_TEST_GA_CONTROL);
  }

  @Test
  public void suffixSortVeryLongDataTest() throws Exception {
    RandomAccessObject groupArray2RO =
        getSuffixSorter().suffixSort(BsDiffTestData.LONGER_DATA_349_RO);

    assertSorted(groupArray2RO, BsDiffTestData.LONGER_DATA_349_RO);

    assertThat(randomAccessObjectToIntArray(groupArray2RO))
        .isEqualTo(BsDiffTestData.QUICK_SUFFIX_SORT_TEST_IA_CONTROL);
  }

  @Test
  public void testRandom() throws Exception {
    Random rand = new Random(1123458);
    for (int i = 1; i <= 10; i++) {
      ByteSource input = generateRandom(rand, i * 10000);
      RandomAccessObject suffixArray = getSuffixSorter().suffixSort(input);

      assertSorted(suffixArray, input);
    }
  }

  private static ByteSource generateRandom(Random rand, int length) {
    byte[] bytes = new byte[length];
    rand.nextBytes(bytes);
    return ByteSource.wrap(bytes);
  }

  protected static RandomAccessObject intArrayToRandomAccessObject(final int[] array)
      throws Exception {
    RandomAccessObject ret =
        new RandomAccessObject.RandomAccessByteArrayObject(new byte[array.length * 4]);
    ret.seekToIntAligned(0);

    for (int element : array) {
      ret.writeInt(element);
    }

    return ret;
  }

  protected static boolean intArrayEqualsRandomAccessObject(
      int[] array, RandomAccessObject randomAccessObject) throws Exception {
    randomAccessObject.seekToIntAligned(0);

    for (int element : array) {
      if (element != randomAccessObject.readInt()) {
        return false;
      }
    }

    return true;
  }

  protected static int[] randomAccessObjectToIntArray(RandomAccessObject randomAccessObject)
      throws Exception {
    int[] ret = new int[(int) (randomAccessObject.length() / 4)];
    randomAccessObject.seekToIntAligned(0);

    for (int i = 0; i < ret.length; i++) {
      ret[i] = randomAccessObject.readInt();
    }

    return ret;
  }

  private static boolean checkSuffixLessThanOrEqual(ByteSource input, int index1, int index2)
      throws Exception {
    while (true) {
      if (index1 == input.length()) {
        return true;
      }
      int unsignedByte1;
      try (InputStream in = input.sliceFrom(index1).openStream()) {
        unsignedByte1 = in.read();
      }
      int unsignedByte2;
      try (InputStream in = input.sliceFrom(index2).openStream()) {
        unsignedByte2 = in.read();
      }
      if (unsignedByte1 < unsignedByte2) {
        return true;
      }
      if (unsignedByte1 > unsignedByte2) {
        return false;
      }
      index1++;
      index2++;
    }
  }

  private static void assertSorted(RandomAccessObject suffixArray, ByteSource input)
      throws Exception {
    for (int i = 0; i < input.length(); i++) {
      suffixArray.seekToIntAligned(i);
      int index1 = suffixArray.readInt();
      suffixArray.seekToIntAligned(i+1);
      int index2 = suffixArray.readInt();
      if (!checkSuffixLessThanOrEqual(input, index1, index2)) {
        fail();
      }
    }
  }
}
