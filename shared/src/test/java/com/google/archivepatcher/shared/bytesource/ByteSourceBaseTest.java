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
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import java.io.InputStream;
import org.junit.After;
import org.junit.Test;

public abstract class ByteSourceBaseTest {

  private final boolean expectedSupportsMultipleStreams;

  protected byte[] expectedData = null;
  protected ByteSource byteSource = null;

  public ByteSourceBaseTest(boolean supportsMultipleStreams) {
    this.expectedSupportsMultipleStreams = supportsMultipleStreams;
  }

  @After
  public void tearDown() throws Exception {
    if (byteSource != null) {
      byteSource.close();
    }
  }

  @Test
  public void length() throws Exception {
    length(byteSource, expectedData);
  }

  private void length(ByteSource byteSource, byte[] expectedData) {
    assertThat(byteSource.length()).isEqualTo(expectedData.length);
  }

  @Test
  public void supportsMultipleStreams() throws Exception {
    supportsMultipleStreams(byteSource);
  }

  private void supportsMultipleStreams(ByteSource byteSource) throws Exception {
    assertThat(byteSource.supportsMultipleStreams()).isEqualTo(expectedSupportsMultipleStreams);
  }

  @Test
  public void openStream() throws Exception {
    openStream(byteSource, expectedData);
  }

  private void openStream(ByteSource byteSource, byte[] expectedData) throws Exception {
    // Here we open stream multiple times to verify we are reading the same thing.
    for (int i = 0; i < 5; i++) {
      try (InputStream in = byteSource.openStream()) {
        testInputStreamData(in, expectedData);
      }
    }
  }

  @Test
  public void openStream_noMultipleStreams() throws Exception {
    openStream_noMultipleStreams(byteSource);
  }

  private void openStream_noMultipleStreams(ByteSource byteSource) throws Exception {
    // This test only targets byte sources that do not support multiple streams.
    assumeFalse(byteSource.supportsMultipleStreams());

    byteSource.openStream();
    assertThrows(IllegalStateException.class, () -> byteSource.openStream());
  }

  @Test
  public void openStream_multipleStreams() throws Exception {
    openStream_multipleStreams(byteSource);
  }

  private void openStream_multipleStreams(ByteSource byteSource) throws Exception {
    // This test only targets byte sources that do support multiple streams.
    assumeTrue(byteSource.supportsMultipleStreams());

    InputStream in1 = byteSource.openStream();
    InputStream in2 = byteSource.openStream();
    assertThat(in1.available()).isEqualTo(in2.available());
    while (in1.available() > 0 && in2.available() > 0) {
      assertThat(in1.read()).isEqualTo(in2.read());
    }
  }

  @Test
  public void slice() throws Exception {
    slice(byteSource, expectedData);
  }

  private void slice(ByteSource byteSource, byte[] expectedData) throws Exception {
    int offset = expectedData.length / 2;
    int length = Math.min(expectedData.length / 3, expectedData.length - offset);
    byte[] newExpectedData = new byte[length];
    for (int x = 0; x < length; x++) {
      newExpectedData[x] = expectedData[x + offset];
    }
    ByteSource slice = byteSource.slice(offset, length);

    // Here we just rerun the other tests to check they run successfully on the new byte source.
    length(slice, newExpectedData);
    supportsMultipleStreams(slice);
    openStream(slice, newExpectedData);
    openStream_multipleStreams(slice);
    openStream_noMultipleStreams(slice);
  }

  private void testInputStreamData(InputStream in, byte[] expectedData) throws Exception {
    assertThat(in.available()).isEqualTo(expectedData.length);
    assertThat(in.read()).isEqualTo(expectedData[0]);

    int offset = 1;
    byte[] buffer = new byte[expectedData.length];
    assertThat(in.read(buffer, offset, expectedData.length - 1)).isEqualTo(expectedData.length - 1);

    for (int i = offset; i < expectedData.length; ++i) {
      assertThat(buffer[i]).isEqualTo(expectedData[i]);
    }
  }

  protected static byte[] getSampleTestData() {
    byte[] testData = new byte[128];
    for (int x = 0; x < 128; x++) {
      testData[x] = (byte) x;
    }
    return testData;
  }
}
