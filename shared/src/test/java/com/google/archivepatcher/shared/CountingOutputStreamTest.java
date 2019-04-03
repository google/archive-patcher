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

package com.google.archivepatcher.shared;

import static com.google.common.truth.Truth.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link CountingOutputStream}.
 */
@RunWith(JUnit4.class)
@SuppressWarnings("javadoc")
public class CountingOutputStreamTest {
  private ByteArrayOutputStream outBuffer;
  private CountingOutputStream stream;

  /**
   * Helper class that discards all output.
   */
  private static class NullStream extends OutputStream {
    @Override
    public void write(int b) throws IOException {
      // Do nothing
    }

    @Override
    public void write(byte[] b) throws IOException {
      // Do nothing
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
      // Do nothing
    }
  }

  @Before
  public void setup() {
    outBuffer = new ByteArrayOutputStream();
    stream = new CountingOutputStream(outBuffer);
  }

  @Test
  public void testGetNumBytesWritten_Zero() {
    assertThat(stream.getNumBytesWritten()).isEqualTo(0);
  }

  @Test
  public void testGetNumBytesWritten_FewBytes() throws IOException {
    stream.write(1);
    assertThat(stream.getNumBytesWritten()).isEqualTo(1);
    stream.write(new byte[] {2, 3, 4});
    assertThat(stream.getNumBytesWritten()).isEqualTo(4);
    stream.write(new byte[] {4, 5, 6, 7, 8}, 1, 3); // Write only {5, 6, 7}
    assertThat(stream.getNumBytesWritten()).isEqualTo(7);
    byte[] expected = new byte[] {1, 2, 3, 4, 5, 6, 7};
    assertThat(outBuffer.toByteArray()).isEqualTo(expected);
  }

  @Test
  public void testGetNumBytesWritten_PastIntegerMaxValue() throws IOException {
    // Make a 1MB buffer. Iterating over this 2048 times will take the test to the 2GB limit of
    // Integer.maxValue. Use a NullStream to avoid excessive memory usage and make the test fast.
    stream = new CountingOutputStream(new NullStream());
    byte[] buffer = new byte[1024 * 1024];
    for (int x = 0; x < 2048; x++) {
      stream.write(buffer);
    }
    long expected = 2048L * 1024L * 1024L; // == 2GB, Integer.MAX_VALUE + 1
    assertThat(expected).isGreaterThan((long) Integer.MAX_VALUE);
    assertThat(stream.getNumBytesWritten()).isEqualTo(expected);
    // Push it well past 4GB
    for (int x = 0; x < 78053; x++) {
      stream.write(buffer);
      expected += buffer.length;
      assertThat(stream.getNumBytesWritten()).isEqualTo(expected);
    }
  }
}
