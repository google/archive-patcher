// Copyright 2017 Google Inc. All rights reserved.
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

import static org.junit.Assert.assertArrayEquals;

import com.google.archivepatcher.applier.bsdiff.BsPatch;
import com.google.archivepatcher.generator.bsdiff.RandomAccessObject.RandomAccessFileObject;
import com.google.common.io.Files;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
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
    byte[] buffer = new byte[32768];
    int numRead;
    while ((numRead = in.read(buffer)) >= 0) {
      result.write(buffer, 0, numRead);
    }

    in.close();

    return result.toByteArray();
  }

  @Test
  public void testBsDiffNativePatchWriter() throws Exception {
    // Read old file
    byte[] oldData = readTestData("BsDiffInternalTestOld.txt");
    File oldFile = new File(mTemporaryFolder.getRoot(), "oldFile.txt");
    Files.write(oldData, oldFile);

    // Read new file.
    byte[] newData = readTestData("BsDiffInternalTestNew.txt");
    File newFile = new File(mTemporaryFolder.getRoot(), "newFile.txt");
    Files.write(newData, newFile);

    // Generate a patch based on the old file and the new file.
    ByteArrayOutputStream bsdiffOutputStream = new ByteArrayOutputStream();
    BsDiffNativePatchWriter.generatePatch(oldFile, newFile, bsdiffOutputStream);

    // Apply the generated patch to the old file and check if the result is the expected new file.
    ByteArrayInputStream patchInputStream =
        new ByteArrayInputStream(bsdiffOutputStream.toByteArray());
    ByteArrayOutputStream patchApplyResult = new ByteArrayOutputStream();
    RandomAccessFile randomAccessOldFile = new RandomAccessFileObject(oldFile, "r");
    BsPatch.applyPatch(randomAccessOldFile, patchApplyResult, patchInputStream);

    assertArrayEquals(newData, patchApplyResult.toByteArray());
  }
}
