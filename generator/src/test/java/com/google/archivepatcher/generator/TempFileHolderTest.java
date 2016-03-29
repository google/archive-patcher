// Copyright 2016 Google Inc. All rights reserved.
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

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.io.IOException;

/**
* Tests for {@link TempFileHolder}.
*/
@RunWith(JUnit4.class)
@SuppressWarnings("javadoc")
public class TempFileHolderTest {
  @Test
  public void testConstructAndClose() throws IOException {
    // Tests that a temp file can be created and that it is deleted upon close().
    File allocated = null;
    try(TempFileHolder holder = new TempFileHolder()) {
      Assert.assertNotNull(holder.file);
      Assert.assertTrue(holder.file.exists());
      allocated = holder.file;
    }
    Assert.assertFalse(allocated.exists());
  }
}
