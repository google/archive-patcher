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

import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.Random;
import org.junit.Assert;
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
    RandomAccessObject input = new RandomAccessObject.RandomAccessByteArrayObject(inputBytes);
    RandomAccessObject groupArray = getSuffixSorter().suffixSort(input);

    assertSorted(groupArray, input);
    Assert.assertArrayEquals(expectedSuffixArray, randomAccessObjectToIntArray(groupArray));
  }

  @Test
  public void suffixSortLongDataTest() throws Exception {
    RandomAccessObject groupArrayRO = getSuffixSorter().suffixSort(BsDiffTestData.LONG_DATA_99_RO);

    assertSorted(groupArrayRO, BsDiffTestData.LONG_DATA_99_RO);

    Assert.assertArrayEquals(
        BsDiffTestData.QUICK_SUFFIX_SORT_TEST_GA_CONTROL,
        randomAccessObjectToIntArray(groupArrayRO));
  }

  @Test
  public void suffixSortVeryLongDataTest() throws Exception {
    RandomAccessObject groupArray2RO =
        getSuffixSorter().suffixSort(BsDiffTestData.LONGER_DATA_349_RO);

    assertSorted(groupArray2RO, BsDiffTestData.LONGER_DATA_349_RO);

    Assert.assertArrayEquals(
        BsDiffTestData.QUICK_SUFFIX_SORT_TEST_IA_CONTROL,
        randomAccessObjectToIntArray(groupArray2RO));
  }

  @Test
  public void testRandom() throws Exception {
    Random rand = new Random(1123458);
    for (int i = 1; i <= 10; i++) {
      RandomAccessObject input = generateRandom(rand, i * 10000);
      RandomAccessObject suffixArray = getSuffixSorter().suffixSort(input);

      assertSorted(suffixArray, input);
    }
  }

  private static RandomAccessObject generateRandom(Random rand, int length) {
    byte[] bytes = new byte[length];
    rand.nextBytes(bytes);
    return new RandomAccessObject.RandomAccessByteArrayObject(bytes);
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

  private static boolean checkSuffixLessThanOrEqual(
      RandomAccessObject input, int index1, int index2) throws Exception {
    while (true) {
      if (index1 == input.length()) {
        return true;
      }
      input.seek(index1);
      int unsignedByte1 = input.readUnsignedByte();
      input.seek(index2);
      int unsignedByte2 = input.readUnsignedByte();
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

  private static void assertSorted(RandomAccessObject suffixArray, RandomAccessObject input)
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
