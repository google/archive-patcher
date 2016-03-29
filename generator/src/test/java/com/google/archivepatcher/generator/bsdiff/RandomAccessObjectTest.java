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

package com.google.archivepatcher.generator.bsdiff;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;

@RunWith(JUnit4.class)
public class RandomAccessObjectTest {
  private static final byte[] BLOB = new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13};

  @Test
  public void fileLengthTest() throws IOException {
    File tmpFile = storeInTempFile(new ByteArrayInputStream(BLOB));

    try (RandomAccessObject obj = new RandomAccessObject.RandomAccessFileObject(tmpFile, "r")) {
      Assert.assertEquals(13, obj.length());
    } finally {
      tmpFile.delete();
    }
  }

  @Test
  public void byteArrayLengthTest() throws IOException {
    try (RandomAccessObject obj = new RandomAccessObject.RandomAccessByteArrayObject(BLOB)) {
      Assert.assertEquals(13, obj.length());
    }
  }

  @Test
  public void mmapLengthTest() throws IOException {
    File tmpFile = storeInTempFile(new ByteArrayInputStream(BLOB));

    try (RandomAccessObject obj =
        new RandomAccessObject.RandomAccessMmapObject(new RandomAccessFile(tmpFile, "r"), "r")) {
      Assert.assertEquals(13, obj.length());
    } finally {
      tmpFile.delete();
    }
  }

  @Test
  public void fileReadByteTest() throws IOException {
    File tmpFile = storeInTempFile(new ByteArrayInputStream(BLOB));

    try (RandomAccessObject obj = new RandomAccessObject.RandomAccessFileObject(tmpFile, "r")) {
      for (int x = 0; x < BLOB.length; x++) {
        Assert.assertEquals(x + 1, obj.readByte());
      }

      try {
        obj.readByte();
        Assert.fail("Should've thrown an IOException");
      } catch (IOException expected) {
      }
    } finally {
      tmpFile.delete();
    }
  }

  @Test
  public void byteArrayReadByteTest() throws IOException {
    // Mix positives and negatives to test sign preservation in readByte()
    byte[] bytes = new byte[] {-128, -127, -126, -1, 0, 1, 125, 126, 127};
    try (RandomAccessObject obj = new RandomAccessObject.RandomAccessByteArrayObject(bytes)) {
      for (int x = 0; x < bytes.length; x++) {
        Assert.assertEquals(bytes[x], obj.readByte());
      }

      try {
        obj.readByte();
        Assert.fail("Should've thrown an IOException");
      } catch (BufferUnderflowException expected) {
      }
    }
  }

  @Test
  public void byteArrayReadUnsignedByteTest() throws IOException {
    // Test values above 127 to test unsigned-ness of readUnsignedByte()
    int[] ints = new int[] {255, 254, 253};
    byte[] bytes = new byte[] {(byte) 0xff, (byte) 0xfe, (byte) 0xfd};
    try (RandomAccessObject obj = new RandomAccessObject.RandomAccessByteArrayObject(bytes)) {
      for (int x = 0; x < bytes.length; x++) {
        Assert.assertEquals(ints[x], obj.readUnsignedByte());
      }

      try {
        obj.readUnsignedByte();
        Assert.fail("Should've thrown an IOException");
      } catch (BufferUnderflowException expected) {
      }
    }
  }

  @Test
  public void mmapReadByteTest() throws IOException {
    File tmpFile = storeInTempFile(new ByteArrayInputStream(BLOB));

    try (RandomAccessObject obj =
        new RandomAccessObject.RandomAccessMmapObject(new RandomAccessFile(tmpFile, "r"), "r")) {
      for (int x = 0; x < BLOB.length; x++) {
        Assert.assertEquals(x + 1, obj.readByte());
      }

      try {
        obj.readByte();
        Assert.fail("Should've thrown an BufferUnderflowException");
      } catch (BufferUnderflowException expected) {
      }
    } finally {
      tmpFile.delete();
    }
  }

  @Test
  public void fileWriteByteTest() throws IOException {
    File tmpFile = storeInTempFile(new ByteArrayInputStream(BLOB));

    try (RandomAccessObject obj = new RandomAccessObject.RandomAccessFileObject(tmpFile, "rw")) {
      for (int x = 0; x < BLOB.length; x++) {
        obj.writeByte((byte) (5 - x));
      }

      // Writing a byte past the end of a file should be ok - this just extends the file.
      obj.writeByte((byte) 243);

      // As per RandomAccessFile documentation, the reported length should update after writing off
      // the end of a file.
      Assert.assertEquals(BLOB.length + 1, obj.length());

      obj.seek(0);
      for (int x = 0; x < BLOB.length; x++) {
        Assert.assertEquals(5 - x, obj.readByte());
      }

      // Note that because of signed bytes, if cased to an int, this would actually resolve to -13.
      Assert.assertEquals((byte) 243, obj.readByte());

      try {
        obj.readByte();
        Assert.fail("Should've thrown an IOException");
      } catch (IOException expected) {
      }
    } finally {
      tmpFile.delete();
    }
  }

  @Test
  public void fileWriteByteToEmptyFileTest() throws IOException {
    File tmpFile = File.createTempFile("RandomAccessObjectTest", "temp");

    try (RandomAccessObject obj = new RandomAccessObject.RandomAccessFileObject(tmpFile, "rw")) {
      for (int x = 0; x < BLOB.length; x++) {
        obj.writeByte((byte) (5 - x));
      }

      obj.seek(0);
      for (int x = 0; x < BLOB.length; x++) {
        Assert.assertEquals(5 - x, obj.readByte());
      }

      Assert.assertEquals(BLOB.length, obj.length());
    } finally {
      tmpFile.delete();
    }
  }

  @Test
  public void byteArrayWriteByteTest() throws IOException {
    final int len = 13;
    try (RandomAccessObject obj =
        new RandomAccessObject.RandomAccessByteArrayObject(new byte[len])) {
      for (int x = 0; x < len; x++) {
        obj.writeByte((byte) (5 - x));
      }

      try {
        // Writing a byte past the end of an array is not ok.
        obj.writeByte((byte) 243);
        Assert.fail("Should've thrown a BufferOverflowException");
      } catch (BufferOverflowException expected) {
      }

      obj.seek(0);
      for (int x = 0; x < len; x++) {
        Assert.assertEquals(5 - x, obj.readByte());
      }

      try {
        obj.readByte();
        Assert.fail("Should've thrown a BufferUnderflowException");
      } catch (BufferUnderflowException expected) {
      }
    }
  }

  @Test
  public void mmapWriteByteTest() throws IOException {
    File tmpFile = storeInTempFile(new ByteArrayInputStream(BLOB));

    try (RandomAccessObject obj =
        new RandomAccessObject.RandomAccessMmapObject(new RandomAccessFile(tmpFile, "rw"), "rw")) {
      for (int x = 0; x < BLOB.length; x++) {
        obj.writeByte((byte) (5 - x));
      }

      try {
        // Writing a byte past the end of an mmap is not ok.
        obj.writeByte((byte) 243);
        Assert.fail("Should've thrown an BufferOverflowException");
      } catch (BufferOverflowException expected) {
      }

      Assert.assertEquals(BLOB.length, obj.length());

      obj.seek(0);
      for (int x = 0; x < BLOB.length; x++) {
        Assert.assertEquals(5 - x, obj.readByte());
      }

      try {
        obj.readByte();
        Assert.fail("Should've thrown an BufferUnderflowException");
      } catch (BufferUnderflowException expected) {
      }
    } finally {
      tmpFile.delete();
    }
  }

  @Test
  public void mmapWriteByteToEmptyFileTest() throws IOException {
    File tmpFile = File.createTempFile("RandomAccessObjectTest", "temp");

    try (RandomAccessObject obj =
        new RandomAccessObject.RandomAccessMmapObject(new RandomAccessFile(tmpFile, "rw"), "rw")) {
      for (int x = 0; x < BLOB.length; x++) {
        try {
          // Writing a byte past the end of an mmap is not ok.
          obj.writeByte((byte) (5 - x));
          Assert.fail("Should've thrown an BufferOverflowException");
        } catch (BufferOverflowException expected) {
        }
      }

      try {
        obj.seek(BLOB.length);
        Assert.fail("Should've thrown an IllegalArgumentException");
      } catch (IllegalArgumentException expected) {
      }

      for (int x = 0; x < BLOB.length; x++) {
        try {
          obj.readByte();
          Assert.fail("Should've thrown an BufferUnderflowException");
        } catch (BufferUnderflowException expected) {
        }
      }

      Assert.assertEquals(0, obj.length());
    } finally {
      tmpFile.delete();
    }
  }

  @Test
  public void fileSeekTest() throws IOException {
    File tmpFile = storeInTempFile(new ByteArrayInputStream(BLOB));

    try {
      RandomAccessObject obj = new RandomAccessObject.RandomAccessFileObject(tmpFile, "rw");
      seekTest(obj);

      try {
        obj.seek(-1);
        Assert.fail("Should've thrown an IOException");
      } catch (IOException expected) {
      }

      // This should not throw an exception.
      obj.seek(BLOB.length);
    } finally {
      tmpFile.delete();
    }
  }

  @Test
  public void byteArraySeekTest() throws IOException {
    byte[] data = new byte[BLOB.length];
    System.arraycopy(BLOB, 0, data, 0, BLOB.length);
    RandomAccessObject obj = new RandomAccessObject.RandomAccessByteArrayObject(data);
    seekTest(obj);

    try {
      obj.seek(-1);
      Assert.fail("Should've thrown an IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
    }

    // Should not fail.
    obj.seek(BLOB.length);

    // Only fails once you try to read past the end.
    try {
      obj.readByte();
      Assert.fail("Should've thrown a BufferUnderflowException");
    } catch (BufferUnderflowException expected) {
    }
  }

  @Test
  public void mmapSeekTest() throws IOException {
    File tmpFile = storeInTempFile(new ByteArrayInputStream(BLOB));

    try {
      RandomAccessObject obj =
          new RandomAccessObject.RandomAccessMmapObject(new RandomAccessFile(tmpFile, "rw"), "rw");
      seekTest(obj);

      try {
        obj.seek(-1);
        Assert.fail("Should've thrown an IllegalArgumentException");
      } catch (IllegalArgumentException expected) {
      }

      // Should not fail.
      obj.seek(BLOB.length);

      // Only fails once you try to read past the end.
      try {
        obj.readByte();
        Assert.fail("Should've thrown a BufferUnderflowException");
      } catch (BufferUnderflowException expected) {
      }
    } finally {
      tmpFile.delete();
    }
  }

  @Test
  public void fileReadIntTest() throws IOException {
    File tmpFile = storeInTempFile(new ByteArrayInputStream(BLOB));

    try {
      RandomAccessObject obj = new RandomAccessObject.RandomAccessFileObject(tmpFile, "r");
      readIntTest(obj);
      try {
        obj.readInt();
        Assert.fail("Should've thrown a EOFException");
      } catch (EOFException expected) {
      }
    } finally {
      tmpFile.delete();
    }
  }

  @Test
  public void byteArrayReadIntTest() throws IOException {
    RandomAccessObject obj = new RandomAccessObject.RandomAccessByteArrayObject(BLOB);
    readIntTest(obj);
    try {
      obj.readInt();
      Assert.fail("Should've thrown a BufferUnderflowException");
    } catch (BufferUnderflowException expected) {
    }
  }

  @Test
  public void mmapReadIntTest() throws IOException {
    File tmpFile = storeInTempFile(new ByteArrayInputStream(BLOB));

    try {
      RandomAccessObject obj =
          new RandomAccessObject.RandomAccessMmapObject(new RandomAccessFile(tmpFile, "r"), "r");
      readIntTest(obj);

      try {
        obj.readInt();
        Assert.fail("Should've thrown an BufferUnderflowException");
      } catch (BufferUnderflowException expected) {
      }
    } finally {
      tmpFile.delete();
    }
  }

  @Test
  public void fileWriteIntTest() throws IOException {
    File tmpFile = storeInTempFile(new ByteArrayInputStream(BLOB));

    try (RandomAccessObject obj = new RandomAccessObject.RandomAccessFileObject(tmpFile, "rw")) {
      for (int x = 0; x < BLOB.length / 4; x++) {
        obj.writeInt(500 + x);
      }

      obj.seekToIntAligned(0);
      for (int x = 0; x < BLOB.length / 4; x++) {
        Assert.assertEquals(500 + x, obj.readInt());
      }
    } finally {
      tmpFile.delete();
    }
  }

  @Test
  public void byteArrayWriteIntTest() throws IOException {
    final int len = 13;
    try (RandomAccessObject obj =
        new RandomAccessObject.RandomAccessByteArrayObject(new byte[len])) {
      for (int x = 0; x < len / 4; x++) {
        obj.writeInt(500 + x);
      }

      obj.seek(0);
      for (int x = 0; x < len / 4; x++) {
        Assert.assertEquals(500 + x, obj.readInt());
      }
    }
  }

  @Test
  public void mmapWriteIntTest() throws IOException {
    File tmpFile = storeInTempFile(new ByteArrayInputStream(BLOB));

    try (RandomAccessObject obj =
        new RandomAccessObject.RandomAccessMmapObject(new RandomAccessFile(tmpFile, "rw"), "rw")) {
      for (int x = 0; x < BLOB.length / 4; x++) {
        obj.writeInt(500 + x);
      }

      obj.seekToIntAligned(0);
      for (int x = 0; x < BLOB.length / 4; x++) {
        Assert.assertEquals(500 + x, obj.readInt());
      }
    } finally {
      tmpFile.delete();
    }
  }

  @Test
  public void fileSeekToIntAlignedTest() throws IOException {
    File tmpFile = storeInTempFile(new ByteArrayInputStream(BLOB));

    try {
      RandomAccessObject obj = new RandomAccessObject.RandomAccessFileObject(tmpFile, "rw");
      seekToIntAlignedTest(obj);
    } finally {
      tmpFile.delete();
    }
  }

  @Test
  public void byteArraySeekToIntAlignedTest() throws IOException {
    byte[] data = new byte[BLOB.length];
    System.arraycopy(BLOB, 0, data, 0, BLOB.length);
    RandomAccessObject obj = new RandomAccessObject.RandomAccessByteArrayObject(data);
    seekToIntAlignedTest(obj);
  }

  @Test
  public void mmapSeekToIntAlignedTest() throws IOException {
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
  public void fileCloseTest() throws IOException {
    File tmpFile = storeInTempFile(new ByteArrayInputStream(BLOB));

    try {
      RandomAccessObject obj = new RandomAccessObject.RandomAccessFileObject(tmpFile, "r", true);
      obj.close();
      Assert.assertFalse(tmpFile.exists());
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
      Assert.assertTrue(tmpFile.exists());
    } finally {
      tmpFile.delete();
    }
  }

  @Test
  public void mmapCloseTest() throws IOException {
    File tmpFile = storeInTempFile(new ByteArrayInputStream(BLOB));

    try {
      try (RandomAccessObject obj = new RandomAccessObject.RandomAccessMmapObject(tmpFile, "r")) {}
      Assert.assertFalse(tmpFile.exists());
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
      Assert.assertTrue(tmpFile.exists());
    } finally {
      tmpFile.delete();
    }
  }

  private void seekTest(final RandomAccessObject obj) throws IOException {
    obj.seek(7);
    Assert.assertEquals(8, obj.readByte());
    obj.seek(3);
    Assert.assertEquals(4, obj.readByte());
    obj.seek(9);
    Assert.assertEquals(10, obj.readByte());
    obj.seek(5);
    obj.writeByte((byte) 23);
    obj.seek(5);
    Assert.assertEquals(23, obj.readByte());
    obj.seek(4);
    Assert.assertEquals(5, obj.readByte());

    obj.seek(0);
    for (int x = 0; x < BLOB.length; x++) {
      if (x == 5) {
        Assert.assertEquals(23, obj.readByte());
      } else {
        Assert.assertEquals(x + 1, obj.readByte());
      }
    }
  }

  private void readIntTest(final RandomAccessObject obj) throws IOException {
    Assert.assertEquals(0x01020304, obj.readInt());
    Assert.assertEquals(0x05060708, obj.readInt());
    Assert.assertEquals(0x090A0B0C, obj.readInt());
  }

  private void seekToIntAlignedTest(final RandomAccessObject obj) throws IOException {
    obj.seekToIntAligned(3);
    Assert.assertEquals(3 * 4 + 1, obj.readByte());

    obj.seekToIntAligned(2);
    Assert.assertEquals(2 * 4 + 1, obj.readByte());
    Assert.assertEquals(0x0A0B0C0D, obj.readInt());

    obj.seekToIntAligned(0);
    Assert.assertEquals(1, obj.readByte());

    obj.seekToIntAligned(1);
    Assert.assertEquals(5, obj.readByte());
    Assert.assertEquals(0x06070809, obj.readInt());

    obj.seekToIntAligned(2);
    obj.writeInt(0x26391bd2);

    obj.seekToIntAligned(0);
    Assert.assertEquals(0x01020304, obj.readInt());
    Assert.assertEquals(0x05060708, obj.readInt());
    Assert.assertEquals(0x26391bd2, obj.readInt());
  }

  private File storeInTempFile(InputStream content) throws IOException {
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
}
