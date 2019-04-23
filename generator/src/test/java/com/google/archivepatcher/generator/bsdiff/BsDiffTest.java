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

import static com.google.archivepatcher.shared.bytesource.ByteStreams.copy;
import static com.google.common.truth.Truth.assertThat;

import com.google.archivepatcher.generator.bsdiff.Matcher.NextMatch;
import com.google.archivepatcher.shared.bytesource.ByteSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class BsDiffTest {

  @Test
  public void lengthOfMatchTest() throws IOException {
    String s1 =
        "this is a string that starts the same and has some sameness in the middle, but "
            + "ends differently";
    String s2 =
        "this is a string that starts the samish and has some sameness in the middle, but "
            + "then ends didlyiefferently";
    byte[] s1b = s1.getBytes(Charset.forName("US-ASCII"));
    byte[] s2b = s2.getBytes(Charset.forName("US-ASCII"));
    ByteSource s1ro = ByteSource.wrap(s1b);
    ByteSource s2ro = ByteSource.wrap(s2b);

    assertThat(BsDiff.lengthOfMatch(s1ro, 0, s2ro, 0)).isEqualTo(36);
    assertThat(BsDiff.lengthOfMatch(s1ro, 5, s2ro, 0)).isEqualTo(0);
    assertThat(BsDiff.lengthOfMatch(s1ro, 5, s2ro, 5)).isEqualTo(31);
    assertThat(BsDiff.lengthOfMatch(s1ro, 37, s2ro, 39)).isEqualTo(42);
    assertThat(BsDiff.lengthOfMatch(s1ro, 38, s2ro, 39)).isEqualTo(0);
    assertThat(BsDiff.lengthOfMatch(s1ro, 47, s2ro, 49)).isEqualTo(32);
    assertThat(BsDiff.lengthOfMatch(s1ro, 90, s2ro, 83)).isEqualTo(2);
  }

  @Test
  public void searchForMatchBaseCaseShortGroupArrayTest() throws IOException {
    final String s1 = "asdf;1234;this should match;5678";
    final String s2 = "hkjl.9999.00vbn,``'=-this should match.9900-mmmnmn,,,.x??'";
    final byte[] s1b = s1.getBytes(Charset.forName("US-ASCII"));
    final byte[] s2b = s2.getBytes(Charset.forName("US-ASCII"));
    ByteSource s1ro = ByteSource.wrap(s1b);
    ByteSource s2ro = ByteSource.wrap(s2b);
    final RandomAccessObject groupArrayRO =
        intArrayToRandomAccessObject(BsDiffTestData.SHORT_GROUP_ARRAY);

    BsDiff.Match ret = BsDiff.searchForMatchBaseCase(groupArrayRO, s1ro, s2ro, 0, 0, 12);
    assertThat(ret.length).isEqualTo(0);
    assertThat(ret.start).isEqualTo(12);

    ret = BsDiff.searchForMatchBaseCase(groupArrayRO, s1ro, s2ro, 0, 9, 10);
    assertThat(ret.length).isEqualTo(0);
    assertThat(ret.start).isEqualTo(10);
  }

  @Test
  public void searchForMatchBaseCaseLongGroupArrayTest() throws IOException {
    final RandomAccessObject groupArray2RO =
        intArrayToRandomAccessObject(BsDiffTestData.LONG_GROUP_ARRAY_100);

    final int scan = 1;
    BsDiff.Match ret =
        BsDiff.searchForMatchBaseCase(
            groupArray2RO,
            BsDiffTestData.LONG_DATA_99_RO,
            BsDiffTestData.LONG_DATA_104_NEW_RO,
            scan,
            0,
            BsDiffTestData.LONG_DATA_99.length);
    assertThat(ret.length).isEqualTo(0);
    assertThat(ret.start).isEqualTo(10);

    ret =
        BsDiff.searchForMatchBaseCase(
            groupArray2RO,
            BsDiffTestData.LONG_DATA_99_RO,
            BsDiffTestData.LONG_DATA_104_NEW_RO,
            scan,
            64,
            65);
    assertThat(ret.length).isEqualTo(0);
    assertThat(ret.start).isEqualTo(52);

    ret =
        BsDiff.searchForMatchBaseCase(
            groupArray2RO,
            BsDiffTestData.LONG_DATA_99_RO,
            BsDiffTestData.LONG_DATA_104_NEW_RO,
            scan,
            1,
            2);
    assertThat(ret.length).isEqualTo(0);
    assertThat(ret.start).isEqualTo(46);
  }

  @Test
  public void searchForMatchBaseCaseVeryLongGroupArrayTest() throws IOException {
    final RandomAccessObject groupArray3RO =
        intArrayToRandomAccessObject(BsDiffTestData.LONGER_GROUP_ARRAY_350);

    final int scan = 1;
    BsDiff.Match ret =
        BsDiff.searchForMatchBaseCase(
            groupArray3RO,
            BsDiffTestData.LONGER_DATA_349_RO,
            BsDiffTestData.LONGER_DATA_354_NEW_RO,
            scan,
            0,
            BsDiffTestData.LONGER_DATA_349.length);
    assertThat(ret.length).isEqualTo(0);
    assertThat(ret.start).isEqualTo(246);

    ret =
        BsDiff.searchForMatchBaseCase(
            groupArray3RO,
            BsDiffTestData.LONGER_DATA_349_RO,
            BsDiffTestData.LONGER_DATA_354_NEW_RO,
            scan,
            219,
            220);
    assertThat(ret.length).isEqualTo(0);
    assertThat(ret.start).isEqualTo(251);
  }

  @Test
  public void searchForMatchShortGroupArrayTest() throws IOException {
    final String s1 = "asdf;1234;this should match;5678";
    final String s2 = "hkjl.9999.00vbn,``'=-this should match.9900-mmmnmn,,,.x??'";
    final byte[] s1b = s1.getBytes(Charset.forName("US-ASCII"));
    final byte[] s2b = s2.getBytes(Charset.forName("US-ASCII"));
    ByteSource s1ro = ByteSource.wrap(s1b);
    ByteSource s2ro = ByteSource.wrap(s2b);
    final RandomAccessObject groupArrayRO =
        intArrayToRandomAccessObject(BsDiffTestData.SHORT_GROUP_ARRAY);

    BsDiff.Match ret = BsDiff.searchForMatch(groupArrayRO, s1ro, s2ro, 0, 0, 12);
    assertThat(ret.length).isEqualTo(0);
    assertThat(ret.start).isEqualTo(10);
  }

  @Test
  public void searchForMatchLongGroupArrayTest() throws IOException {
    final RandomAccessObject groupArray2RO =
        intArrayToRandomAccessObject(BsDiffTestData.LONG_GROUP_ARRAY_100);

    final int scan = 1;
    BsDiff.Match ret =
        BsDiff.searchForMatch(
            groupArray2RO,
            BsDiffTestData.LONG_DATA_99_RO,
            BsDiffTestData.LONG_DATA_104_NEW_RO,
            scan,
            0,
            BsDiffTestData.LONG_DATA_99.length);
    assertThat(ret.length).isEqualTo(0);
    assertThat(ret.start).isEqualTo(52);
  }

  @Test
  public void searchForMatchVeryLongGroupArrayTest() throws IOException {
    final RandomAccessObject groupArray3RO =
        intArrayToRandomAccessObject(BsDiffTestData.LONGER_GROUP_ARRAY_350);

    final int scan = 1;
    BsDiff.Match ret =
        BsDiff.searchForMatch(
            groupArray3RO,
            BsDiffTestData.LONGER_DATA_349_RO,
            BsDiffTestData.LONGER_DATA_354_NEW_RO,
            scan,
            0,
            BsDiffTestData.LONGER_DATA_349.length);
    assertThat(ret.length).isEqualTo(0);
    assertThat(ret.start).isEqualTo(251);
  }

  @Test
  public void searchForMatch() throws Exception {
    String[] testCases = {
      "a",
      "aa",
      "az",
      "za",
      "aaaaa",
      "CACAO",
      "banana",
      "tobeornottobe",
      "the quick brown fox jumps over the lazy dog.",
      "elephantelephantelephantelephantelephant",
      "011010011001011010010110011010010",
    };
    for (String testCase : testCases) {
      int size = testCase.length();
      byte[] bytes = testCase.getBytes(StandardCharsets.US_ASCII);
      ByteSource input = ByteSource.wrap(bytes);
      RandomAccessObject suffixArray =
          new DivSuffixSorter(new RandomAccessObjectFactory.RandomAccessByteArrayObjectFactory())
              .suffixSort(input);

      // Test exact matches for every non-empty substring.
      for (int lo = 0; lo < size; ++lo) {
        for (int hi = lo + 1; hi <= size; ++hi) {
          byte[] query = Arrays.copyOfRange(bytes, lo, hi);
          int querySize = query.length;
          assertThat(hi - lo).isEqualTo(querySize);
          ByteSource queryBuf = ByteSource.wrap(query);

          BsDiff.Match match = BsDiff.searchForMatch(suffixArray, input, queryBuf, 0, 0, size);

          assertThat(match.length).isEqualTo(querySize);
          assertThat(match.start).isAtLeast(0);
          assertThat(match.start).isAtMost(size - match.length);
          byte[] suffix = Arrays.copyOfRange(bytes, match.start, match.start + match.length);
          assertThat(suffix).isEqualTo(query);
        }
      }
    }
  }

  @Test
  public void generatePatchTest() throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] oldData = readTestData("BsDiffInternalTestOld.txt");
    byte[] newData = readTestData("BsDiffInternalTestNew.txt");
    byte[] expectedPatch = readTestData("BsDiffInternalTestPatchExpected.patch");

    BsDiffPatchWriter.generatePatch(oldData, newData, out);

    byte[] actualPatch = out.toByteArray();
    assertThat(actualPatch).isEqualTo(expectedPatch);
  }

  @Test
  public void generatePatchOnRealCompiledBinaryTest() throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] oldData = readTestData("minimalBlobA.bin");
    byte[] newData = readTestData("minimalBlobB.bin");
    byte[] expectedPatch = readTestData("minimalBlobPatch.patch");

    BsDiffPatchWriter.generatePatch(oldData, newData, out);

    byte[] actualPatch = out.toByteArray();
    assertThat(actualPatch).isEqualTo(expectedPatch);
  }

  /**
   * Naive implementation of BsDiff.Matcher. Exact matches between newData[a ... a + len - 1] and
   * oldData[b ... b + len - 1] are valid if |len| >= 3.
   */
  @Test
  public void generatePatchWithMatcherTest() throws Exception {
    {
      // Test that all of the characters are diffed if two strings are identical even if there
      // is no "valid match" because the strings are too short.
      CtrlEntry[] expectedCtrlEntries = {new CtrlEntry(2, 0, 0)};
      generatePatchAndCheckCtrlEntries("aa", "aa", expectedCtrlEntries);
    }

    {
      // Test that all of the characters are diffed if two strings are identical and are long
      // enough to be considered a "valid match".
      CtrlEntry[] expectedCtrlEntries = {new CtrlEntry(0, 0, 0), new CtrlEntry(3, 0, 0)};
      generatePatchAndCheckCtrlEntries("aaa", "aaa", expectedCtrlEntries);
    }

    {
      // Test that none of the characters are diffed if the strings do not match.
      CtrlEntry[] expectedCtrlEntries = {new CtrlEntry(0, 2, 0)};
      generatePatchAndCheckCtrlEntries("aa", "bb", expectedCtrlEntries);
    }

    {
      // Test that characters are diffed if the beginning of the strings match even if the match
      // is not long enough to be considered valid.
      CtrlEntry[] expectedCtrlEntries = {new CtrlEntry(2, 6, 3), new CtrlEntry(3, 0, 0)};
      generatePatchAndCheckCtrlEntries("aazzzbbb", "aaayyyyybbb", expectedCtrlEntries);
    }

    {
      // Test that none of the characters are diffed if the beginning of the strings do not
      // match and the available match is not long enough to be considered valid.
      CtrlEntry[] expectedCtrlEntries = {new CtrlEntry(0, 3, 0)};
      generatePatchAndCheckCtrlEntries("zzzbb", "abb", expectedCtrlEntries);
    }

    {
      // Test that all of the characters are either diffed or are included in the extra
      // string when Matcher's match is extended.
      CtrlEntry[] expectedCtrlEntries = { // extended match | extra string
        new CtrlEntry(0, 1, 2), // n/a            | #
        new CtrlEntry(6, 3, 1), // 012345         | %^&
        new CtrlEntry(13, 0, 0) // abcdefghijklm  | n/a
      };
      generatePatchAndCheckCtrlEntries(
          "@@012345@ab@de@ghijklm", "#012$45%^&abcdefghijklm", expectedCtrlEntries);
    }

    {
      // Test that things work when the n + 1 match in the old string is before the nth match
      // in the old string.
      CtrlEntry[] expectedCtrlEntries = { // extended match | extra string
        new CtrlEntry(0, 1, 16), // n/a            | #
        new CtrlEntry(6, 3, -21), // 012345         | %^&
        new CtrlEntry(13, 0, 0) // abcdefghijklm  | n/a
      };
      generatePatchAndCheckCtrlEntries(
          "@ab@de@ghijklm@@012345", "#012$45%^&abcdefghijklm", expectedCtrlEntries);
    }

    {
      // Test the behavior when the n + 1's match backward extension overlaps with the n's match
      // forward extension.
      // "567" can be forward extended to "567@9n1x3s56"
      // "exus6" can be backward extended to "9n1x3s56exus6"
      CtrlEntry[] expectedCtrlEntries = {
        new CtrlEntry(0, 0, 5), new CtrlEntry(4, 0, 17), new CtrlEntry(13, 0, 0),
      };
      generatePatchAndCheckCtrlEntries(
          "012345678901234567890nexus9nexus5nexus6", "567@9n1x3s56exus6", expectedCtrlEntries);
    }

    {
      // Test that a match is not backward extended past the previous match.
      // "bbb" cannot be backward extended to "bb@bb@bbaaabbb" because "aaa" is a valid match.
      CtrlEntry[] expectedCtrlEntries = {
        new CtrlEntry(0, 8, 0), new CtrlEntry(3, 0, 3), new CtrlEntry(3, 0, 0),
      };
      generatePatchAndCheckCtrlEntries("aaazzzbbbbbbbb", "bb@bb@bbaaabbb", expectedCtrlEntries);
    }

    {
      // Test that a match is not forward extended past the next match.
      // "aaa" cannot be forward extended to "aaabbbaa@aa@aa" because "bbb" is a valid match.
      CtrlEntry[] expectedCtrlEntries = {
        new CtrlEntry(0, 0, 0), new CtrlEntry(3, 0, 11), new CtrlEntry(3, 8, 0),
      };
      generatePatchAndCheckCtrlEntries("aaaaaaaaaaazzzbbb", "aaabbbaa@aa@aa", expectedCtrlEntries);
    }

    {
      // Test that a match can be extended to take up the entire string.
      CtrlEntry[] expectedCtrlEntries = {
        new CtrlEntry(0, 0, 0), new CtrlEntry(9, 0, 0),
      };
      generatePatchAndCheckCtrlEntries("abcdefghi", "ab@def@hi", expectedCtrlEntries);
    }
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

  // Some systems force all text files to end in a newline, which screws up this test.
  private static byte[] stripNewlineIfNecessary(byte[] b) {
    if (b[b.length - 1] != (byte) '\n') {
      return b;
    }

    byte[] ret = new byte[b.length - 1];
    System.arraycopy(b, 0, ret, 0, ret.length);
    return ret;
  }

  private byte[] readTestData(String fileName) throws IOException {
    InputStream in = getClass().getResourceAsStream("testdata/" + fileName);
    ByteArrayOutputStream result = new ByteArrayOutputStream();
    copy(in, result);
    return stripNewlineIfNecessary(result.toByteArray());
  }

  private static class NaiveMatcher implements Matcher {
    private final byte[] mOldData;
    private final byte[] mNewData;
    private int mOldPos;
    private int mNewPos;
    private int mMatchLen;

    NaiveMatcher(byte[] oldData, byte[] newData) {
      mOldData = oldData;
      mNewData = newData;
      mOldPos = 0;
      mMatchLen = 0;
    }

    @Override
    public NextMatch next() {
      mNewPos += mMatchLen;
      for (; mNewPos < mNewData.length; ++mNewPos) {
        BsDiff.Match longestMatch = findLongestMatchInOld(mNewPos);
        mOldPos = longestMatch.start;
        mMatchLen = longestMatch.length;
        if (mMatchLen >= 3) {
          return NextMatch.of(true, mOldPos, mNewPos);
        }
      }

      return NextMatch.of(false, 0, 0);
    }

    /**
     * Finds the longest match between mNewData[newStartIndex ... mNewData.length - 1] and
     * |mOldData|.
     */
    private BsDiff.Match findLongestMatchInOld(int newStartIndex) {
      int bestMatchIndex = 0;
      int bestMatchLength = 0;
      for (int i = 0; i < mOldData.length; ++i) {
        int matchLength = 0;
        for (int newIndex = newStartIndex, oldIndex = i;
            newIndex < mNewData.length && oldIndex < mOldData.length;
            ++newIndex, ++oldIndex) {
          if (mOldData[oldIndex] != mNewData[newIndex]) {
            break;
          }
          ++matchLength;
        }

        if (matchLength > bestMatchLength) {
          bestMatchIndex = i;
          bestMatchLength = matchLength;
        }
      }

      return BsDiff.Match.of(bestMatchIndex, bestMatchLength);
    }
  }

  private static class CtrlEntry {
    public long diffLength;
    public long extraLength;
    public long oldOffset;

    public CtrlEntry(long diffLength, long extraLength, long oldOffset) {
      this.diffLength = diffLength;
      this.extraLength = extraLength;
      this.oldOffset = oldOffset;
    }

    @Override
    public String toString() {
      return String.format(
          "CtrlEntry(diffLength = %d, extraLength = %d, oldOffset = %d",
          diffLength, extraLength, oldOffset);
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof CtrlEntry)) {
        return false;
      }
      CtrlEntry o = (CtrlEntry) obj;
      return o.diffLength == diffLength && o.extraLength == extraLength && o.oldOffset == oldOffset;
    }

    @Override
    public int hashCode() {
      return Objects.hash(diffLength, extraLength, oldOffset);
    }
  }

  /**
   * Generates a patch from the differences between |oldData| and |newData| and checks that the
   * patch's control data matches |expected|. For the sake of simplicity, assumes that chars are
   * always 1 byte.
   *
   * @param expected The expected control entries in the generated patch
   */
  private void generatePatchAndCheckCtrlEntries(
      String oldData, String newData, CtrlEntry[] expected) throws Exception {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    byte[] oldBytes = oldData.getBytes(Charset.forName("US-ASCII"));
    byte[] newBytes = newData.getBytes(Charset.forName("US-ASCII"));
    ByteSource oldBytesRo = ByteSource.wrap(oldBytes);
    ByteSource newBytesRo = ByteSource.wrap(newBytes);
    BsDiffPatchWriter.generatePatchWithMatcher(
        oldBytesRo, newBytesRo, new NaiveMatcher(oldBytes, newBytes), outputStream);

    ByteArrayInputStream patchInputStream = new ByteArrayInputStream(outputStream.toByteArray());

    List<CtrlEntry> actualEntries = new ArrayList<>();
    while (patchInputStream.available() >= 24) {
      long diffLength = BsUtil.readFormattedLong(patchInputStream);
      long extraLength = BsUtil.readFormattedLong(patchInputStream);
      long oldOffset = BsUtil.readFormattedLong(patchInputStream);
      CtrlEntry entry = new CtrlEntry(diffLength, extraLength, oldOffset);
      actualEntries.add(entry);
      patchInputStream.skip(diffLength + extraLength);
    }

    assertThat(actualEntries).containsExactlyElementsIn(expected).inOrder();

    assertThat(patchInputStream.available()).isEqualTo(0);
  }
}
