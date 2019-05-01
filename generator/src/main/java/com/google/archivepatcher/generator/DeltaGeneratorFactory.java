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

package com.google.archivepatcher.generator;

import com.google.archivepatcher.generator.bsdiff.BsDiffDeltaGenerator;
import com.google.archivepatcher.shared.PatchConstants;
import com.google.archivepatcher.shared.PatchConstants.DeltaFormat;
import java.util.Arrays;
import java.util.Collections;

/** Factory class for creating {@link DeltaGenerator}s. */
public class DeltaGeneratorFactory {

  private final boolean useNativeBsDiff;

  public DeltaGeneratorFactory(boolean useNativeBsDiff) {
    this.useNativeBsDiff = useNativeBsDiff;
  }

  public DeltaGenerator create(DeltaFormat deltaFormat) {
    switch (deltaFormat) {
      case BSDIFF:
        return new BsDiffDeltaGenerator(this.useNativeBsDiff);
      case FILE_BY_FILE:
        // Here we think that apks containing apks containing apks is too rare a use case. So we
        // only allow BSDIFF in the internal archive.
        // TODO: Handle XxxSizeLimiter properly in FBFV2
        return new FileByFileDeltaGenerator(
            Arrays.asList(
                new TotalRecompressionLimiter(
                    PatchConstants.TOTAL_RECOMPRESSION_LIMIT_EMBEDDED_ARCHIVE),
                new DeltaFriendlyOldBlobSizeLimiter(
                    PatchConstants.DELTA_FRIENDLY_OLD_BLOB_SIZE_LIMIT_EMBEDDED_ARCHIVE)),
            Collections.singleton(DeltaFormat.BSDIFF),
            this.useNativeBsDiff);
    }
    throw new IllegalArgumentException("Unsupported delta format " + deltaFormat);
  }
}
