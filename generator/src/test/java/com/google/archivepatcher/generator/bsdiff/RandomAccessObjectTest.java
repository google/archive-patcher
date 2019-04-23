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

package com.google.archivepatcher.generator.bsdiff;

import static com.google.archivepatcher.shared.TestUtils.storeInTempFile;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.RandomAccessFile;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class RandomAccessObjectTest {
  private static final byte[] BLOB = new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13};

  @Test
  public void fileLengthTest() throws Exception {
    File tmpFile = storeInTempFile(new ByteArrayInputStream(BLOB));

    try (RandomAccessObject obj = new RandomAccessObject.RandomAccessFileObject(tmpFile, "r")) {
      assertThat(obj.length()).isEqualTo(13);
    } finally {
      tmpFile.delete();
    }
  }

  @Test
  public void byteArrayLengthTest() throws Exception {
    try (RandomAccessObject obj = new RandomAccessObject.RandomAccessByteArrayObject(BLOB)) {
      assertThat(obj.length()).isEqualTo(13);
    }
  }

  @Test
  public void mmapLengthTest() throws Exception {
    File tmpFile = storeInTempFile(new ByteArrayInputStream(BLOB));

    try (RandomAccessObject obj =
        new RandomAccessObject.RandomAccessMmapObject(new RandomAccessFile(tmpFile, "r"), "r")) {
      assertThat(obj.length()).isEqualTo(13);
    } finally {
      tmpFile.delete();
    }
  }

  @Test
  public void fileReadByteTest() throws Exception {
    File tmpFile = storeInTempFile(new ByteArrayInputStream(BLOB));

    try (RandomAccessObject obj = new RandomAccessObject.RandomAccessFileObject(tmpFile, "r")) {
      for (int x = 0; x < BLOB.length; x++) {
        assertThat(obj.readByte()).isEqualTo(x + 1);
      }

      try {
        obj.readByte();
        assertWithMessage("Should've thrown an Exception").fail();
      } catch (Exception expected) {
      }
    } finally {
      tmpFile.delete();
    }
  }

  @Test
  public void byteArrayReadByteTest() throws Exception {
    // Mix positives and negatives to test sign preservation in readByte()
    byte[] bytes = new byte[] {-128, -127, -126, -1, 0, 1, 125, 126, 127};
    try (RandomAccessObject obj = new RandomAccessObject.RandomAccessByteArrayObject(bytes)) {
      for (int x = 0; x < bytes.length; x++) {
        assertThat(obj.readByte()).isEqualTo(bytes[x]);
      }

      try {
        obj.readByte();
        assertWithMessage("Should've thrown an Exception").fail();
      } catch (BufferUnderflowException expected) {
      }
    }
  }

  @Test
  public void byteArrayReadUnsignedByteTest() throws Exception {
    // Test values above 127 to test unsigned-ness of readUnsignedByte()
    int[] ints = new int[] {255, 254, 253};
    byte[] bytes = new byte[] {(byte) 0xff, (byte) 0xfe, (byte) 0xfd};
    try (RandomAccessObject obj = new RandomAccessObject.RandomAccessByteArrayObject(bytes)) {
      for (int x = 0; x < bytes.length; x++) {
        assertThat(obj.readUnsignedByte()).isEqualTo(ints[x]);
      }

      try {
        obj.readUnsignedByte();
        assertWithMessage("Should've thrown an Exception").fail();
      } catch (BufferUnderflowException expected) {
      }
    }
  }

  @Test
  public void mmapReadByteTest() throws Exception {
    File tmpFile = storeInTempFile(new ByteArrayInputStream(BLOB));

    try (RandomAccessObject obj =
        new RandomAccessObject.RandomAccessMmapObject(new RandomAccessFile(tmpFile, "r"), "r")) {
      for (int x = 0; x < BLOB.length; x++) {
        assertThat(obj.readByte()).isEqualTo(x + 1);
      }

      try {
        obj.readByte();
        assertWithMessage("Should've thrown an BufferUnderflowException").fail();
      } catch (BufferUnderflowException expected) {
      }
    } finally {
      tmpFile.delete();
    }
  }

  @Test
  public void fileWriteByteTest() throws Exception {
    File tmpFile = storeInTempFile(new ByteArrayInputStream(BLOB));

    try (RandomAccessObject obj = new RandomAccessObject.RandomAccessFileObject(tmpFile, "rw")) {
      for (int x = 0; x < BLOB.length; x++) {
        obj.writeByte((byte) (5 - x));
      }

      // Writing a byte past the end of a file should be ok - this just extends the file.
      obj.writeByte((byte) 243);

      // As per RandomAccessFile documentation, the reported length should update after writing off
      // the end of a file.
      assertThat(obj.length()).isEqualTo(BLOB.length + 1);

      obj.seek(0);
      for (int x = 0; x < BLOB.length; x++) {
        assertThat(obj.readByte()).isEqualTo(5 - x);
      }

      // Note that because of signed bytes, if cased to an int, this would actually resolve to -13.
      assertThat(obj.readByte()).isEqualTo((byte) 243);

      try {
        obj.readByte();
        assertWithMessage("Should've thrown an Exception").fail();
      } catch (Exception expected) {
      }
    } finally {
      tmpFile.delete();
    }
  }

  @Test
  public void fileWriteByteToEmptyFileTest() throws Exception {
    File tmpFile = File.createTempFile("RandomAccessObjectTest", "temp");

    try (RandomAccessObject obj = new RandomAccessObject.RandomAccessFileObject(tmpFile, "rw")) {
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
  public void byteArrayWriteByteTest() throws Exception {
    final int len = 13;
    try (RandomAccessObject obj =
        new RandomAccessObject.RandomAccessByteArrayObject(new byte[len])) {
      for (int x = 0; x < len; x++) {
        obj.writeByte((byte) (5 - x));
      }

      try {
        // Writing a byte past the end of an array is not ok.
        obj.writeByte((byte) 243);
        assertWithMessage("Should've thrown a BufferOverflowException").fail();
      } catch (BufferOverflowException expected) {
      }

      obj.seek(0);
      for (int x = 0; x < len; x++) {
        assertThat(obj.readByte()).isEqualTo(5 - x);
      }

      try {
        obj.readByte();
        assertWithMessage("Should've thrown a BufferUnderflowException").fail();
      } catch (BufferUnderflowException expected) {
      }
    }
  }

  @Test
  public void mmapWriteByteTest() throws Exception {
    File tmpFile = storeInTempFile(new ByteArrayInputStream(BLOB));

    try (RandomAccessObject obj =
        new RandomAccessObject.RandomAccessMmapObject(new RandomAccessFile(tmpFile, "rw"), "rw")) {
      for (int x = 0; x < BLOB.length; x++) {
        obj.writeByte((byte) (5 - x));
      }

      try {
        // Writing a byte past the end of an mmap is not ok.
        obj.writeByte((byte) 243);
        assertWithMessage("Should've thrown an BufferOverflowException").fail();
      } catch (BufferOverflowException expected) {
      }

      assertThat(obj.length()).isEqualTo(BLOB.length);

      obj.seek(0);
      for (int x = 0; x < BLOB.length; x++) {
        assertThat(obj.readByte()).isEqualTo(5 - x);
      }

      try {
        obj.readByte();
        assertWithMessage("Should've thrown an BufferUnderflowException").fail();
      } catch (BufferUnderflowException expected) {
      }
    } finally {
      tmpFile.delete();
    }
  }

  @Test
  public void mmapWriteByteToEmptyFileTest() throws Exception {
    File tmpFile = File.createTempFile("RandomAccessObjectTest", "temp");

    try (RandomAccessObject obj =
        new RandomAccessObject.RandomAccessMmapObject(new RandomAccessFile(tmpFile, "rw"), "rw")) {
      for (int x = 0; x < BLOB.length; x++) {
        try {
          // Writing a byte past the end of an mmap is not ok.
          obj.writeByte((byte) (5 - x));
          assertWithMessage("Should've thrown an BufferOverflowException").fail();
        } catch (BufferOverflowException expected) {
        }
      }

      try {
        obj.seek(BLOB.length);
        assertWithMessage("Should've thrown an IllegalArgumentException").fail();
      } catch (IllegalArgumentException expected) {
      }

      for (int x = 0; x < BLOB.length; x++) {
        try {
          obj.readByte();
          assertWithMessage("Should've thrown an BufferUnderflowException").fail();
        } catch (BufferUnderflowException expected) {
        }
      }

      assertThat(obj.length()).isEqualTo(0);
    } finally {
      tmpFile.delete();
    }
  }

  @Test
  public void fileSeekTest() throws Exception {
    File tmpFile = storeInTempFile(new ByteArrayInputStream(BLOB));

    try {
      RandomAccessObject obj = new RandomAccessObject.RandomAccessFileObject(tmpFile, "rw");
      seekTest(obj);

      try {
        obj.seek(-1);
        assertWithMessage("Should've thrown an Exception").fail();
      } catch (Exception expected) {
      }

      // This should not throw an exception.
      obj.seek(BLOB.length);
    } finally {
      tmpFile.delete();
    }
  }

  @Test
  public void byteArraySeekTest() throws Exception {
    byte[] data = new byte[BLOB.length];
    System.arraycopy(BLOB, 0, data, 0, BLOB.length);
    RandomAccessObject obj = new RandomAccessObject.RandomAccessByteArrayObject(data);
    seekTest(obj);

    try {
      obj.seek(-1);
      assertWithMessage("Should've thrown an IllegalArgumentException").fail();
    } catch (IllegalArgumentException expected) {
    }

    // Should not fail.
    obj.seek(BLOB.length);

    // Only fails once you try to read past the end.
    try {
      obj.readByte();
      assertWithMessage("Should've thrown a BufferUnderflowException").fail();
    } catch (BufferUnderflowException expected) {
    }
  }

  @Test
  public void mmapSeekTest() throws Exception {
    File tmpFile = storeInTempFile(new ByteArrayInputStream(BLOB));

    try {
      RandomAccessObject obj =
          new RandomAccessObject.RandomAccessMmapObject(new RandomAccessFile(tmpFile, "rw"), "rw");
      seekTest(obj);

      try {
        obj.seek(-1);
        assertWithMessage("Should've thrown an IllegalArgumentException").fail();
      } catch (IllegalArgumentException expected) {
      }

      // Should not fail.
      obj.seek(BLOB.length);

      // Only fails once you try to read past the end.
      try {
        obj.readByte();
        assertWithMessage("Should've thrown a BufferUnderflowException").fail();
      } catch (BufferUnderflowException expected) {
      }
    } finally {
      tmpFile.delete();
    }
  }

  @Test
  public void fileReadIntTest() throws Exception {
    File tmpFile = storeInTempFile(new ByteArrayInputStream(BLOB));

    try {
      RandomAccessObject obj = new RandomAccessObject.RandomAccessFileObject(tmpFile, "r");
      readIntTest(obj);
      try {
        obj.readInt();
        assertWithMessage("Should've thrown a EOFException").fail();
      } catch (EOFException expected) {
      }
    } finally {
      tmpFile.delete();
    }
  }

  @Test
  public void byteArrayReadIntTest() throws Exception {
    RandomAccessObject obj = new RandomAccessObject.RandomAccessByteArrayObject(BLOB);
    readIntTest(obj);
    try {
      obj.readInt();
      assertWithMessage("Should've thrown a BufferUnderflowException").fail();
    } catch (BufferUnderflowException expected) {
    }
  }

  @Test
  public void mmapReadIntTest() throws Exception {
    File tmpFile = storeInTempFile(new ByteArrayInputStream(BLOB));

    try {
      RandomAccessObject obj =
          new RandomAccessObject.RandomAccessMmapObject(new RandomAccessFile(tmpFile, "r"), "r");
      readIntTest(obj);

      try {
        obj.readInt();
        assertWithMessage("Should've thrown an BufferUnderflowException").fail();
      } catch (BufferUnderflowException expected) {
      }
    } finally {
      tmpFile.delete();
    }
  }

  @Test
  public void fileWriteIntTest() throws Exception {
    File tmpFile = storeInTempFile(new ByteArrayInputStream(BLOB));

    try (RandomAccessObject obj = new RandomAccessObject.RandomAccessFileObject(tmpFile, "rw")) {
      for (int x = 0; x < BLOB.length / 4; x++) {
        obj.writeInt(500 + x);
      }

      obj.seekToIntAligned(0);
      for (int x = 0; x < BLOB.length / 4; x++) {
        assertThat(obj.readInt()).isEqualTo(500 + x);
      }
    } finally {
      tmpFile.delete();
    }
  }

  @Test
  public void byteArrayWriteIntTest() throws Exception {
    final int len = 13;
    try (RandomAccessObject obj =
        new RandomAccessObject.RandomAccessByteArrayObject(new byte[len])) {
      for (int x = 0; x < len / 4; x++) {
        obj.writeInt(500 + x);
      }

      obj.seek(0);
      for (int x = 0; x < len / 4; x++) {
        assertThat(obj.readInt()).isEqualTo(500 + x);
      }
    }
  }

  @Test
  public void mmapWriteIntTest() throws Exception {
    File tmpFile = storeInTempFile(new ByteArrayInputStream(BLOB));

    try (RandomAccessObject obj =
        new RandomAccessObject.RandomAccessMmapObject(new RandomAccessFile(tmpFile, "rw"), "rw")) {
      for (int x = 0; x < BLOB.length / 4; x++) {
        obj.writeInt(500 + x);
      }

      obj.seekToIntAligned(0);
      for (int x = 0; x < BLOB.length / 4; x++) {
        assertThat(obj.readInt()).isEqualTo(500 + x);
      }
    } finally {
      tmpFile.delete();
    }
  }

  @Test
  public void fileSeekToIntAlignedTest() throws Exception {
    File tmpFile = storeInTempFile(new ByteArrayInputStream(BLOB));

    try {
      RandomAccessObject obj = new RandomAccessObject.RandomAccessFileObject(tmpFile, "rw");
      seekToIntAlignedTest(obj);
    } finally {
      tmpFile.delete();
    }
  }

  @Test
  public void byteArraySeekToIntAlignedTest() throws Exception {
    byte[] data = new byte[BLOB.length];
    System.arraycopy(BLOB, 0, data, 0, BLOB.length);
    RandomAccessObject obj = new RandomAccessObject.RandomAccessByteArrayObject(data);
    seekToIntAlignedTest(obj);
  }

  @Test
  public void mmapSeekToIntAlignedTest() throws Exception {
    File tmpFile = storeInTempFile(new ByteArrayInputStream(BLOB));

    try {
      RandomAccessObject obj =
          new RandomAccessObject.RandomAccessMmapObject(new RandomAccessFile(tmpFile, "rw"), "rw");
      seekToIntAlignedTest(obj);
    } finally {
      tmpFile.delete();
    }
  }

  @Test
  public void fileCloseTest() throws Exception {
    File tmpFile = storeInTempFile(new ByteArrayInputStream(BLOB));

    try {
      RandomAccessObject obj = new RandomAccessObject.RandomAccessFileObject(tmpFile, "r", true);
      obj.close();
      assertThat(tmpFile.exists()).isFalse();
      tmpFile = null;
    } finally {
      if (tmpFile != null) {
        tmpFile.delete();
      }
    }

    tmpFile = storeInTempFile(new ByteArrayInputStream(BLOB));

    try {
      RandomAccessObject obj = new RandomAccessObject.RandomAccessFileObject(tmpFile, "r");
      obj.close();
      assertThat(tmpFile.exists()).isTrue();
    } finally {
      tmpFile.delete();
    }
  }

  @Test
  public void mmapCloseTest() throws Exception {
    File tmpFile = storeInTempFile(new ByteArrayInputStream(BLOB));

    try {
      try (RandomAccessObject obj = new RandomAccessObject.RandomAccessMmapObject(tmpFile, "r")) {}
      assertThat(tmpFile.exists()).isFalse();
      tmpFile = null;
    } finally {
      if (tmpFile != null) {
        tmpFile.delete();
      }
    }

    tmpFile = storeInTempFile(new ByteArrayInputStream(BLOB));

    try {
      try (RandomAccessObject obj =
          new RandomAccessObject.RandomAccessMmapObject(new RandomAccessFile(tmpFile, "r"), "r")) {}
      assertThat(tmpFile.exists()).isTrue();
    } finally {
      tmpFile.delete();
    }
  }

  private void seekTest(final RandomAccessObject obj) throws Exception {
    obj.seek(7);
    assertThat(obj.readByte()).isEqualTo(8);
    obj.seek(3);
    assertThat(obj.readByte()).isEqualTo(4);
    obj.seek(9);
    assertThat(obj.readByte()).isEqualTo(10);
    obj.seek(5);
    obj.writeByte((byte) 23);
    obj.seek(5);
    assertThat(obj.readByte()).isEqualTo(23);
    obj.seek(4);
    assertThat(obj.readByte()).isEqualTo(5);

    obj.seek(0);
    for (int x = 0; x < BLOB.length; x++) {
      if (x == 5) {
        assertThat(obj.readByte()).isEqualTo(23);
      } else {
        assertThat(obj.readByte()).isEqualTo(x + 1);
      }
    }
  }

  private void readIntTest(final RandomAccessObject obj) throws Exception {
    assertThat(obj.readInt()).isEqualTo(0x01020304);
    assertThat(obj.readInt()).isEqualTo(0x05060708);
    assertThat(obj.readInt()).isEqualTo(0x090A0B0C);
  }

  private void seekToIntAlignedTest(final RandomAccessObject obj) throws Exception {
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
}
