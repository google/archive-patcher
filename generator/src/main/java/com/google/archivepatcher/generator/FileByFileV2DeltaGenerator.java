// Copyright 2016 Google Inc. All rights reserved.
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

package com.google.archivepatcher.generator;

import com.google.archivepatcher.shared.RandomAccessFileInputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

/** Generates file-by-file patches using file-type specific algorithms if applicable. */
public class FileByFileV2DeltaGenerator implements DeltaGeneratorV2 {

  /**
   * Convenience method for generating delta on {@link File} rather than {@link
   * RandomAccessFileInputStream}.
   *
   * @see DeltaGeneratorV2#generateDelta(RandomAccessFileInputStream, RandomAccessFileInputStream,
   *     OutputStream)
   */
  public void generateDelta(File oldBlob, File newBlob, OutputStream deltaOut)
      throws IOException, InterruptedException {
    try (RandomAccessFileInputStream oldBlobInputStream = new RandomAccessFileInputStream(oldBlob);
        RandomAccessFileInputStream newBlobInputStream = new RandomAccessFileInputStream(newBlob)) {
      generateDelta(oldBlobInputStream, newBlobInputStream, deltaOut);
    }
  }

  @Override
  public void generateDelta(
      RandomAccessFileInputStream oldBlob,
      RandomAccessFileInputStream newBlob,
      OutputStream deltaOut)
      throws IOException, InterruptedException {
    throw new UnsupportedOperationException();
  }
}
