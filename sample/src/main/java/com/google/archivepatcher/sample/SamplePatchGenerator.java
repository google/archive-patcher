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

import com.google.archivepatcher.generator.FileByFileV1DeltaGenerator;
import com.google.archivepatcher.shared.DefaultDeflateCompatibilityWindow;
import java.io.File;
import java.io.FileOutputStream;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/** Generate a patch; args are old file path, new file path, and patch file path. */
public class SamplePatchGenerator {
  public static void main(String... args) throws Exception {
    if (!new DefaultDeflateCompatibilityWindow().isCompatible()) {
      System.err.println("zlib not compatible on this system");
      System.exit(-1);
    }
    File oldFile = new File(args[0]); // must be a zip archive
    File newFile = new File(args[1]); // must be a zip archive
    Deflater compressor = new Deflater(9, true); // to compress the patch
    try (FileOutputStream patchOut = new FileOutputStream(args[2]);
        DeflaterOutputStream compressedPatchOut =
            new DeflaterOutputStream(patchOut, compressor, 32768)) {
      new FileByFileV1DeltaGenerator()
          .generateDelta(oldFile, newFile, compressedPatchOut, /* generateDeltaNatively= */ false);
      compressedPatchOut.finish();
      compressedPatchOut.flush();
    } finally {
      compressor.end();
    }
  }
}
