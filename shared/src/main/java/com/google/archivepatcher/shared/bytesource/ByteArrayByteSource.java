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
import java.util.Arrays;

/** A {@link ByteSource} backed by a byte array. */
public class ByteArrayByteSource extends ByteSource {

  private final byte[] buffer;

  public ByteArrayByteSource(byte[] buffer) {
    this.buffer = Arrays.copyOf(buffer, buffer.length);
  }

  @Override
  public long length() {
    return buffer.length;
  }

  @Override
  protected InputStream openStream(long offset, long length) throws IOException {
    return new ByteArrayInputStream(buffer, (int) offset, (int) length);
  }

  @Override
  public void close() throws IOException {
    // Nothing needs to be done.
  }

}
