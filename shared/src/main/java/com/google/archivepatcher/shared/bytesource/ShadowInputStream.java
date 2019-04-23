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
import javax.annotation.Nullable;

/**
 * An {@link InputStream} that redirects all operations to underlying input stream but does not
 * close the underlying stream if it is itself closed. Reading from a closed {@link
 * ShadowInputStream} will cause an {@link IOException}.
 *
 * @param <T> type of the underlying stream
 */
public class ShadowInputStream<T extends InputStream> extends InputStream {

  private final T in;
  // Normally we would use Optional<Runnable> for this. However, java.util.Optional is not available
  // for Android so we have to use Nullable.
  @Nullable private Runnable closeCallback = null;

  private boolean isOpen = true;

  public ShadowInputStream(T in) {
    this.in = in;
  }

  /**
   * Opens this stream.
   *
   * <p>Note that the state of underlying stream is not affected and will remain at the last time
   * this is closed.
   *
   * @param closeCallback callback when the stream is closed
   */
  public void open(Runnable closeCallback) {
    isOpen = true;
    this.closeCallback = closeCallback;
  }

  public void open() {
    isOpen = true;
  }

  /** Returns the underlying stream. */
  public T getStream() {
    return in;
  }

  @Override
  public void close() throws IOException {
    if (isOpen) {
      isOpen = false;
      if (closeCallback != null) {
        closeCallback.run();
        closeCallback = null;
      }
    }
  }

  @Override
  public int available() throws IOException {
    assertIsOpen();
    return in.available();
  }

  @Override
  public synchronized void mark(int readlimit) {
    in.mark(readlimit);
  }

  @Override
  public boolean markSupported() {
    return in.markSupported();
  }

  @Override
  public synchronized void reset() throws IOException {
    assertIsOpen();
    in.reset();
  }

  @Override
  public int read() throws IOException {
    assertIsOpen();
    return in.read();
  }

  @Override
  public int read(byte[] b) throws IOException {
    assertIsOpen();
    return in.read(b);
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    assertIsOpen();
    return in.read(b, off, len);
  }

  @Override
  public long skip(long n) throws IOException {
    assertIsOpen();
    return in.skip(n);
  }

  private void assertIsOpen() throws IOException {
    if (!isOpen) {
      throw new IOException("InputStream is closed.");
    }
  }

  @Override
  protected void finalize() throws Throwable {
    close();
  }
}
