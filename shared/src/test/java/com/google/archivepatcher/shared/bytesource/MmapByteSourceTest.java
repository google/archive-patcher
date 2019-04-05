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

import java.io.ByteArrayInputStream;
import java.io.File;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class MmapByteSourceTest extends ByteSourceBaseTest {

  private static File tempFile = null;
  private static byte[] testData;

  @BeforeClass
  public static void staticSetUp() throws Exception {
    testData = getSampleTestData();
    tempFile = storeInTempFile(new ByteArrayInputStream(testData));
  }

  @Before
  public void setUp() throws Exception {
    byteSource = new MmapByteSource(tempFile);
    expectedData = testData;
  }

  @AfterClass
  public static void staticTearDown() throws Exception {
    if (tempFile != null) {
      tempFile.delete();
    }
  }
}
