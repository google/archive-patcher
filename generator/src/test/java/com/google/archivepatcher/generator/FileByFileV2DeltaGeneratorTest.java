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

import java.io.ByteArrayOutputStream;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link FileByFileV2DeltaGenerator}. This relies heavily on the correctness of {@link
 * PatchWriterTest}, which validates the patch writing process itself, {@link PreDiffPlannerTest},
 * which validates the decision making process for delta-friendly blobs, and {@link
 * PreDiffExecutorTest}, which validates the ability to create the delta-friendly blobs. The {@link
 * FileByFileV2DeltaGenerator} <em>itself</em> is relatively simple, combining all of these pieces
 * of functionality together to create a patch; so the tests here are just ensuring that a patch can
 * be produced.
 */
@RunWith(JUnit4.class)
public class FileByFileV2DeltaGeneratorTest {

  @Test
  public void generateDelta() throws Exception {
    FileByFileV2DeltaGenerator generator = new FileByFileV2DeltaGenerator();

    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    try (TempFileHolder oldArchive = new TempFileHolder();
        TempFileHolder newArchive = new TempFileHolder()) {
      Assert.assertThrows(
          UnsupportedOperationException.class,
          () -> generator.generateDelta(oldArchive.file, newArchive.file, buffer));
    }
  }
}
