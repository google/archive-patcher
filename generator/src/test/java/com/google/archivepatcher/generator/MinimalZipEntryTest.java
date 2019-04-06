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

package com.google.archivepatcher.generator;

import static com.google.archivepatcher.generator.MinimalZipEntryUtils.getFakeBuilder;
import static com.google.common.truth.Truth.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test for {@link MinimalZipEntry}. */
@RunWith(JUnit4.class)
public class MinimalZipEntryTest {

  @Test
  public void getFilename() throws Exception {
    // Make a string with some chars that are from DOS ANSI art days, these chars have different
    // binary representations in UTF8 and Cp437. We use light, medium, and dark "shade" characters
    // (0x2591, 0x2592, 0x2593 respectively) for this purpose. Go go ANSI art!
    // https://en.wikipedia.org/wiki/Code_page_437
    // https://en.wikipedia.org/wiki/Block_Elements
    String fileName = "\u2591\u2592\u2593AWESOME\u2593\u2592\u2591";
    byte[] utf8Bytes = fileName.getBytes(StandardCharsets.UTF_8);
    byte[] cp437Bytes = fileName.getBytes("Cp437");
    assertThat(utf8Bytes).isNotEqualTo(cp437Bytes);

    MinimalZipEntry utf8Entry =
        getFakeBuilder().fileNameBytes(utf8Bytes).useUtf8Encoding(true).build();
    assertThat(utf8Entry.fileNameBytes()).isEqualTo(utf8Bytes);
    assertThat(utf8Entry.getFileName()).isEqualTo(fileName);

    MinimalZipEntry cp437Entry =
        getFakeBuilder().fileNameBytes(cp437Bytes).useUtf8Encoding(false).build();
    assertThat(cp437Entry.fileNameBytes()).isEqualTo(cp437Bytes);
    assertThat(cp437Entry.getFileName()).isEqualTo(fileName);
  }
}
