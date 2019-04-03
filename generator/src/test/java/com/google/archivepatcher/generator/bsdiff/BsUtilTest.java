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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class BsUtilTest {
  @Test
  public void writeFormattedLongTest() throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream(16);
    BsUtil.writeFormattedLong(0x12345678, outputStream);
    BsUtil.writeFormattedLong(0x0eadbeef, outputStream);
    byte[] actual = outputStream.toByteArray();

    byte[] expected = {
      (byte) 0x78,
      (byte) 0x56,
      (byte) 0x34,
      (byte) 0x12,
      (byte) 0,
      (byte) 0,
      (byte) 0,
      (byte) 0,
      (byte) 0xef,
      (byte) 0xbe,
      (byte) 0xad,
      (byte) 0x0e,
      (byte) 0,
      (byte) 0,
      (byte) 0,
      (byte) 0
    };
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void readFormattedLongTest() throws IOException {
    byte[] data = {
      (byte) 0x78,
      (byte) 0x56,
      (byte) 0x34,
      (byte) 0x12,
      (byte) 0,
      (byte) 0,
      (byte) 0,
      (byte) 0,
      (byte) 0xef,
      (byte) 0xbe,
      (byte) 0xad,
      (byte) 0x0e,
      (byte) 0,
      (byte) 0,
      (byte) 0,
      (byte) 0
    };
    ByteArrayInputStream inputStream = new ByteArrayInputStream(data);

    assertThat(BsUtil.readFormattedLong(inputStream)).isEqualTo(0x12345678);
    assertThat(BsUtil.readFormattedLong(inputStream)).isEqualTo(0x0eadbeef);
  }

  private long writeThenReadFormattedLong(long value) throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream(8);
    BsUtil.writeFormattedLong(value, outputStream);
    byte[] outputBytes = outputStream.toByteArray();
    ByteArrayInputStream inputStream = new ByteArrayInputStream(outputBytes);
    return BsUtil.readFormattedLong(inputStream);
  }

  @Test
  public void writeThenReadFormattedLongTest() throws IOException {
    assertThat(writeThenReadFormattedLong(-1)).isEqualTo(-1);
    assertThat(writeThenReadFormattedLong(0x7fffffff)).isEqualTo(0x7fffffff);
    assertThat(writeThenReadFormattedLong(0)).isEqualTo(0);
    assertThat(writeThenReadFormattedLong(Long.MAX_VALUE)).isEqualTo(Long.MAX_VALUE);
    assertThat(writeThenReadFormattedLong(Long.MIN_VALUE)).isEqualTo(Long.MIN_VALUE);
  }

  @Test
  public void lexicographicalCompareTest() throws IOException {
    String s1 = "this is a string";
    String s2 = "that was a string";
    byte[] s1b = s1.getBytes(Charset.forName("US-ASCII"));
    byte[] s2b = s2.getBytes(Charset.forName("US-ASCII"));
    RandomAccessObject s1ro = new RandomAccessObject.RandomAccessByteArrayObject(s1b);
    RandomAccessObject s2ro = new RandomAccessObject.RandomAccessByteArrayObject(s2b);

    int r = BsUtil.lexicographicalCompare(s1ro, 0, s1b.length, s2ro, 0, s2b.length);
    assertThat(r).isGreaterThan(0);

    r = BsUtil.lexicographicalCompare(s1ro, 5, s1b.length - 5, s2ro, 5, s2b.length - 5);
    assertThat(r).isLessThan(0);

    r = BsUtil.lexicographicalCompare(s1ro, 7, s1b.length - 7, s2ro, 8, s2b.length - 7);
    assertThat(r).isLessThan(0);

    r = BsUtil.lexicographicalCompare(s1ro, 7, s1b.length - 8, s2ro, 8, s2b.length - 8);
    assertThat(r).isLessThan(0);

    r = BsUtil.lexicographicalCompare(s1ro, 0, 2, s2ro, 0, 2);
    assertThat(r).isEqualTo(0);

    r = BsUtil.lexicographicalCompare(s1ro, 0, 1, s2ro, 0, 2);
    assertThat(r).isLessThan(0);

    r = BsUtil.lexicographicalCompare(s1ro, 0, 2, s2ro, 0, 1);
    assertThat(r).isGreaterThan(0);
  }
}
