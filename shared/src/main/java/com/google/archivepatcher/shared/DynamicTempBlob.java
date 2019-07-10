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

import com.google.archivepatcher.shared.bytesource.ByteSource;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;

/**
 * A blob to which bytes can be written and read back. It changes storage location from in-memory to
 * on-disk for large blobs.
 */
public class DynamicTempBlob implements Closeable {

  private DynamicOutputStream dynamicOutputStream;

  public DynamicTempBlob() {
    dynamicOutputStream = new DynamicOutputStream(/* maxInMemorySizeBytes = */ 50 * 1024 * 1024);
  }

  /** Obtain the content of this blob as {@link ByteSource}. */
  public ByteSource asByteSource() throws IOException {
    return ByteSource.wrap(dynamicOutputStream.toByteArray());
  }

  /** Returns an {@link OutputStream} to write to this blob. */
  public OutputStream openOutputStream() {
    return dynamicOutputStream;
  }

  /** Returns the size of the content written to this blob. */
  public long length() {
    return dynamicOutputStream.length();
  }

  @Override
  public void close() {
    dynamicOutputStream.close();
  }
}
