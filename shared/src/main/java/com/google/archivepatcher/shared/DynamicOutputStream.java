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

package com.google.archivepatcher.shared;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;

/**
 * Output stream which changes from being in-memory to on-disk when its size exceeds {@code
 * maxInMemorySizeBytes}.
 */
public class DynamicOutputStream extends OutputStream {
  enum STATE {
    IN_MEMORY,
    ON_DISK
  }

  // Integer.BYTES is not compatible with legacy Android devices.
  private static final int BITS_IN_BYTE = 8;

  // Max size of in-memory buffer. When blob exceeds this size it'll be move to disk.
  private final int maxInMemorySizeBytes;
  private final ByteArrayOutputStream byteArrayOutputStream;
  private CountingOutputStream countingOutputStream;
  private File file;
  private STATE state;

  public DynamicOutputStream(int maxInMemorySizeBytes) {
    this.maxInMemorySizeBytes = maxInMemorySizeBytes;
    this.byteArrayOutputStream = new ByteArrayOutputStream(maxInMemorySizeBytes);
    this.countingOutputStream = new CountingOutputStream(byteArrayOutputStream);
    this.state = STATE.IN_MEMORY;
  }

  @Override
  public void write(int b) throws IOException {
    if (state == STATE.IN_MEMORY
        && countingOutputStream.getNumBytesWritten() + (Integer.SIZE / BITS_IN_BYTE)
            > maxInMemorySizeBytes) {
      changeStorageToOnDisk();
    }
    countingOutputStream.write(b);
  }

  @Override
  public void close() {
    if (state == STATE.ON_DISK) {
      file.delete();
    }
  }

  /** Returns the size of the content written to this stream. */
  public long length() {
    return countingOutputStream.getNumBytesWritten();
  }

  /** Returns the current contents of this output stream, as a byte array */
  public byte[] toByteArray() throws IOException {
    if (state == STATE.IN_MEMORY) {
      return byteArrayOutputStream.toByteArray();
    }
    return Files.readAllBytes(file.toPath());
  }

  private void changeStorageToOnDisk() throws IOException {
    file = File.createTempFile("archive_patcher", "tmp");
    file.deleteOnExit();
    countingOutputStream = new CountingOutputStream(new FileOutputStream(file));
    countingOutputStream.write(byteArrayOutputStream.toByteArray());
    byteArrayOutputStream.flush();
    state = STATE.ON_DISK;
  }
}
