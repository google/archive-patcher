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

package com.google.archivepatcher.shared.bytesource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel.MapMode;

/**
 * A {@link ByteSource} backed by a memory mapped file.
 *
 * <p>WARNING: this class was not stress tested and should be used with caution, especially on
 * Android devices. For more context, see the doc for {@link #close()}.
 */
public class MmapByteSource extends FileByteSource {
  private MappedByteBuffer byteBuffer;

  MmapByteSource(File file) throws IOException {
    super(file);
    if (length() > Integer.MAX_VALUE) {
      throw new IllegalArgumentException(
          "MappedByteSource only supports file sizes up to " + "Integer.MAX_VALUE.");
    }

    try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
      this.byteBuffer = raf.getChannel().map(MapMode.READ_ONLY, 0, length());
    }
  }

  public static ByteSource create(File file) throws IOException {
    if (file.length() <= Integer.MAX_VALUE) {
      return new MmapByteSource(file);
    } else {
      return new RandomAccessFileByteSource(file);
    }
  }

  @Override
  public InputStream openBufferedStream() throws IOException {
    return openStream(0, length());
  }

  /**
   * Note that this method is not thread safe since all streams will be sharing the same ByteBuffer.
   */
  @Override
  protected InputStream openStream(long offset, long length) throws IOException {
    if (offset + length > length()) {
      throw new IllegalArgumentException(
          "Specified offset and length would read out of the bounds of the mapped byte buffer.");
    }
    return new ByteBufferInputStream(byteBuffer, (int) offset, (int) length);
  }

  /**
   * Closes the {@link ByteSource} and release any resource.
   */
  @Override
  public void close() throws IOException {
    if (MappedByteBufferUtils.canFreeMappedBuffers()) {
      try {
        MappedByteBufferUtils.freeBuffer(byteBuffer);
        return;
      } catch (ReflectiveOperationException e) {
        // fall through to the default way for closing buffers.
      }
    }

    // There is a long-standing bug with memory mapped objects in Java that requires the JVM to
    // finalize the MappedByteBuffer reference before the unmap operation is performed. This leaks
    // file handles and fills the virtual address space. Worse, on some systems (Windows for one)
    // the
    // active mmap prevents the temp file from being deleted - even if File.deleteOnExit() is used.
    // The only safe way to ensure that file handles and actual files are not leaked by this class
    // is
    // to force an explicit full gc after explicitly nulling the MappedByteBuffer reference. This
    // has
    // to be done before attempting file deletion.
    //
    // See https://github.com/google/archive-patcher/issues/5 for more information. Also
    // http://bugs.java.com/view_bug.do?bug_id=6417205.
    byteBuffer = null;
    System.gc();
    System.runFinalization();
  }

  private static class ByteBufferInputStream extends InputStream {
    private final ByteBuffer buffer;
    private final int readLimit;
    // Position of next read. We cannot rely on internal state of the byte buffer since it will be
    // shared across multiple streams
    private int nextReadPos;

    public ByteBufferInputStream(ByteBuffer buffer, int offset, int length) {
      this.buffer = buffer;
      this.nextReadPos = offset;
      this.readLimit = offset + length;
    }

    @Override
    public int available() throws IOException {
      return readLimit - nextReadPos;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
      if (endOfStream()) {
        return -1;
      }
      buffer.position(nextReadPos);

      // Default behaviour for ByteBuffer when not enough data can be read is to through an
      // Exception. Expected behaviour of an InputStream is to read as much as possible.
      int remaining = readLimit - nextReadPos;
      if (len > remaining) {
        len = remaining;
      }

      buffer.get(b, off, len);
      nextReadPos += len;
      return len;
    }

    @Override
    public int read() throws IOException {
      if (endOfStream()) {
        return -1;
      }
      buffer.position(nextReadPos);

      // InputStream.read() expects an unsigned byte. ByteBuffer.get() returns a signed one. Hence
      // we need to convert it to unsigned.
      ++nextReadPos;
      return buffer.get() & 0xff;
    }

    @Override
    public long skip(long n) throws IOException {
      if (n <= 0) {
        return 0;
      }

      int remaining = readLimit - nextReadPos;
      if (n > remaining) {
        nextReadPos = readLimit;
        return remaining;
      } else {
        nextReadPos += (int) n;
        return n;
      }
    }

    private boolean endOfStream() {
      return nextReadPos >= readLimit;
    }
  }
}
