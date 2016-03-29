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

import com.google.archivepatcher.generator.bsdiff.RandomAccessObject.RandomAccessByteArrayObject;
import com.google.archivepatcher.generator.bsdiff.RandomAccessObject.RandomAccessFileObject;
import com.google.archivepatcher.generator.bsdiff.RandomAccessObject.RandomAccessMmapObject;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * A factory for creating instances of {@link RandomAccessObject}. BsDiff needs to store some
 * ancillary data of size proportional to the binary to be patched. This allows abstraction of the
 * allocation so that BsDiff can run either entirely in-memory (faster) or with file-backed swap
 * (handles bigger inputs without consuming inordinate amounts of memory).
 */
public interface RandomAccessObjectFactory {
  public RandomAccessObject create(int size) throws IOException;

  /**
   * A factory that produces {@link RandomAccessFileObject} instances backed by temp files.
   */
  // TODO(hartmanng): rethink the handling of these temp files. It's confusing and shouldn't
  // really be the responsibility of RandomAccessObject.
  public static final class RandomAccessFileObjectFactory implements RandomAccessObjectFactory {
    private static final String FILE_NAME_PREFIX = "wavsprafof";
    private final String mMode;

    /**
     * Factory for a RandomAccessFileObject.
     * @param mode the file mode string ("r", "w", "rw", etc - see documentation for
     * {@link RandomAccessFile})
     */
    public RandomAccessFileObjectFactory(String mode) {
      mMode = mode;
    }

    /**
     * Creates a temp file, and returns a {@link RandomAccessFile} wrapped in a
     * {@link RandomAccessFileObject} representing the new temp file. The temp file does not need to
     * explicitly be managed (deleted) by the caller, as long as the caller ensures
     * {@link RandomAccessObject#close()} is called when the object is no longer needed.
     */
    // TODO(hartmanng): rethink the handling of these temp files. It's confusing and shouldn't
    // really be the responsibility of RandomAccessObject.
    @Override
    public RandomAccessObject create(int size) throws IOException {
      return new RandomAccessObject.RandomAccessFileObject(
          File.createTempFile(FILE_NAME_PREFIX, "temp"), mMode, true);
    }
  }

  /**
   * A factory that produces {@link RandomAccessByteArrayObject} instances backed by memory.
   */
  public static final class RandomAccessByteArrayObjectFactory
      implements RandomAccessObjectFactory {
    @Override
    public RandomAccessObject create(int size) {
      return new RandomAccessObject.RandomAccessByteArrayObject(size);
    }
  }

  /**
   * A factory that produces {@link RandomAccessMmapObject} instances backed by temp files..
   */
  // TODO(hartmanng): rethink the handling of these temp files. It's confusing and shouldn't
  // really be the responsibility of RandomAccessObject.
  public static final class RandomAccessMmapObjectFactory implements RandomAccessObjectFactory {
    private static final String FILE_NAME_PREFIX = "wavsprafof";
    private String mMode;

    /**
     * Factory for a RandomAccessMmapObject.
     * @param mode the file mode string ("r", "w", "rw", etc - see documentation for
     * {@link RandomAccessFile})
     */
    public RandomAccessMmapObjectFactory(String mode) {
      mMode = mode;
    }

    /**
     * Creates a temp file, and returns a {@link RandomAccessFile} wrapped in a
     * {@link RandomAccessMmapObject} representing the new temp file. The temp file does not need to
     * explicitly be managed (deleted) by the caller, as long as the caller ensures
     * {@link RandomAccessObject#close()} is called when the object is no longer needed.
     */
    // TODO(hartmanng): rethink the handling of these temp files. It's confusing and shouldn't
    // really be the responsibility of RandomAccessObject.
    @Override
    public RandomAccessObject create(int size) throws IOException {
      return new RandomAccessObject.RandomAccessMmapObject(FILE_NAME_PREFIX, mMode, size);
    }
  }
}
