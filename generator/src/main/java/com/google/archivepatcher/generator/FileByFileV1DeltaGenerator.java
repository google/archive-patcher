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

import com.google.archivepatcher.generator.bsdiff.BsDiffDeltaGenerator;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Generates file-by-file patches.
 */
public class FileByFileV1DeltaGenerator implements DeltaGenerator {

  /** Optional modifiers for planning and patch generation. */
  private final List<RecommendationModifier> recommendationModifiers;

  /**
   * Constructs a new generator for File-by-File v1 patches, using the specified configuration.
   *
   * @param recommendationModifiers optionally, {@link RecommendationModifier}s to use for modifying
   *     the planning phase of patch generation. These can be used to, e.g., limit the total amount
   *     of recompression that a patch applier needs to do. Modifiers are applied in the order they
   *     are specified.
   */
  public FileByFileV1DeltaGenerator(RecommendationModifier... recommendationModifiers) {
    if (recommendationModifiers != null) {
      this.recommendationModifiers =
          Collections.unmodifiableList(Arrays.asList(recommendationModifiers));
    } else {
      this.recommendationModifiers = Collections.emptyList();
    }
  }

  /**
   * Generate a V1 patch for the specified input files and write the patch to the specified {@link
   * OutputStream}. The written patch is <em>raw</em>, i.e. it has not been compressed. Compression
   * should almost always be applied to the patch, either right in the specified {@link
   * OutputStream} or in a post-processing step, prior to transmitting the patch to the patch
   * applier.
   *
   * @param oldFile the original old file to read (will not be modified)
   * @param newFile the original new file to read (will not be modified)
   * @param patchOut the stream to write the patch to
   * @throws IOException if unable to complete the operation due to an I/O error
   * @throws InterruptedException if any thread has interrupted the current thread
   */
  @Override
  public void generateDelta(File oldFile, File newFile, OutputStream patchOut)
      throws IOException, InterruptedException {
    try (TempFileHolder deltaFriendlyOldFile = new TempFileHolder();
        TempFileHolder deltaFriendlyNewFile = new TempFileHolder();
        TempFileHolder deltaFile = new TempFileHolder();
        FileOutputStream deltaFileOut = new FileOutputStream(deltaFile.file);
        BufferedOutputStream bufferedDeltaOut = new BufferedOutputStream(deltaFileOut)) {
      PreDiffExecutor.Builder builder =
          new PreDiffExecutor.Builder()
              .readingOriginalFiles(oldFile, newFile)
              .writingDeltaFriendlyFiles(deltaFriendlyOldFile.file, deltaFriendlyNewFile.file);
      for (RecommendationModifier modifier : recommendationModifiers) {
        builder.withRecommendationModifier(modifier);
      }
      PreDiffExecutor executor = builder.build();
      PreDiffPlan preDiffPlan = executor.prepareForDiffing();
      DeltaGenerator deltaGenerator = getDeltaGenerator();
      deltaGenerator.generateDelta(
          deltaFriendlyOldFile.file, deltaFriendlyNewFile.file, bufferedDeltaOut);
      bufferedDeltaOut.close();
      PatchWriter patchWriter =
          new PatchWriter(
              preDiffPlan,
              deltaFriendlyOldFile.file.length(),
              deltaFriendlyNewFile.file.length(),
              deltaFile.file);
      patchWriter.writeV1Patch(patchOut);
    }
  }

  /**
   * Generate a V1 patch pre diffing plan.
   *
   * @param oldFile the original old file to read (will not be modified)
   * @param newFile the original new file to read (will not be modified)
   * @return the plan
   * @throws IOException if unable to complete the operation due to an I/O error
   * @throws InterruptedException if any thread has interrupted the current thread
   */
  public PreDiffPlan generatePreDiffPlan(File oldFile, File newFile)
      throws IOException, InterruptedException {
    try (TempFileHolder deltaFriendlyOldFile = new TempFileHolder();
        TempFileHolder deltaFriendlyNewFile = new TempFileHolder()) {
      PreDiffExecutor.Builder builder =
          new PreDiffExecutor.Builder()
              .readingOriginalFiles(oldFile, newFile)
              .writingDeltaFriendlyFiles(deltaFriendlyOldFile.file, deltaFriendlyNewFile.file);
      for (RecommendationModifier modifier : recommendationModifiers) {
        builder.withRecommendationModifier(modifier);
      }

      PreDiffExecutor executor = builder.build();

      return executor.prepareForDiffing();
    }
  }

  // Visible for testing only
  protected DeltaGenerator getDeltaGenerator() {
    return new BsDiffDeltaGenerator();
  }
}
