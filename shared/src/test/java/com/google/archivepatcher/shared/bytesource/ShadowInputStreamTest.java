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
import static com.google.common.truth.Truth.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ShadowInputStreamTest {

  private static final byte[] DATA = {1, 2, 3, 4, 5, 6, 7};

  private ShadowInputStream<ByteArrayInputStream> inputStream;

  private ByteArrayInputStream underlyingStream = new ByteArrayInputStream(DATA);

  private boolean isClosed = false;

  @Before
  public void setUp() throws Exception {
    inputStream = new ShadowInputStream<>(underlyingStream);
  }

  @Test
  public void getStream() throws Exception {
    assertThat(inputStream.getStream()).isSameInstanceAs(underlyingStream);
  }

  @Test
  public void close() throws Exception {
    inputStream.open(() -> isClosed = true);

    inputStream.close();

    assertThat(isClosed).isTrue();
    byte[] buffer = new byte[DATA.length];
    assertThrows(IOException.class, () -> inputStream.read());
    assertThrows(IOException.class, () -> inputStream.read(buffer, 0, 1));
    assertThrows(IOException.class, () -> inputStream.available());
    assertThrows(IOException.class, () -> inputStream.skip(4));
  }

  @Test
  public void open() throws Exception {
    inputStream.close();

    inputStream.open();

    // Here we do some random reading to test that the input stream is available again.
    byte[] buffer = new byte[DATA.length];
    assertThat(inputStream.read()).isEqualTo(DATA[0]);
    assertThat(inputStream.read(buffer, 0, 1)).isEqualTo(1);
  }

  @Test
  public void available() throws Exception {
    assertThat(inputStream.available()).isEqualTo(DATA.length);
    inputStream.read();
    assertThat(inputStream.available()).isEqualTo(DATA.length - 1);
  }

  @Test
  public void markSupported() throws Exception {
    assertThat(inputStream.markSupported())
        .isEqualTo(new ByteArrayInputStream(DATA).markSupported());
  }

  @Test
  public void read() throws Exception {
    for (byte b : DATA) {
      assertThat(inputStream.read()).isEqualTo(b);
    }
    assertThat(inputStream.read()).isEqualTo(-1);
  }

  @Test
  public void read_byteArray() throws Exception {
    byte[] b = new byte[DATA.length];
    inputStream.read(b);
    assertThat(b).isEqualTo(DATA);
  }

  @Test
  public void read_byteArrayWithOffset() throws Exception {
    byte[] b = new byte[DATA.length];
    int bytesToRead = DATA.length / 2;
    int offset = 1;
    inputStream.read(b, offset, bytesToRead);

    for (int i = 0; i < bytesToRead; i++) {
      assertThat(b[i + offset]).isEqualTo(DATA[i]);
    }
  }

  @Test
  public void skip() throws Exception {
    assertThat(inputStream.skip(3)).isEqualTo(3);
    assertThat(inputStream.read()).isEqualTo(DATA[3]);
  }
}
