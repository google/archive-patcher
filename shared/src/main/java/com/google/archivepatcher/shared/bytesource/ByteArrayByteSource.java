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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/** A {@link ByteSource} backed by a byte array. */
public class ByteArrayByteSource extends ByteSource {

  private final byte[] buffer;

  /**
   * Constructs a new {@link ByteArrayByteSource}.
   *
   * <p>WARNING: the byte array passed in is not copied and should not be mutated afterwards.
   */
  public ByteArrayByteSource(byte[] buffer) {
    this.buffer = buffer;
  }

  @Override
  public long length() {
    return buffer.length;
  }

  @Override
  public InputStream openBufferedStream() throws IOException {
    return openStream(0, length());
  }

  @Override
  protected InputStream openStream(long offset, long length) throws IOException {
    return new ByteArrayInputStream(buffer, (int) offset, (int) length);
  }

  @Override
  public void close() throws IOException {
    // Nothing needs to be done.
  }

  /**
   * Getter for the underlying byte array for cases where we absolutely needs it, e.g., passing file
   * name to native API.
   */
  public byte[] getByteArray() {
    return buffer;
  }
}
