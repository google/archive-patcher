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

package com.google.archivepatcher.shared.bytesource;

import static com.google.archivepatcher.shared.TestUtils.assertThrows;
import static com.google.archivepatcher.shared.bytesource.ByteStreams.copy;
import static com.google.archivepatcher.shared.bytesource.ByteStreams.readFully;
import static com.google.common.truth.Truth.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ByteStreamsTest {

  byte[] input;
  byte[] dst;
  InputStream inputStream;

  @Before
  public void setUp() throws Exception {
    input = "this is a sample string to read".getBytes("UTF-8");
    inputStream = new ByteArrayInputStream(input);
    dst = new byte[50];
  }

  @Test
  public void readFully_notEnoughBytes() throws Exception {
    assertThrows(IOException.class, () -> readFully(inputStream, dst, 0, 50));
  }

  @Test
  public void readFully_enoughBytes() throws Exception {
    readFully(inputStream, dst, 0, input.length);
    assertThat(ByteBuffer.wrap(dst, 0, input.length)).isEqualTo(ByteBuffer.wrap(input));
  }

  @Test
  public void readFully_nonZeroOffset() throws Exception {
    readFully(inputStream, dst, 40, 10);
    assertThat(ByteBuffer.wrap(dst, 40, 10)).isEqualTo(ByteBuffer.wrap(input, 0, 10));
  }

  @Test
  public void readFully_outOfBounds() throws IOException {
    assertThrows(IndexOutOfBoundsException.class, () -> readFully(inputStream, dst, 45, 11));
  }

  @Test
  public void copy_success() throws Exception {
    byte[] testData = new byte[] {1, 2, 3, 4, 5, 6, 7, 8};
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    copy(new ByteArrayInputStream(testData), outputStream);

    assertThat(outputStream.toByteArray()).isEqualTo(testData);
  }
}
