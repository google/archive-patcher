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

import java.io.Closeable;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

// TODO(andrewhayden): clean up the implementations, we only really need two and they can be in
// separate files.

/**
 * Interface which offers random access interface. This class exists to allow BsDiff to use either
 * a RandomAccessFile for disk-based io, or a byte[] for fully in-memory operation.
 */
public interface RandomAccessObject extends DataInput, DataOutput, Closeable {

  /**
   * Returns the length of the file or byte array associated with the RandomAccessObject.
   *
   * @return the length of the file or byte array associated with the RandomAccessObject
   * @throws IOException if unable to determine the length of the file, when backed by a file
   */
  public long length() throws IOException;

  /**
   * Seeks to a specified position, in bytes, into the file or byte array.
   *
   * @param pos the position to seek to
   * @throws IOException if seeking fails
   */
  public void seek(long pos) throws IOException;

  /**
   * Seek to a specified integer-aligned position in the associated file or byte array. For example,
   * seekToIntAligned(5) will seek to the beginning of the 5th integer, or in other words the 20th
   * byte. In general, seekToIntAligned(n) will seek to byte 4n.
   *
   * @param pos the position to seek to
   * @throws IOException if seeking fails
   */
  public void seekToIntAligned(long pos) throws IOException;

  /**
   * A {@link RandomAccessFile}-based implementation of {@link RandomAccessObject} which just
   * delegates all operations to the equivalents in {@link RandomAccessFile}. Slower than the
   * {@link RandomAccessMmapObject} implementation.
   */
  public static final class RandomAccessFileObject extends RandomAccessFile
      implements RandomAccessObject {
    private final boolean mShouldDeleteFileOnClose;
    private final File mFile;

    /**
     * This constructor takes in a temporary file. This constructor does not take ownership of the
     * file, and the file will not be deleted on {@link #close()}.
     *
     * @param tempFile the file backing this object
     * @param mode the mode to use, e.g. "r" or "w" for read or write
     * @throws IOException if unable to open the file for the specified mode
     */
    public RandomAccessFileObject(final File tempFile, final String mode) throws IOException {
      this(tempFile, mode, false);
    }

    /**
     * This constructor takes in a temporary file. If deleteFileOnClose is true, the constructor
     * takes ownership of that file, and this file is deleted on close().
     *
     * @param tempFile the file backing this object
     * @param mode the mode to use, e.g. "r" or "w" for read or write
     * @param deleteFileOnClose if true the constructor takes ownership of that file, and this file
     *     is deleted on close().
     * @throws IOException if unable to open the file for the specified mode
     * @throws IllegalArgumentException if the size of the file is too great
     */
    // TODO(hartmanng): rethink the handling of these temp files. It's confusing and shouldn't
    // really be the responsibility of RandomAccessObject.
    public RandomAccessFileObject(final File tempFile, final String mode, boolean deleteFileOnClose)
        throws IOException {
      super(tempFile, mode);
      mShouldDeleteFileOnClose = deleteFileOnClose;
      mFile = tempFile;
      if (mShouldDeleteFileOnClose) {
        mFile.deleteOnExit();
      }
    }

    @Override
    public void seekToIntAligned(long pos) throws IOException {
      seek(pos * 4);
    }

    /**
     * Close the associated file. Also delete the associated temp file if specified in the
     * constructor. This should be called on every RandomAccessObject when it is no longer needed.
     */
    // TODO(hartmanng): rethink the handling of these temp files. It's confusing and shouldn't
    // really be the responsibility of RandomAccessObject.
    @Override
    public void close() throws IOException {
      super.close();
      if (mShouldDeleteFileOnClose) {
        mFile.delete();
      }
    }
  }

  /**
   * An array-based implementation of {@link RandomAccessObject} for entirely in-memory operations.
   */
  public static class RandomAccessByteArrayObject implements RandomAccessObject {
    protected ByteBuffer mByteBuffer;

    /**
     * The passed-in byte array will be treated as big-endian when dealing with ints.
     *
     * @param byteArray the byte array to wrap
     */
    public RandomAccessByteArrayObject(final byte[] byteArray) {
      mByteBuffer = ByteBuffer.wrap(byteArray);
    }

    /**
     * Allocate a new ByteBuffer of given length. This will be treated as big-endian.
     *
     * @param length the length of the buffer to allocate
     */
    public RandomAccessByteArrayObject(final int length) {
      mByteBuffer = ByteBuffer.allocate(length);
    }

    protected RandomAccessByteArrayObject() {
      // No-op, this is just used by the extending class RandomAccessMmapObject.
    }

    @Override
    public long length() {
      return mByteBuffer.capacity();
    }

    @Override
    public byte readByte() {
      return mByteBuffer.get();
    }

    /**
     * Reads an integer from the underlying data array in big-endian order.
     */
    @Override
    public int readInt() {
      return mByteBuffer.getInt();
    }

    public void writeByte(byte b) {
      mByteBuffer.put(b);
    }

    @Override
    public void writeInt(int i) {
      mByteBuffer.putInt(i);
    }

    /**
     * RandomAccessByteArrayObject.seek() only supports addresses up to 2^31-1, due to the fact
     * that it needs to support byte[] backend (and in Java, arrays are indexable only by int).
     * This means that it can only seek up to a 2GiB byte[].
     */
    @Override
    public void seek(long pos) {
      if (pos > Integer.MAX_VALUE) {
        throw new IllegalArgumentException(
            "RandomAccessByteArrayObject can only handle seek() "
                + "addresses up to Integer.MAX_VALUE.");
      }

      mByteBuffer.position((int) pos);
    }

    @Override
    public void seekToIntAligned(long pos) {
      seek(pos * 4);
    }

    @Override
    public void close() throws IOException {
      // Nothing necessary.
    }

    @Override
    public boolean readBoolean() {
      return readByte() != 0;
    }

    @Override
    public char readChar() {
      return mByteBuffer.getChar();
    }

    @Override
    public double readDouble() {
      return mByteBuffer.getDouble();
    }

    @Override
    public float readFloat() {
      return mByteBuffer.getFloat();
    }

    @Override
    public void readFully(byte[] b) {
      mByteBuffer.get(b);
    }

    @Override
    public void readFully(byte[] b, int off, int len) {
      mByteBuffer.get(b, off, len);
    }

    @Override
    public String readLine() {
      throw new UnsupportedOperationException();
    }

    @Override
    public long readLong() {
      return mByteBuffer.getLong();
    }

    @Override
    public short readShort() {
      return mByteBuffer.getShort();
    }

    @Override
    public int readUnsignedByte() {
      return mByteBuffer.get() & 0xff;
    }

    @Override
    public int readUnsignedShort() {
      throw new UnsupportedOperationException();
    }

    @Override
    public String readUTF() {
      throw new UnsupportedOperationException();
    }

    @Override
    public int skipBytes(int n) {
      mByteBuffer.position(mByteBuffer.position() + n);
      return n;
    }

    @Override
    public void write(byte[] b) {
      mByteBuffer.put(b);
    }

    @Override
    public void write(byte[] b, int off, int len) {
      mByteBuffer.put(b, off, len);
    }

    @Override
    public void write(int b) {
      writeByte((byte) b);
    }

    @Override
    public void writeBoolean(boolean v) {
      writeByte(v ? (byte) 1 : (byte) 0);
    }

    @Override
    public void writeByte(int v) {
      writeByte((byte) v);
    }

    @Override
    public void writeBytes(String s) {
      for (int x = 0; x < s.length(); x++) {
        writeByte((byte) s.charAt(x));
      }
    }

    @Override
    public void writeChar(int v) {
      mByteBuffer.putChar((char) v);
    }

    @Override
    public void writeChars(String s) {
      for (int x = 0; x < s.length(); x++) {
        writeChar(s.charAt(x));
      }
    }

    @Override
    public void writeDouble(double v) {
      mByteBuffer.putDouble(v);
    }

    @Override
    public void writeFloat(float v) {
      mByteBuffer.putFloat(v);
    }

    @Override
    public void writeLong(long v) {
      mByteBuffer.putLong(v);
    }

    @Override
    public void writeShort(int v) {
      mByteBuffer.putShort((short) v);
    }

    @Override
    public void writeUTF(String s) {
      throw new UnsupportedOperationException();
    }
  }

  /**
   * A {@link ByteBuffer}-based implementation of {@link RandomAccessObject} that uses files on
   * disk, but is significantly faster than the RandomAccessFile implementation.
   */
  public static final class RandomAccessMmapObject extends RandomAccessByteArrayObject {
    private final boolean mShouldDeleteFileOnRelease;
    private final File mFile;
    private final FileChannel mFileChannel;

    public RandomAccessMmapObject(final RandomAccessFile randomAccessFile, String mode)
        throws IOException, IllegalArgumentException {
      if (randomAccessFile.length() > Integer.MAX_VALUE) {
        throw new IllegalArgumentException("Only files up to 2GiB in size are supported.");
      }

      FileChannel.MapMode mapMode;
      if (mode.equals("r")) {
        mapMode = FileChannel.MapMode.READ_ONLY;
      } else {
        mapMode = FileChannel.MapMode.READ_WRITE;
      }

      mFileChannel = randomAccessFile.getChannel();
      mByteBuffer = mFileChannel.map(mapMode, 0, randomAccessFile.length());
      mByteBuffer.position(0);
      mShouldDeleteFileOnRelease = false;
      mFile = null;
    }

    /**
     * This constructor creates a temporary file. This file is deleted on close(), so be sure to
     * call it when you're done, otherwise it'll leave stray files.
     *
     * @param tempFileName the path to the file backing this object
     * @param mode the mode to use, e.g. "r" or "w" for read or write
     * @param length the size of the file to be read or written
     * @throws IOException if unable to open the file for the specified mode
     * @throws IllegalArgumentException if the size of the file is too great
     */
    // TODO(hartmanng): rethink the handling of these temp files. It's confusing and shouldn't
    // really be the responsibility of RandomAccessObject.
    @SuppressWarnings("resource") // RandomAccessFile deliberately left open
    public RandomAccessMmapObject(final String tempFileName, final String mode, long length)
        throws IOException, IllegalArgumentException {
      if (length > Integer.MAX_VALUE) {
        throw new IllegalArgumentException(
            "RandomAccessMmapObject only supports file sizes up to " + "Integer.MAX_VALUE.");
      }

      mFile = File.createTempFile(tempFileName, "temp");
      mFile.deleteOnExit();
      mShouldDeleteFileOnRelease = true;

      FileChannel.MapMode mapMode;
      if (mode.equals("r")) {
        mapMode = FileChannel.MapMode.READ_ONLY;
      } else {
        mapMode = FileChannel.MapMode.READ_WRITE;
      }

      RandomAccessFile file = null;
      try {
        file = new RandomAccessFile(mFile, mode);
        mFileChannel = file.getChannel();
        mByteBuffer = mFileChannel.map(mapMode, 0, (int) length);
        mByteBuffer.position(0);
      } catch (IOException e) {
        if (file != null) {
          try {
            file.close();
          } catch (Exception ignored) {
            // Nothing more can be done
          }
        }
        close();
        throw new IOException("Unable to open file", e);
      }
    }

    /**
     * This constructor takes in a temporary file, and takes ownership of that file. This file is
     * deleted on close() OR IF THE CONSTRUCTOR FAILS. The main purpose of this constructor is to
     * test close() on the passed-in file.
     *
     * @param tempFile the the file backing this object
     * @param mode the mode to use, e.g. "r" or "w" for read or write
     * @throws IOException if unable to open the file for the specified mode
     * @throws IllegalArgumentException if the size of the file is too great
     */
    // TODO(hartmanng): rethink the handling of these temp files. It's confusing and shouldn't
    // really be the responsibility of RandomAccessObject.
    @SuppressWarnings("resource") // RandomAccessFile deliberately left open
    public RandomAccessMmapObject(final File tempFile, final String mode)
        throws IOException, IllegalArgumentException {
      if (tempFile.length() > Integer.MAX_VALUE) {
        throw new IllegalArgumentException("Only files up to 2GiB in size are supported.");
      }

      mFile = tempFile;
      mFile.deleteOnExit();
      mShouldDeleteFileOnRelease = true;

      FileChannel.MapMode mapMode;
      if (mode.equals("r")) {
        mapMode = FileChannel.MapMode.READ_ONLY;
      } else {
        mapMode = FileChannel.MapMode.READ_WRITE;
      }

      RandomAccessFile file = null;
      try {
        file = new RandomAccessFile(mFile, mode);
        mFileChannel = file.getChannel();
        mByteBuffer = mFileChannel.map(mapMode, 0, tempFile.length());
        mByteBuffer.position(0);
      } catch (IOException e) {
        if (file != null) {
          try {
            file.close();
          } catch (Exception ignored) {
            // Nothing more can be done
          }
        }
        close();
        throw new IOException("Unable to open file", e);
      }
    }

    @Override
    public void close() throws IOException {
      if (mFileChannel != null) {
        mFileChannel.close();
      }

      // There is a long-standing bug with memory mapped objects in Java that requires the JVM to
      // finalize the MappedByteBuffer reference before the unmap operation is performed. This leaks
      // file handles and fills the virtual address space. Worse, on some systems (Windows for one)
      // the active mmap prevents the temp file from being deleted - even if File.deleteOnExit() is
      // used. The only safe way to ensure that file handles and actual files are not leaked by this
      // class is to force an explicit full gc after explicitly nulling the MappedByteBuffer
      // reference. This has to be done before attempting file deletion.
      //
      // See https://github.com/andrewhayden/archive-patcher/issues/5 for more information.
      mByteBuffer = null;
      System.gc();

      if (mShouldDeleteFileOnRelease && mFile != null) {
        mFile.delete();
      }

    }
  }
}
