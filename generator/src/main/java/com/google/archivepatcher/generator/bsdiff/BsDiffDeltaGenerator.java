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

package com.google.archivepatcher.generator.bsdiff;

import static com.google.archivepatcher.shared.PatchConstants.USE_NATIVE_BSDIFF_BY_DEFAULT;

import com.google.archivepatcher.generator.DeltaGenerator;
import com.google.archivepatcher.generator.bsdiff.wrapper.BsDiffNativePatchWriter;
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

  /** Whether to use the native version of BsDiff for generating patches. */
  private final boolean useNativeBsDiff;

  public BsDiffDeltaGenerator() {
    this(USE_NATIVE_BSDIFF_BY_DEFAULT);
  }

  public BsDiffDeltaGenerator(boolean useNativeBsDiff) {
    this.useNativeBsDiff = useNativeBsDiff;
  }

  @Override
  public void generateDelta(File oldBlob, File newBlob, OutputStream deltaOut)
      throws IOException, InterruptedException {
    if (useNativeBsDiff) {
      BsDiffNativePatchWriter.generatePatch(oldBlob, newBlob, deltaOut);
    } else {
      BsDiffPatchWriter.generatePatch(oldBlob, newBlob, deltaOut, MATCH_LENGTH_BYTES);
    }
  }

  public static void generateDelta(
      byte[] oldData, byte[] newData, OutputStream deltaOut, boolean generateDeltaNatively)
      throws IOException, InterruptedException {
    if (generateDeltaNatively) {
      BsDiffNativePatchWriter.generatePatch(oldData, newData, deltaOut);
    } else {
      BsDiffPatchWriter.generatePatch(oldData, newData, deltaOut, MATCH_LENGTH_BYTES);
    }
  }
}
