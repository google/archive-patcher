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

package com.google.archivepatcher.sample;

import com.google.archivepatcher.applier.FileByFileV1DeltaApplier;
import com.google.archivepatcher.shared.DefaultDeflateCompatibilityWindow;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/** Apply a patch; args are old file path, patch file path, and new file path. */
public class SamplePatchApplier {
  public static void main(String... args) throws Exception {
    if (!new DefaultDeflateCompatibilityWindow().isCompatible()) {
      System.err.println("zlib not compatible on this system");
      System.exit(-1);
    }
    File oldFile = new File(args[0]); // must be a zip archive
    Inflater uncompressor = new Inflater(true);
    try (FileInputStream compressedPatchIn = new FileInputStream(args[1]);
        InflaterInputStream patchIn =
            new InflaterInputStream(compressedPatchIn, uncompressor, 32768);
        FileOutputStream newFileOut = new FileOutputStream(args[2])) {
      new FileByFileV1DeltaApplier().applyDelta(oldFile, patchIn, newFileOut);
    } finally {
      uncompressor.end();
    }
  }
}
