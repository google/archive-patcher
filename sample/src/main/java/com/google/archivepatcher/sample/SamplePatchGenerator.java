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

package com.google.archivepatcher.sample;

import com.google.archivepatcher.generator.FileByFileDeltaGenerator;
import com.google.archivepatcher.shared.DefaultDeflateCompatibilityWindow;
import com.google.archivepatcher.shared.PatchConstants.DeltaFormat;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Collections;
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
    Deflater compressor = new Deflater(/* level= */ 9, /* nowrap= */ true); // to compress the patch
    try (FileOutputStream patchOut = new FileOutputStream(args[2]);
        DeflaterOutputStream compressedPatchOut =
            new DeflaterOutputStream(patchOut, compressor, /* size= */ 32768)) {
      new FileByFileDeltaGenerator(
              /* preDiffPlanEntryModifiers= */ Collections.emptyList(),
              Collections.singleton(DeltaFormat.BSDIFF))
          .generateDelta(oldFile, newFile, compressedPatchOut);
      compressedPatchOut.finish();
      compressedPatchOut.flush();
    } finally {
      compressor.end();
    }
  }
}
