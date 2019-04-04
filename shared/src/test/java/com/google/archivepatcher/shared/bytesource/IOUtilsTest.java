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

import static com.google.archivepatcher.shared.bytesource.IOUtils.readFully;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class IOUtilsTest {

  @Test
  public void testReadFully() throws IOException {
    final byte[] input = "this is a sample string to read".getBytes("UTF-8");
    final ByteArrayInputStream inputStream = new ByteArrayInputStream(input);
    final byte[] dst = new byte[50];

    try {
      readFully(inputStream, dst, 0, 50);
      assertWithMessage("Should've thrown an IOException").fail();
    } catch (IOException expected) {
      // Pass
    }

    inputStream.reset();
    readFully(inputStream, dst, 0, input.length);
    assertThat(ByteBuffer.wrap(dst, 0, input.length)).isEqualTo(ByteBuffer.wrap(input));

    inputStream.reset();
    readFully(inputStream, dst, 40, 10);
    assertThat(ByteBuffer.wrap(dst, 40, 10)).isEqualTo(ByteBuffer.wrap(input, 0, 10));

    inputStream.reset();
    try {
      readFully(inputStream, dst, 45, 11);
      assertWithMessage("Should've thrown an IndexOutOfBoundsException").fail();
    } catch (IndexOutOfBoundsException expected) {
      // Pass
    }
  }
}
