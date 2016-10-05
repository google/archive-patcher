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

package com.google.archivepatcher.generator.bsdiff;

import com.google.archivepatcher.generator.DeltaGenerator;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

/**
 * An implementation of {@link DeltaGenerator} that uses {@link BsDiffPatchWriter} to write a
 * bsdiff patch that represents the delta between given inputs.
 */
public class BsDiffDeltaGenerator implements DeltaGenerator {
  /**
   * The minimum match length to use for bsdiff.
   */
  private static final int MATCH_LENGTH_BYTES = 16;

  @Override
  public void generateDelta(File oldBlob, File newBlob, OutputStream deltaOut)
      throws IOException, InterruptedException {
    BsDiffPatchWriter.generatePatch(oldBlob, newBlob, deltaOut, MATCH_LENGTH_BYTES);
  }
}
