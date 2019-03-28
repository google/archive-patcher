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

package com.google.archivepatcher.shared;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import javax.annotation.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SeekableDataInputTest {

  private static final byte[] DATA = {-1, 2, 3, 4, 5, 6, 7, 8, 9, 10};

  // Tracking the temporary files we used so that we can clean them up afterwards.
  @Nullable private static File tmpFile;

  private final CheckedDataInputSupplier dataInputSupplier;

  // DataInput under test.
  private SeekableDataInput input;

  @Parameters
  public static Collection<Object[]> data() throws Exception {
    return Arrays.asList(
        new Object[][] {
          {
            (CheckedDataInputSupplier)
                data -> {
                  tmpFile = storeInTempFile(data);
                  return SeekableDataInput.fromFile(tmpFile);
                }
          }
        });
  }

  public SeekableDataInputTest(CheckedDataInputSupplier dataInputSupplier) {
    this.dataInputSupplier = dataInputSupplier;
  }

  @Before
  public void setUp() throws Exception {
    tmpFile = null;
    input = dataInputSupplier.get(DATA);
  }

  @After
  public void tearDown() {
    try {
      input.close();
    } catch (Exception ignored) {
    }

    if (tmpFile != null) {
      try {
        tmpFile.delete();
      } catch (Exception ignored) {
      }
    }
  }

  @Test
  public void length() throws Exception {
    assertThat(input.length()).isEqualTo(DATA.length);
  }

  @Test
  public void seek_getPosition() throws Exception {
    assertThat(input.getPosition()).isEqualTo(0);

    input.seek(5);
    assertThat(input.getPosition()).isEqualTo(5);

    input.readByte();
    assertThat(input.getPosition()).isEqualTo(6);

    input.readInt();
    assertThat(input.getPosition()).isEqualTo(10);

    input.seek(0);
    byte[] buffer = new byte[DATA.length];

    input.readFully(buffer);
    assertThat(input.getPosition()).isEqualTo(DATA.length);
  }

  @Test
  public void close() throws Exception {
    input.close();

    assertThrows(Exception.class, () -> input.readByte());
  }

  @Test
  public void readFully_enoughData() throws Exception {
    byte[] buffer = new byte[DATA.length];

    input.readFully(buffer);

    assertThat(buffer).isEqualTo(DATA);
  }

  @Test
  public void readFully_notEnoughData() throws Exception {
    byte[] buffer = new byte[DATA.length + 1];

    assertThrows(IOException.class, () -> input.readFully(buffer));
  }

  @Test
  public void readFully_enoughData_withOffset() throws Exception {
    byte[] buffer = new byte[DATA.length + 1];

    input.readFully(buffer, 1, DATA.length);

    for (int i = 1; i < buffer.length; ++i) {
      assertThat(buffer[i]).isEqualTo(DATA[i - 1]);
    }
  }

  @Test
  public void readFully_notEnoughData_withOffset() throws Exception {
    byte[] buffer = new byte[DATA.length + 2];

    assertThrows(IOException.class, () -> input.readFully(buffer, 1, DATA.length + 1));
  }

  @Test
  public void readByte() throws Exception {
    for (byte b : DATA) {
      assertThat(input.readByte()).isEqualTo(b);
    }
  }

  @Test
  public void readUnsignedByte() throws Exception {
    assertThat(input.readUnsignedByte()).isEqualTo(255);
    assertThat(input.readUnsignedByte()).isEqualTo(2);
  }

  private interface CheckedDataInputSupplier {
    SeekableDataInput get(byte[] data) throws Exception;
  }

  private interface CheckedRunnable {
    void run() throws Throwable;
  }

  private static File storeInTempFile(byte[] data) throws IOException {
    File tmpFile = null;
    try {
      tmpFile = File.createTempFile("SeekableDataInputTest", "temp");
      tmpFile.deleteOnExit();
      FileOutputStream out = new FileOutputStream(tmpFile);
      out.write(data);
      out.flush();
      out.close();
      return tmpFile;
    } catch (IOException e) {
      if (tmpFile != null) {
        // Attempt immediate cleanup.
        tmpFile.delete();
      }
      throw e;
    }
  }

  public static <T extends Throwable> void assertThrows(
      Class<T> expectedThrowable, CheckedRunnable runnable) {
    try {
      runnable.run();
    } catch (Throwable actualThrowable) {
      if (!expectedThrowable.isAssignableFrom(actualThrowable.getClass())) {
        assertWithMessage(
                "Wrong throwable type detected. Expected "
                    + expectedThrowable
                    + " Got "
                    + actualThrowable)
            .fail();
      }
      return;
    }
    assertWithMessage("No throwable detected. Expected " + expectedThrowable).fail();
  }
}
