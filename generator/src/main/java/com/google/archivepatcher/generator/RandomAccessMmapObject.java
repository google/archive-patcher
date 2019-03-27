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

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * A {@link ByteBuffer}-based implementation of {@link
 * com.google.archivepatcher.generator.RandomAccessObject} that uses files on disk, but is
 * significantly faster than the RandomAccessFile implementation.
 */
public final class RandomAccessMmapObject extends RandomAccessByteArrayObject {
  private final boolean shouldDeleteFileOnRelease;
  private final File file;
  private final FileChannel fileChannel;

  public RandomAccessMmapObject(final RandomAccessFile randomAccessFile, String mode)
      throws IOException {
    if (randomAccessFile.length() > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("Only files up to 2GiB in size are supported.");
    }

    FileChannel.MapMode mapMode;
    if (mode.equals("r")) {
      mapMode = FileChannel.MapMode.READ_ONLY;
    } else {
      mapMode = FileChannel.MapMode.READ_WRITE;
    }

    fileChannel = randomAccessFile.getChannel();
    byteBuffer = fileChannel.map(mapMode, 0, randomAccessFile.length());
    byteBuffer.position(0);
    shouldDeleteFileOnRelease = false;
    file = null;
  }

  /**
   * This constructor creates a temporary file. This file is deleted on close(), so be sure to call
   * it when you're done, otherwise it'll leave stray files.
   *
   * @param tempFileName the path to the file backing this object
   * @param mode the mode to use, e.g. "r" or "w" for read or write
   * @param length the size of the file to be read or written
   * @throws IOException if unable to open the file for the specified mode
   * @throws IllegalArgumentException if the size of the file is too great
   */
  // TODO: rethink the handling of these temp files. It's confusing and shouldn't
  // really be the responsibility of RandomAccessObject.
  @SuppressWarnings("resource") // RandomAccessFile deliberately left open
  public RandomAccessMmapObject(final String tempFileName, final String mode, long length)
      throws IOException {
    if (length > Integer.MAX_VALUE) {
      throw new IllegalArgumentException(
          "RandomAccessMmapObject only supports file sizes up to " + "Integer.MAX_VALUE.");
    }

    file = File.createTempFile(tempFileName, "temp");
    file.deleteOnExit();
    shouldDeleteFileOnRelease = true;

    FileChannel.MapMode mapMode;
    if (mode.equals("r")) {
      mapMode = FileChannel.MapMode.READ_ONLY;
    } else {
      mapMode = FileChannel.MapMode.READ_WRITE;
    }

    RandomAccessFile file = null;
    try {
      file = new RandomAccessFile(this.file, mode);
      fileChannel = file.getChannel();
      byteBuffer = fileChannel.map(mapMode, 0, (int) length);
      byteBuffer.position(0);
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
   * deleted on close() OR IF THE CONSTRUCTOR FAILS. The main purpose of this constructor is to test
   * close() on the passed-in file.
   *
   * @param tempFile the file backing this object
   * @param mode the mode to use, e.g. "r" or "w" for read or write
   * @throws IOException if unable to open the file for the specified mode
   * @throws IllegalArgumentException if the size of the file is too great
   */
  // TODO: rethink the handling of these temp files. It's confusing and shouldn't
  // really be the responsibility of RandomAccessObject.
  @SuppressWarnings("resource") // RandomAccessFile deliberately left open
  public RandomAccessMmapObject(final File tempFile, final String mode) throws IOException {
    if (tempFile.length() > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("Only files up to 2GiB in size are supported.");
    }

    file = tempFile;
    file.deleteOnExit();
    shouldDeleteFileOnRelease = true;

    FileChannel.MapMode mapMode;
    if (mode.equals("r")) {
      mapMode = FileChannel.MapMode.READ_ONLY;
    } else {
      mapMode = FileChannel.MapMode.READ_WRITE;
    }

    RandomAccessFile file = null;
    try {
      file = new RandomAccessFile(this.file, mode);
      fileChannel = file.getChannel();
      byteBuffer = fileChannel.map(mapMode, 0, tempFile.length());
      byteBuffer.position(0);
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
    if (fileChannel != null) {
      fileChannel.close();
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
    byteBuffer = null;
    System.gc();

    if (shouldDeleteFileOnRelease && file != null) {
      file.delete();
    }
  }
}
