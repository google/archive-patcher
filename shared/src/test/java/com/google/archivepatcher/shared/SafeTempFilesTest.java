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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test for {@link SafeTempFiles}. */
@RunWith(JUnit4.class)
public class SafeTempFilesTest {

  @Test
  public void testTempFilePermission() throws IOException {
    File file = SafeTempFiles.createTempFile("prefix", "suffix");
    assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(file.toPath())))
        .isEqualTo("rw-------");
  }
}
