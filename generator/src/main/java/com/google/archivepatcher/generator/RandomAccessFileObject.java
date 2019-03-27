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

/**
 * A {@link RandomAccessFile}-based implementation of {@link RandomAccessObject} which just
 * delegates all operations to the equivalents in {@link RandomAccessFile}. Slower than the {@link
 * RandomAccessMmapObject} implementation.
 */
public final class RandomAccessFileObject extends RandomAccessFile implements RandomAccessObject {
  private final boolean shouldDeleteFileOnClose;
  private final File file;

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
   * This constructor takes in a temporary file. If deleteFileOnClose is true, the constructor takes
   * ownership of that file, and this file is deleted on close().
   *
   * @param tempFile the file backing this object
   * @param mode the mode to use, e.g. "r" or "w" for read or write
   * @param deleteFileOnClose if true the constructor takes ownership of that file, and this file is
   *     deleted on close().
   * @throws IOException if unable to open the file for the specified mode
   * @throws IllegalArgumentException if the size of the file is too great
   */
  // TODO: rethink the handling of these temp files. It's confusing and shouldn't
  // really be the responsibility of RandomAccessObject.
  public RandomAccessFileObject(final File tempFile, final String mode, boolean deleteFileOnClose)
      throws IOException {
    super(tempFile, mode);
    shouldDeleteFileOnClose = deleteFileOnClose;
    file = tempFile;
    if (shouldDeleteFileOnClose) {
      file.deleteOnExit();
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
  // TODO: rethink the handling of these temp files. It's confusing and shouldn't
  // really be the responsibility of RandomAccessObject.
  @Override
  public void close() throws IOException {
    super.close();
    if (shouldDeleteFileOnClose) {
      file.delete();
    }
  }
}
