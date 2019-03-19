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
import java.io.IOException;
import java.io.OutputStream;

/** An interface to be implemented by v2 delta generators. */
public interface DeltaGeneratorV2 {
  /**
   * Generates a delta in deltaOut that can be applied to oldBlob to produce newBlob.
   *
   * @param oldBlob the old blob
   * @param newBlob the new blob
   * @param deltaOut the stream to write the delta to
   * @throws IOException in the event of an I/O error reading the input files or writing to the
   *     delta output stream
   * @throws InterruptedException if any thread has interrupted the current thread
   */
  void generateDelta(
      RandomAccessFileInputStream oldBlob,
      RandomAccessFileInputStream newBlob,
      OutputStream deltaOut)
      throws IOException, InterruptedException;
}
