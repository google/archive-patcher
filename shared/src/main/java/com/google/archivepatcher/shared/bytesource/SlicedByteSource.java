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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

/** A {@link ByteSource} derived from a slice of another {@link ByteSource}. */
public class SlicedByteSource extends ByteSource {

  private final ByteSource byteSource;
  /**
   * Start of current {@link SlicedByteSource} inside {@link #byteSource} in absolute bytes.
   * Inclusive.
   */
  private final long startOfSlice;
  /**
   * End of current {@link SlicedByteSource} inside {@link #byteSource} in absolute bytes.
   * Exclusive.
   */
  private final long endOfSlice;

  public SlicedByteSource(ByteSource byteSource, long offset, long length) {
    this.byteSource = byteSource;
    this.startOfSlice = getValidOffset(offset);
    this.endOfSlice = getValidOffset(this.startOfSlice + length);
  }

  @Override
  protected InputStream openStream(long offset, long length) throws IOException {
    long startOfStream = getValidOffset(startOfSlice + offset);
    long endOfStream = getValidOffset(startOfStream + length);

    return byteSource.openStream(startOfStream, endOfStream - startOfStream);
  }

  @Override
  public ByteSource slice(long offset, long length) {
    // Here we create a fresh instance of SlicedByteSource instead of creating a chain of
    // SlidedByteSource (the case where we leave this method unimplemented for easier reasoning and
    // less memory usage (unused slices can be GC-ed faster).
    long newStartOfSlice = getValidOffset(startOfSlice + offset);
    long newEndOfSlice = getValidOffset(newStartOfSlice + length);

    return new SlicedByteSource(byteSource, newStartOfSlice, newEndOfSlice - newStartOfSlice);
  }

  /**
   * Opens a {@link BufferedInputStream} for this {@link ByteSource}. If {@link #byteSource} is of
   * {@link ByteArrayByteSource} or {@link MmapByteSource} type then this is unnecessary.
   */
  @Override
  public InputStream openBufferedStream() throws IOException {
    return new BufferedInputStream(openStream(0, length()));
  }

  @Override
  public long length() {
    return endOfSlice - startOfSlice;
  }

  /**
   * Returns a valid offset into {@link #byteSource} from {@code offset}.
   *
   * <p>The value returned will be
   *
   * <ul>
   *   <li>0 if offset < 0
   *   <li>offset if 0 <= offset <= byteSource.length()
   *   <li>byteSource.length() if offset > byteSource.length()
   * </ul>
   */
  private long getValidOffset(long offset) {
    if (offset < 0) {
      return 0;
    } else if (offset > byteSource.length()) {
      return byteSource.length();
    } else /* 0 <= offset <= byteSource.length() */ {
      return offset;
    }
  }

  @Override
  public void close() throws IOException {
    // Do nothing since we want to be able to create different slices from the original byteSource.
  }
}
