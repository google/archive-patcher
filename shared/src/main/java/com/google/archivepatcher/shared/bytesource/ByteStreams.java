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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** This class contains utility methods related to IO. */
public class ByteStreams {
  public static final int COPY_BUFFER_SIZE = 32768;

  /**
   * Read exactly the specified number of bytes into the specified buffer.
   *
   * @param in the input stream to read from
   * @param destination where to write the bytes to
   * @param startAt the offset at which to start writing bytes in the destination buffer
   * @param numBytes the number of bytes to read
   * @throws IOException if reading from the stream fails
   */
  public static void readFully(
      final InputStream in, final byte[] destination, final int startAt, final int numBytes)
      throws IOException {
    int numRead = 0;
    while (numRead < numBytes) {
      int readNow = in.read(destination, startAt + numRead, numBytes - numRead);
      if (readNow == -1) {
        throw new IOException("truncated input stream");
      }
      numRead += readNow;
    }
  }

  /**
   * Read into the buffer from an input stream and fail if not enough data to fill the buffer.
   *
   * @param in the input stream to read from
   * @param destination where to write the bytes to
   * @throws IOException if reading from the stream fails
   */
  public static void readFully(final InputStream in, final byte[] destination) throws IOException {
    readFully(in, destination, 0, destination.length);
  }

  /** Copies everything from an {@link InputStream} to an {@link OutputStream}. */
  public static void copy(InputStream in, OutputStream out) throws IOException {
    byte[] buffer = new byte[COPY_BUFFER_SIZE];
    int numRead = 0;
    while ((numRead = in.read(buffer)) >= 0) {
      out.write(buffer, 0, numRead);
    }
  }
}
