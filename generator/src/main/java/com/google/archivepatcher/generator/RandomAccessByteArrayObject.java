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

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * An array-based implementation of {@link com.google.archivepatcher.generator.RandomAccessObject}
 * for entirely in-memory operations.
 */
public class RandomAccessByteArrayObject implements RandomAccessObject {
  protected ByteBuffer byteBuffer;

  /**
   * The passed-in byte array will be treated as big-endian when dealing with ints.
   *
   * @param byteArray the byte array to wrap
   */
  public RandomAccessByteArrayObject(final byte[] byteArray) {
    byteBuffer = ByteBuffer.wrap(byteArray);
  }

  /**
   * Allocate a new ByteBuffer of given length. This will be treated as big-endian.
   *
   * @param length the length of the buffer to allocate
   */
  public RandomAccessByteArrayObject(final int length) {
    byteBuffer = ByteBuffer.allocate(length);
  }

  protected RandomAccessByteArrayObject() {
    // No-op, this is just used by the extending class RandomAccessMmapObject.
  }

  @Override
  public long length() {
    return byteBuffer.capacity();
  }

  @Override
  public byte readByte() {
    return byteBuffer.get();
  }

  /** Reads an integer from the underlying data array in big-endian order. */
  @Override
  public int readInt() {
    return byteBuffer.getInt();
  }

  @Override
  public void writeInt(int i) {
    byteBuffer.putInt(i);
  }

  /**
   * RandomAccessByteArrayObject.seek() only supports addresses up to 2^31-1, due to the fact that
   * it needs to support byte[] backend (and in Java, arrays are indexable only by int). This means
   * that it can only seek up to a 2GiB byte[].
   */
  @Override
  public void seek(long pos) {
    if (pos > Integer.MAX_VALUE) {
      throw new IllegalArgumentException(
          "RandomAccessByteArrayObject can only handle seek() "
              + "addresses up to Integer.MAX_VALUE.");
    }

    byteBuffer.position((int) pos);
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
    return byteBuffer.getChar();
  }

  @Override
  public double readDouble() {
    return byteBuffer.getDouble();
  }

  @Override
  public float readFloat() {
    return byteBuffer.getFloat();
  }

  @Override
  public void readFully(byte[] b) {
    byteBuffer.get(b);
  }

  @Override
  public void readFully(byte[] b, int off, int len) {
    byteBuffer.get(b, off, len);
  }

  @Override
  public String readLine() {
    throw new UnsupportedOperationException();
  }

  @Override
  public long readLong() {
    return byteBuffer.getLong();
  }

  @Override
  public short readShort() {
    return byteBuffer.getShort();
  }

  @Override
  public int readUnsignedByte() {
    return byteBuffer.get() & 0xff;
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
    byteBuffer.position(byteBuffer.position() + n);
    return n;
  }

  @Override
  public void write(byte[] b) {
    byteBuffer.put(b);
  }

  @Override
  public void write(byte[] b, int off, int len) {
    byteBuffer.put(b, off, len);
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

  public void writeByte(byte b) {
    byteBuffer.put(b);
  }

  @Override
  public void writeBytes(String s) {
    for (int x = 0; x < s.length(); x++) {
      writeByte((byte) s.charAt(x));
    }
  }

  @Override
  public void writeChar(int v) {
    byteBuffer.putChar((char) v);
  }

  @Override
  public void writeChars(String s) {
    for (int x = 0; x < s.length(); x++) {
      writeChar(s.charAt(x));
    }
  }

  @Override
  public void writeDouble(double v) {
    byteBuffer.putDouble(v);
  }

  @Override
  public void writeFloat(float v) {
    byteBuffer.putFloat(v);
  }

  @Override
  public void writeLong(long v) {
    byteBuffer.putLong(v);
  }

  @Override
  public void writeShort(int v) {
    byteBuffer.putShort((short) v);
  }

  @Override
  public void writeUTF(String s) {
    throw new UnsupportedOperationException();
  }
}
