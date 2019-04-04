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
import static com.google.archivepatcher.shared.TestUtils.storeInTempFile;
import static com.google.common.truth.Truth.assertThat;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Test class specific for {@link RandomAccessFileByteSource}. The common functionality is tested in
 * {@link ByteSourceBaseTest}.
 */
@RunWith(JUnit4.class)
public class RandomAccessFileByteSourceTest extends ByteSourceBaseTest {

  private static File tempFile = null;
  private static byte[] testData;

  @BeforeClass
  public static void staticSetUp() throws Exception {
    testData = getSampleTestData();
    tempFile = storeInTempFile(new ByteArrayInputStream(testData));
  }

  @Before
  public void setUp() throws Exception {
    byteSource = new RandomAccessFileByteSource(tempFile);
    expectedData = testData;
  }

  @AfterClass
  public static void staticTearDown() throws Exception {
    if (tempFile != null) {
      tempFile.delete();
    }
  }

  @Test
  public void close() throws Exception {
    // We can read from byteSource.
    try (InputStream in = byteSource.openStream()) {
      assertThat(in.read()).isEqualTo(expectedData[0]);
    }

    byteSource.close();

    // We cannot read from byteSource.
    assertThrows(IOException.class, () -> byteSource.openStream().read());
  }
}
