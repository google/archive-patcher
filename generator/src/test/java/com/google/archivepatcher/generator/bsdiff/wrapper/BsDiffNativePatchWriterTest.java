// Copyright 2017 Google LLC. All rights reserved.
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

package com.google.archivepatcher.generator.bsdiff.wrapper;

import static com.google.archivepatcher.shared.bytesource.ByteStreams.copy;
import static com.google.common.truth.Truth.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class BsDiffNativePatchWriterTest {
  @Rule public TemporaryFolder mTemporaryFolder = new TemporaryFolder();

  private byte[] readTestData(String fileName) throws IOException {
    InputStream in = getClass().getResourceAsStream("testdata/" + fileName);
    ByteArrayOutputStream result = new ByteArrayOutputStream();
    copy(in, result);
    in.close();

    return result.toByteArray();
  }

  @Test
  public void testBsDiffNativePatchWriter() throws Exception {
    // Read old file
    byte[] oldData = readTestData("BsDiffInternalTestOld.txt");
    Path oldFile = Paths.get(mTemporaryFolder.getRoot().getAbsolutePath(), "oldFile.txt");
    Files.write(oldFile, oldData);

    // Read new file.
    byte[] newData = readTestData("BsDiffInternalTestNew.txt");
    Path newFile = Paths.get(mTemporaryFolder.getRoot().getAbsolutePath(), "newFile.txt");
    Files.write(newFile, newData);

    // Generate a patch based on the old file and the new file.
    ByteArrayOutputStream bsdiffOutputStream = new ByteArrayOutputStream();
    BsDiffNativePatchWriter.generatePatch(oldFile.toFile(), newFile.toFile(), bsdiffOutputStream);

    byte[] patchExpected = readTestData("BsDiffInternalTestPatchExpected.patch");
    assertThat(bsdiffOutputStream.toByteArray()).isEqualTo(patchExpected);
  }
}
