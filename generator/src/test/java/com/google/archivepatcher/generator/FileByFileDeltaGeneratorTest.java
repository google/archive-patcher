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

import com.google.archivepatcher.shared.PatchConstants.DeltaFormat;
import com.google.archivepatcher.shared.UnitTestZipArchive;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * Tests for {@link FileByFileDeltaGenerator}. This relies heavily on the correctness of {@link
 * PatchWriterTest}, which validates the patch writing process itself, {@link PreDiffPlannerTest},
 * which validates the decision making process for delta-friendly blobs, and {@link
 * PreDiffExecutorTest}, which validates the ability to create the delta-friendly blobs. The {@link
 * FileByFileDeltaGenerator} <em>itself</em> is relatively simple, combining all of these pieces of
 * functionality together to create a patch; so the tests here are just ensuring that a patch can be
 * produced.
 */
@RunWith(Parameterized.class)
@SuppressWarnings("javadoc")
public class FileByFileDeltaGeneratorTest {

  @Parameters
  public static Collection<Object[]> data() {
    // Note that the order of the parameter is important for gradle to ignore the native test.
    return Arrays.asList(new Object[][] {{true}, {false}});
  }

  // Indicates whether native BsDiff should be used
  private final boolean useNativeBsDiff;

  public FileByFileDeltaGeneratorTest(boolean useNativeBsDiff) {
    this.useNativeBsDiff = useNativeBsDiff;
  }

  @Test
  public void testGenerateDelta_BaseCase() throws Exception {
    // Simple test of generating a patch with no changes.
    FileByFileDeltaGenerator generator =
        new FileByFileDeltaGenerator(
            /* preDiffPlanEntryModifiers= */ Collections.emptyList(),
            Collections.singleton(DeltaFormat.BSDIFF),
            useNativeBsDiff);
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    try (TempFileHolder oldArchive = new TempFileHolder();
        TempFileHolder newArchive = new TempFileHolder()) {
      UnitTestZipArchive.saveTestZip(oldArchive.file);
      UnitTestZipArchive.saveTestZip(newArchive.file);
      generator.generateDelta(oldArchive.file, newArchive.file, buffer);
    }
    byte[] result = buffer.toByteArray();
    Assert.assertTrue(result.length > 0);
  }
}
