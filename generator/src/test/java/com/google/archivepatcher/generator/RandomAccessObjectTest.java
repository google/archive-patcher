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

import static com.google.archivepatcher.shared.Assert.assertThrows;
import static com.google.common.truth.Truth.assertThat;

import com.google.archivepatcher.shared.Assert.CheckedSupplier;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.util.Arrays;
import java.util.Collection;
import javax.annotation.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/** Test for subclasses of {@link RandomAccessObject}. */
@RunWith(Enclosed.class)
public class RandomAccessObjectTest {

  public static final byte[] BLOB = new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13};

  @RunWith(Parameterized.class)
  public static class GeneralTest {

    @Nullable private static File tmpFile;

    @Parameters
    public static Collection<Object[]> data() throws Exception {
      return Arrays.asList(
          new Object[][] {
            {
              (CheckedSupplier<RandomAccessObject>) () -> new RandomAccessFileObject(tmpFile, "rw"),
              /* requireTmpFile= */ true
            },
            {
              (CheckedSupplier<RandomAccessObject>)
                  () -> new RandomAccessByteArrayObject(Arrays.copyOf(BLOB, BLOB.length)),
              /* requireTmpFile= */ false
            },
            {
              (CheckedSupplier<RandomAccessObject>)
                  () -> new RandomAccessMmapObject(new RandomAccessFile(tmpFile, "rw"), "rw"),
              /* requireTmpFile= */ true
            }
          });
    }

    private RandomAccessObject obj;

    private final CheckedSupplier<RandomAccessObject> objGetter;
    private final boolean requireTmpFile;

    /**
     * We have to use a {@link CheckedSupplier} since we need to create a fresh instance for every
     * test.
     */
    public GeneralTest(CheckedSupplier<RandomAccessObject> objGetter, boolean requireTmpFile) {
      this.objGetter = objGetter;
      this.requireTmpFile = requireTmpFile;
    }

    @Before
    public void setUp() throws Exception {
      if (requireTmpFile) {
        tmpFile = storeInTempFile(new ByteArrayInputStream(BLOB));
      }
      this.obj = objGetter.get();
    }

    @After
    public void tearDown() throws Exception {
      if (requireTmpFile) {
        tmpFile.delete();
      }
      obj.close();
    }

    @Test
    public void length() throws Exception {
      assertThat(obj.length()).isEqualTo(BLOB.length);
    }

    @Test
    public void readByte() throws Exception {
      for (int x = 0; x < BLOB.length; x++) {
        assertThat(obj.readByte()).isEqualTo(BLOB[x]);
      }

      assertEof(obj);
    }

    @Test
    public void writeByte() throws Exception {
      for (int x = 0; x < BLOB.length; x++) {
        obj.writeByte((byte) (5 - x));
      }

      int expectedNewLength = BLOB.length;
      // Writing a byte past the end of a file should be ok - this just extends the file.
      if (obj instanceof RandomAccessByteArrayObject) {
        assertThrows(BufferOverflowException.class, () -> obj.writeByte((byte) 243));
      } else {
        obj.writeByte((byte) 243);
        expectedNewLength += 1;
      }

      // As per RandomAccessFile documentation, the reported length should update after writing off
      // the end of a file.
      assertThat(obj.length()).isEqualTo(expectedNewLength);

      obj.seek(0);
      for (int x = 0; x < BLOB.length; x++) {
        assertThat(obj.readByte()).isEqualTo(5 - x);
      }

      // Note that because of signed bytes, if cased to an int, this would actually resolve to -13.

      if (expectedNewLength > BLOB.length) {
        assertThat(obj.readByte()).isEqualTo((byte) 243);
      }

      assertEof(obj);
    }

    @Test
    public void seek() throws Exception {
      obj.seek(7);
      assertThat(obj.readByte()).isEqualTo(BLOB[7]);
      obj.seek(3);
      assertThat(obj.readByte()).isEqualTo(BLOB[3]);
      obj.seek(9);
      assertThat(obj.readByte()).isEqualTo(BLOB[9]);
      obj.seek(5);
      obj.writeByte((byte) 23);
      obj.seek(5);
      assertThat(obj.readByte()).isEqualTo(23);
      obj.seek(4);
      assertThat(obj.readByte()).isEqualTo(BLOB[4]);

      obj.seek(0);
      for (int x = 0; x < BLOB.length; x++) {
        if (x == 5) {
          assertThat(obj.readByte()).isEqualTo(23);
        } else {
          assertThat(obj.readByte()).isEqualTo(BLOB[x]);
        }
      }

      assertThrows(Exception.class, () -> obj.seek(-1));

      // This should not throw an exception.
      obj.seek(BLOB.length);

      assertEof(obj);
    }

    @Test
    public void readInt() throws Exception {
      assertThat(obj.readInt()).isEqualTo(0x01020304);
      assertThat(obj.readInt()).isEqualTo(0x05060708);
      assertThat(obj.readInt()).isEqualTo(0x090A0B0C);

      assertEof(obj);
    }

    @Test
    public void writeInt() throws Exception {
      for (int x = 0; x < BLOB.length / 4; x++) {
        obj.writeInt(500 + x);
      }

      obj.seekToIntAligned(0);
      for (int x = 0; x < BLOB.length / 4; x++) {
        assertThat(obj.readInt()).isEqualTo(500 + x);
      }
    }

    @Test
    public void seekToIntAligned() throws Exception {
      obj.seekToIntAligned(3);
      assertThat(obj.readByte()).isEqualTo(3 * 4 + 1);

      obj.seekToIntAligned(2);
      assertThat(obj.readByte()).isEqualTo(2 * 4 + 1);
      assertThat(obj.readInt()).isEqualTo(0x0A0B0C0D);

      obj.seekToIntAligned(0);
      assertThat(obj.readByte()).isEqualTo(1);

      obj.seekToIntAligned(1);
      assertThat(obj.readByte()).isEqualTo(5);
      assertThat(obj.readInt()).isEqualTo(0x06070809);

      obj.seekToIntAligned(2);
      obj.writeInt(0x26391bd2);

      obj.seekToIntAligned(0);
      assertThat(obj.readInt()).isEqualTo(0x01020304);
      assertThat(obj.readInt()).isEqualTo(0x05060708);
      assertThat(obj.readInt()).isEqualTo(0x26391bd2);
    }

    @Test
    public void close() throws Exception {
      obj.close();
      if (!(obj instanceof RandomAccessByteArrayObject)) {
        assertThrows(IOException.class, () -> obj.readByte());
      }
    }
  }

  @RunWith(JUnit4.class)
  public static class SpecialTest {

    @Test
    public void byteArrayReadUnsignedByteTest() throws Exception {
      // Test values above 127 to test unsigned-ness of readUnsignedByte()
      int[] ints = new int[] {255, 254, 253};
      byte[] bytes = new byte[] {(byte) 0xff, (byte) 0xfe, (byte) 0xfd};
      try (RandomAccessObject obj = new RandomAccessByteArrayObject(bytes)) {
        for (int x = 0; x < bytes.length; x++) {
          assertThat(obj.readUnsignedByte()).isEqualTo(ints[x]);
        }

        assertThrows(BufferUnderflowException.class, obj::readUnsignedByte);
      }
    }

    @Test
    public void fileWriteByteToEmptyFileTest() throws Exception {
      File tmpFile = File.createTempFile("RandomAccessObjectTest", "temp");

      try (RandomAccessObject obj = new RandomAccessFileObject(tmpFile, "rw")) {
        for (int x = 0; x < BLOB.length; x++) {
          obj.writeByte((byte) (5 - x));
        }

        obj.seek(0);
        for (int x = 0; x < BLOB.length; x++) {
          assertThat(obj.readByte()).isEqualTo(5 - x);
        }

        assertThat(obj.length()).isEqualTo(BLOB.length);
      } finally {
        tmpFile.delete();
      }
    }

    @Test
    public void mmapWriteByteToEmptyFileTest() throws Exception {
      File tmpFile = File.createTempFile("RandomAccessObjectTest", "temp");

      try (RandomAccessObject obj =
          new RandomAccessMmapObject(new RandomAccessFile(tmpFile, "rw"), "rw")) {
        for (int x = 0; x < BLOB.length; x++) {
          // Variable in lambda must be final/effectively final.
          final int y = x;
          assertThrows(BufferOverflowException.class, () -> obj.writeByte((byte) (5 - y)));
        }

        assertThrows(IllegalArgumentException.class, () -> obj.seek(BLOB.length));

        for (int x = 0; x < BLOB.length; x++) {
          assertThrows(BufferUnderflowException.class, () -> obj.readByte());
        }

        assertThat(obj.length()).isEqualTo(0);
      } finally {
        tmpFile.delete();
      }
    }

  }

  private static File storeInTempFile(InputStream content) throws Exception {
    File tmpFile = null;
    try {
      tmpFile = File.createTempFile("RandomAccessObjectTest", "temp");
      tmpFile.deleteOnExit();
      FileOutputStream out = new FileOutputStream(tmpFile);
      byte[] buffer = new byte[32768];
      int numRead = 0;
      while ((numRead = content.read(buffer)) >= 0) {
        out.write(buffer, 0, numRead);
      }
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

  private static void assertEof(RandomAccessObject obj) {
    assertThrows(Exception.class, () -> obj.readInt());
  }
}
