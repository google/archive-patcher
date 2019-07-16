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

import static com.google.archivepatcher.shared.TestUtils.assertThrows;
import static com.google.common.truth.Truth.assertThat;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link TempBlob}. */
@RunWith(JUnit4.class)
@SuppressWarnings("javadoc")
public class TempBlobTest {

  byte[] expected = new byte[] {2, 4, 0, 8};

  @Test
  public void testConstructAndClose() throws IOException {
    // Tests that a temp file can be created and that it is deleted upon close().
    File allocated = null;
    try (TempBlob blob = new TempBlob()) {
      assertThat(blob.file).isNotNull();
      assertThat(blob.file.exists()).isTrue();
      allocated = blob.file;
    }
    assertThat(allocated.exists()).isFalse();
  }

  @Test
  public void testLength() throws Exception {
    TempBlob tempBlob = new TempBlob();
    writeToBlob(expected, tempBlob);

    assertThat(tempBlob.length()).isEqualTo(expected.length);
  }

  @Test
  public void testWriteAndRead() throws Exception {
    TempBlob tempBlob = new TempBlob();
    writeToBlob(expected, tempBlob);

    byte[] read = new byte[expected.length];
    tempBlob.asByteSource().openStream().read(read);

    assertThat(read).isEqualTo(expected);
  }

  @Test
  public void testClearBlob() throws IOException {
    byte[] oldData = new byte[] {1, 2, 3, 4};
    byte[] newData = new byte[] {5, 6, 7, 8};
    TempBlob tempBlob = new TempBlob();
    writeToBlob(oldData, tempBlob);
    tempBlob.clear();
    writeToBlob(newData, tempBlob);

    byte[] read = new byte[newData.length];
    tempBlob.asByteSource().openStream().read(read);

    assertThat(read).isEqualTo(newData);
  }

  @Test
  public void testOpenStreamWithoutClosing() throws Exception {
    TempBlob tempBlob = new TempBlob();
    tempBlob.openOutputStream();

    assertThrows(IOException.class, tempBlob::openOutputStream);
  }

  @Test
  public void testClearWithoutClosing() throws Exception {
    TempBlob tempBlob = new TempBlob();
    tempBlob.openOutputStream();

    assertThrows(IOException.class, tempBlob::clear);
  }

  @Test
  public void testAsByteSourceWithoutClosing() throws Exception {
    TempBlob tempBlob = new TempBlob();
    tempBlob.openOutputStream();

    assertThrows(IOException.class, tempBlob::asByteSource);
  }

  private void writeToBlob(byte[] bytes, TempBlob tempBlob) throws IOException {
    OutputStream outputStream = tempBlob.openOutputStream();
    outputStream.write(bytes);
    outputStream.close();
  }
}
