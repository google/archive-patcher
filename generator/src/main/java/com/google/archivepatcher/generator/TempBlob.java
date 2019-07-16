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

package com.google.archivepatcher.generator;

import com.google.archivepatcher.shared.bytesource.ByteSource;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * A closeable container for a temp file that deletes itself on {@link #close()}. This is convenient
 * for try-with-resources constructs that need to use temp files in scope.
 */
public class TempBlob implements Closeable {
  /** The file that is wrapped by this blob. */
  File file;

  /** If the OutputStream to this blob is still open. */
  private boolean isWriting = false;

  /**
   * Create a new temp file and wrap it in an instance of this class. The file is automatically
   * scheduled for deletion on JVM termination, so it is a serious error to rely on this file path
   * being a durable artifact.
   *
   * @throws IOException if unable to create the file.
   */
  public TempBlob() throws IOException {
    createNewFile();
  }

  /** Obtain the content of this blob as {@link ByteSource}. */
  public ByteSource asByteSource() throws IOException {
    if (isWriting) {
      throw new IOException("A previous stream is still open for writing.");
    }
    return ByteSource.fromFile(file);
  }

  /** Returns an {@link OutputStream} to write to this blob. */
  public OutputStream openOutputStream() throws IOException {
    if (isWriting) {
      throw new IOException("A previous stream is still open for writing.");
    }
    isWriting = true;
    FileOutputStream fileOutputStream = new FileOutputStream(file);
    return new OutputStream() {
      @Override
      public void write(int b) throws IOException {
        fileOutputStream.write(b);
      }

      @Override
      public void write(byte[] b, int off, int len) throws IOException {
        fileOutputStream.write(b, off, len);
      }

      @Override
      public void close() throws IOException {
        isWriting = false;
        fileOutputStream.close();
      }

      @Override
      public void flush() throws IOException {
        fileOutputStream.flush();
      }
    };
  }

  /** Returns the size of the content written to this blob. */
  public long length() {
    return file.length();
  }

  /** Clears the content of this blob. */
  public void clear() throws IOException {
    if (isWriting) {
      throw new IOException("A previous stream is still open for writing.");
    }
    file.delete();
    createNewFile();
  }

  private void createNewFile() throws IOException {
    file = File.createTempFile("archive_patcher", "tmp");
    file.deleteOnExit();
  }

  @Override
  public void close() {
    file.delete();
  }
}
