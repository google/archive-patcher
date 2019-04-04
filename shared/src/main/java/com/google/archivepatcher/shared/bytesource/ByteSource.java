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

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;

/** A readable source of bytes, such as a file or a byte array. */
public abstract class ByteSource implements Closeable {

  /** Returns the length of this {@link ByteSource}. */
  public abstract long length();

  /**
   * Returns a slice of this {@link ByteSource} starting at byte {@code offset} with the given
   * {@code length}.
   */
  public ByteSource slice(long offset, long length) {
    length = Math.min(length, length());
    return new SlicedByteSource(this, offset, length);
  }

  public ByteSource sliceFrom(long offset) {
    long length = Math.max(length() - offset, 0);
    return new SlicedByteSource(this, offset, length);
  }

  /** Returns an {@link InputStream} for reading from this {@link ByteSource}. */
  public synchronized InputStream openStream() throws IOException {
    return openStream(0, length());
  }

  /**
   * Opens an {@link InputStream} starting at byte offset {@code offset} with length {@code length}
   * in this {@link ByteSource}.
   */
  protected abstract InputStream openStream(long offset, long length) throws IOException;
}
