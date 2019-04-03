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

import com.google.archivepatcher.shared.RandomAccessFileInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * A {@link ByteSource} backed by a {@link RandomAccessFileInputStream}. This implementation is
 * created mainly for backwards compatibility.
 */
public class RandomAccessFileByteSource extends ByteSource {

  private final File file;
  private final RandomAccessFileInputStream rafis;
  private int openStreams = 0;

  public RandomAccessFileByteSource(File file) throws IOException {
    this.file = file;
    this.rafis = new RandomAccessFileInputStream(file);
  }

  @Override
  public long length() {
    return rafis.length();
  }

  @Override
  public boolean supportsMultipleStreams() {
    return false;
  }

  @Override
  protected InputStream openStream(long offset, long length) throws IOException {
    if (openStreams > 0) {
      throw new IllegalStateException("Existing open stream found. Cannot open another stream.");
    }

    rafis.setRange(offset, length);
    ++openStreams;
    return new ShadowInputStream(rafis, /* closeCallback= */ () -> --openStreams);
  }

  @Override
  public ByteSource copy() throws IOException {
    return new RandomAccessFileByteSource(file);
  }

  @Override
  public void close() throws IOException {
    rafis.close();
  }

  @Override
  protected void finalize() throws Throwable {
    close();
  }
}
