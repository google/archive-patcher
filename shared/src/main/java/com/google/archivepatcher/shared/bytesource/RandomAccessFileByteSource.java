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

import com.google.archivepatcher.shared.Closeables;
import com.google.archivepatcher.shared.RandomAccessFileInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

/**
 * A {@link ByteSource} backed by a {@link RandomAccessFileInputStream}. This implementation is
 * created mainly for backwards compatibility.
 */
public class RandomAccessFileByteSource extends ByteSource {

  private final File file;
  private final long length;
  private final Queue<RandomAccessFileInputStream> unusedInputStreams = new ArrayDeque<>();
  private final List<RandomAccessFileInputStream> allInputStreams = new ArrayList<>();

  public RandomAccessFileByteSource(File file) throws IOException {
    this.file = file;
    this.length = file.length();
  }

  @Override
  public long length() {
    return length;
  }

  @Override
  protected InputStream openStream(long offset, long length) throws IOException {
    RandomAccessFileInputStream rafis = getUnusedStream();
    rafis.setRange(offset, length);
    return new ShadowInputStream(rafis, () -> unusedInputStreams.add(rafis));
  }

  private RandomAccessFileInputStream getUnusedStream() throws IOException {
    RandomAccessFileInputStream rafis = unusedInputStreams.poll();
    if (rafis == null) {
      rafis = new RandomAccessFileInputStream(file);
      allInputStreams.add(rafis);
    }
    return rafis;
  }

  @Override
  public void close() throws IOException {
    for (RandomAccessFileInputStream inputStream : allInputStreams) {
      Closeables.closeQuietly(inputStream);
    }
  }

  @Override
  protected void finalize() throws Throwable {
    close();
  }
}
