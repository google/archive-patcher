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

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link ByteArrayHolder}.
 */
@RunWith(JUnit4.class)
@SuppressWarnings("javadoc")
public class ByteArrayHolderTest {

  @Test
  public void testGetters() {
    byte[] data = "hello world".getBytes(UTF_8);
    ByteArrayHolder byteArrayHolder = new ByteArrayHolder(data);
    assertThat(data).isSameInstanceAs(byteArrayHolder.getData());
  }

  @Test
  public void testHashCode() {
    byte[] data1a = "hello world".getBytes(UTF_8);
    byte[] data1b = "hello world".getBytes(UTF_8);
    byte[] data2 = "hello another world".getBytes(UTF_8);
    ByteArrayHolder rawText1a = new ByteArrayHolder(data1a);
    ByteArrayHolder rawText1b = new ByteArrayHolder(data1b);
    assertThat(rawText1b.hashCode()).isEqualTo(rawText1a.hashCode());
    ByteArrayHolder rawText2 = new ByteArrayHolder(data2);
    assertThat(rawText2.hashCode()).isNotEqualTo(rawText1a.hashCode());
    ByteArrayHolder rawText3 = new ByteArrayHolder(null);
    assertThat(rawText3.hashCode()).isNotEqualTo(rawText1a.hashCode());
    assertThat(rawText3.hashCode()).isNotEqualTo(rawText2.hashCode());
  }

  @Test
  public void testEquals() {
    byte[] data1a = "hello world".getBytes(UTF_8);
    byte[] data1b = "hello world".getBytes(UTF_8);
    byte[] data2 = "hello another world".getBytes(UTF_8);
    ByteArrayHolder rawText1a = new ByteArrayHolder(data1a);
    ByteArrayHolder rawText1b = new ByteArrayHolder(data1b);
    assertThat(rawText1b).isEqualTo(rawText1a);
    ByteArrayHolder rawText2 = new ByteArrayHolder(data2);
    assertThat(rawText2).isNotEqualTo(rawText1a);
    ByteArrayHolder rawText3 = new ByteArrayHolder(null);
    assertThat(rawText3).isNotEqualTo(rawText1a);
    assertThat(rawText1a).isNotEqualTo(rawText3);
  }
}
