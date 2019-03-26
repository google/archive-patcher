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

import static com.google.common.truth.Truth.assertThat;

import java.io.File;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link TempFileHolder}. */
@RunWith(JUnit4.class)
@SuppressWarnings("javadoc")
public class TempFileHolderTest {
  @Test
  public void testConstructAndClose() throws IOException {
    // Tests that a temp file can be created and that it is deleted upon close().
    File allocated = null;
    try(TempFileHolder holder = new TempFileHolder()) {
      assertThat(holder.file).isNotNull();
      assertThat(holder.file.exists()).isTrue();
      allocated = holder.file;
    }
    assertThat(allocated.exists()).isFalse();
  }
}
