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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;

@RunWith(JUnit4.class)
public class QuickSuffixSorterTest {

  private QuickSuffixSorter quickSuffixSorter;

  @Before
  public void setup() {
    quickSuffixSorter =
        new QuickSuffixSorter(new RandomAccessObjectFactory.RandomAccessByteArrayObjectFactory());
  }

  @Test
  public void splitBaseCaseShortGroupArrayTest() throws IOException {
    final RandomAccessObject groupArrayRO =
        intArrayToRandomAccessObject(BsDiffTestData.SHORT_GROUP_ARRAY);
    final RandomAccessObject inverseArrayRO =
        intArrayToRandomAccessObject(BsDiffTestData.SPLIT_BASE_CASE_INVERSE_TEST_ARRAY);

    QuickSuffixSorter.splitBaseCase(groupArrayRO, inverseArrayRO, 0, 32, 0);

    Assert.assertTrue(
        intArrayEqualsRandomAccessObject(
            BsDiffTestData.SPLIT_BASE_CASE_TEST_GA_CONTROL, groupArrayRO));
    Assert.assertTrue(
        intArrayEqualsRandomAccessObject(
            BsDiffTestData.SPLIT_BASE_CASE_TEST_IA_CONTROL, inverseArrayRO));
  }

  @Test
  public void splitBaseCaseTinyGroupArrayTest() throws IOException {
    final RandomAccessObject groupArray2RO =
        intArrayToRandomAccessObject(BsDiffTestData.SPLIT_BASE_CASE_TEST_GROUP_ARRAY_2);
    final RandomAccessObject inverseArray2RO =
        intArrayToRandomAccessObject(BsDiffTestData.SPLIT_BASE_CASE_TEST_INVERSE_ARRAY_2);

    QuickSuffixSorter.splitBaseCase(groupArray2RO, inverseArray2RO, 3, 5, 1);

    Assert.assertTrue(
        intArrayEqualsRandomAccessObject(
            BsDiffTestData.SPLIT_BASE_CASE_TEST_GA_CONTROL_2, groupArray2RO));
    Assert.assertTrue(
        intArrayEqualsRandomAccessObject(
            BsDiffTestData.SPLIT_BASE_CASE_TEST_IA_CONTROL_2, inverseArray2RO));
  }

  @Test
  public void splitLongGroupArrayTest() throws IOException {
    final RandomAccessObject groupArrayRO =
        intArrayToRandomAccessObject(BsDiffTestData.LONG_GROUP_ARRAY_100);
    final RandomAccessObject inverseArrayRO =
        intArrayToRandomAccessObject(BsDiffTestData.LONG_INVERSE_ARRAY_100);

    QuickSuffixSorter.split(groupArrayRO, inverseArrayRO, 5, 95, 8);

    Assert.assertTrue(
        intArrayEqualsRandomAccessObject(BsDiffTestData.SPLIT_TEST_GA_CONTROL, groupArrayRO));
    Assert.assertTrue(
        intArrayEqualsRandomAccessObject(BsDiffTestData.SPLIT_TEST_IA_CONTROL, inverseArrayRO));
  }

  @Test
  public void splitVeryLongGroupArrayTest() throws IOException {
    final RandomAccessObject groupArray2RO =
        intArrayToRandomAccessObject(BsDiffTestData.LONGER_GROUP_ARRAY_350);
    final RandomAccessObject inverseArray2RO =
        intArrayToRandomAccessObject(BsDiffTestData.LONGER_INVERSE_ARRAY_350);

    QuickSuffixSorter.split(groupArray2RO, inverseArray2RO, 17, 350 - 17, 9);

    Assert.assertTrue(
        intArrayEqualsRandomAccessObject(BsDiffTestData.SPLIT_TEST_GA_CONTROL_2, groupArray2RO));
    Assert.assertTrue(
        intArrayEqualsRandomAccessObject(BsDiffTestData.SPLIT_TEST_IA_CONTROL_2, inverseArray2RO));
  }

  @Test
  public void quickSuffixSortInitLongInverseArrayTest() throws IOException {
    final RandomAccessObject inverseArrayRO =
        intArrayToRandomAccessObject(BsDiffTestData.LONG_INVERSE_ARRAY_100);

    RandomAccessObject groupArrayRO =
        quickSuffixSorter.quickSuffixSortInit(
            BsDiffTestData.LONG_DATA_99_RO,
            inverseArrayRO);

    Assert.assertTrue(
        intArrayEqualsRandomAccessObject(
            BsDiffTestData.QUICK_SUFFIX_SORT_INIT_TEST_GA_CONTROL, groupArrayRO));
    Assert.assertTrue(
        intArrayEqualsRandomAccessObject(
            BsDiffTestData.QUICK_SUFFIX_SORT_INIT_TEST_IA_CONTROL, inverseArrayRO));
  }

  @Test
  public void quickSuffixSortInitVeryLongInverseArrayTest() throws IOException {
    final RandomAccessObject inverseArray2RO =
        intArrayToRandomAccessObject(BsDiffTestData.LONGER_INVERSE_ARRAY_350);

    RandomAccessObject groupArray2RO =
        quickSuffixSorter.quickSuffixSortInit(
            BsDiffTestData.LONGER_DATA_349_RO,
            inverseArray2RO);

    Assert.assertTrue(
        intArrayEqualsRandomAccessObject(
            BsDiffTestData.QUICK_SUFFIX_SORT_INIT_TEST_GA_CONTROL_2, groupArray2RO));
    Assert.assertTrue(
        intArrayEqualsRandomAccessObject(
            BsDiffTestData.QUICK_SUFFIX_SORT_INIT_TEST_IA_CONTROL_2, inverseArray2RO));
  }

  @Test
  public void quickSuffixSortLongDataTest() throws IOException {
    RandomAccessObject groupArrayRO = quickSuffixSorter.suffixSort(BsDiffTestData.LONG_DATA_99_RO);

    Assert.assertTrue(
        intArrayEqualsRandomAccessObject(
            BsDiffTestData.QUICK_SUFFIX_SORT_TEST_GA_CONTROL, groupArrayRO));
  }

  @Test
  public void quickSuffixSortVeryLongDataTest() throws IOException {
    RandomAccessObject groupArray2RO =
        quickSuffixSorter.suffixSort(BsDiffTestData.LONGER_DATA_349_RO);

    Assert.assertTrue(
        intArrayEqualsRandomAccessObject(
            BsDiffTestData.QUICK_SUFFIX_SORT_TEST_IA_CONTROL, groupArray2RO));
  }

  private RandomAccessObject intArrayToRandomAccessObject(final int[] array) throws IOException {
    RandomAccessObject ret =
        new RandomAccessObject.RandomAccessByteArrayObject(new byte[array.length * 4]);
    ret.seekToIntAligned(0);

    for (int element : array) {
      ret.writeInt(element);
    }

    return ret;
  }

  private boolean intArrayEqualsRandomAccessObject(
      int[] array, RandomAccessObject randomAccessObject) throws IOException {
    randomAccessObject.seekToIntAligned(0);

    for (int element : array) {
      if (element != randomAccessObject.readInt()) {
        return false;
      }
    }

    return true;
  }
}
