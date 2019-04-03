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

import static com.google.archivepatcher.shared.TestUtils.storeInTempFile;
import static com.google.common.truth.Truth.assertThat;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.Arrays;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Test class specific for {@link SlicedByteSource}. The common functionality is tested in {@link
 * ByteSourceBaseTest}.
 */
@RunWith(JUnit4.class)
public class SlicedByteSourceTest extends ByteSourceBaseTest {

  private static File tempFile = null;
  private static byte[] testData;

  public SlicedByteSourceTest() {
    super(/* supportsMultipleStreams= */ false);
  }

  @BeforeClass
  public static void staticSetUp() throws Exception {
    testData = getSampleTestData();
    tempFile = storeInTempFile(new ByteArrayInputStream(testData));
  }

  @Before
  public void setUp() throws Exception {
    // Get some random range of the test data.
    int from = testData.length / 3;
    int to = Math.min(testData.length, from + testData.length / 2);
    expectedData = Arrays.copyOfRange(testData, from, to);

    byteSource = new RandomAccessFileByteSource(tempFile).slice(from, to - from);
  }

  @AfterClass
  public static void staticTearDown() throws Exception {
    if (tempFile != null) {
      tempFile.delete();
    }
  }

  @Test
  public void closeDoesNotAffectParent() throws Exception {
    // We can read from byteSource.
    try (InputStream in = byteSource.openStream()) {
      assertThat(in.read()).isEqualTo(expectedData[0]);
    }

    // We can create any number of slices and read from them.
    for (int i = 0; i < 10; i++) {
      try (InputStream in = byteSource.slice(1, 1).openStream()) {
        assertThat(in.read()).isEqualTo(expectedData[1]);
      }
    }

    // We can still read from byteSource.
    try (InputStream in = byteSource.openStream()) {
      assertThat(in.read()).isEqualTo(expectedData[0]);
    }
  }
}
