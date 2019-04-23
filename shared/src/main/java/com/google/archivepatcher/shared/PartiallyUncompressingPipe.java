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

import static com.google.archivepatcher.shared.bytesource.ByteStreams.copy;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * A pipe that moves data from an {@link InputStream} to an {@link OutputStream}, optionally
 * uncompressing the input data on-the-fly.
 */
public class PartiallyUncompressingPipe implements Closeable {
  /**
   * The uncompressor used to uncompress compressed input streams.
   */
  private final DeflateUncompressor uncompressor;

  /**
   * The output stream to write to.
   */
  private final CountingOutputStream out;

  /**
   * Modes available for {@link PartiallyUncompressingPipe#pipe(InputStream, Mode)}.
   */
  public static enum Mode {
    /**
     * Copy bytes form the {@link InputStream} to the {@link OutputStream} without modification.
     */
    COPY,

    /**
     * Treat the {@link InputStream} as a deflate stream with nowrap=false, uncompress the bytes
     * on-the-fly and write the uncompressed data to the {@link OutputStream}.
     */
    UNCOMPRESS_WRAPPED,

    /**
     * Treat the {@link InputStream} as a deflate stream with nowrap=true, uncompress the bytes
     * on-the-fly and write the uncompressed data to the {@link OutputStream}.
     */
    UNCOMPRESS_NOWRAP,
  }

  /**
   * Constructs a new stream.
   *
   * @param out the stream, to write to
   */
  public PartiallyUncompressingPipe(OutputStream out) {
    this.out = new CountingOutputStream(out);
    uncompressor = new DeflateUncompressor();
    uncompressor.setCaching(true);
  }

  /**
   * Pipes the entire contents of the specified {@link InputStream} to the configured
   * {@link OutputStream}, optionally uncompressing on-the-fly.
   * @param in the stream to read from
   * @param mode the mode to use for reading and writing
   * @return the number of bytes written to the output stream
   * @throws IOException if anything goes wrong
   */
  public long pipe(InputStream in, Mode mode) throws IOException {
    long bytesWrittenBefore = out.getNumBytesWritten();
    if (mode == Mode.COPY) {
      copy(in, out);
    } else {
      uncompressor.setNowrap(mode == Mode.UNCOMPRESS_NOWRAP);
      uncompressor.uncompress(in, out);
    }
    out.flush();
    return out.getNumBytesWritten() - bytesWrittenBefore;
  }

  /**
   * Returns the number of bytes written to the stream so far.
   * @return as described
   */
  public long getNumBytesWritten() {
    return out.getNumBytesWritten();
  }

  @Override
  public void close() throws IOException {
    uncompressor.release();
    out.close();
  }
}
