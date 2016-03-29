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

import com.google.archivepatcher.generator.DefaultDeflateCompressionDiviner.DivinationResult;
import com.google.archivepatcher.shared.DeltaFriendlyFile;
import com.google.archivepatcher.shared.JreDeflateParameters;
import com.google.archivepatcher.shared.TypedRange;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Prepares resources for differencing.
 */
class PreDiffExecutor {
  /**
   * The old archive to read from.
   */
  private final File originalOldFile;

  /**
   * The new archive to read from.
   */
  private final File originalNewFile;

  /**
   * The delta-friendly old archive to write.
   */
  private final File deltaFriendlyOldFile;

  /**
   * The delta-friendly new archive to write.
   */
  private final File deltaFriendlyNewFile;

  /**
   * Creates an executor that will prepare resources for diffing using the specified input and
   * output files
   * @param originalOldFile the original old file to read (will not be modified)
   * @param originalNewFile the original new file to read (will not be modified)
   * @param deltaFriendlyOldFile the file to write the delta-friendly version of the original old
   * file to (will be created, overwriting if it already exists)
   * @param deltaFriendlyNewFile the file to write the delta-friendly version of the original new
   * file to (will be created, overwriting if it already exists)
   */
  PreDiffExecutor(
      File originalOldFile,
      File originalNewFile,
      File deltaFriendlyOldFile,
      File deltaFriendlyNewFile) {
    this.originalOldFile = originalOldFile;
    this.originalNewFile = originalNewFile;
    this.deltaFriendlyOldFile = deltaFriendlyOldFile;
    this.deltaFriendlyNewFile = deltaFriendlyNewFile;
  }

  /**
   * Prepare resources for diffing and returns the completed plan.
   * @throws IOException if unable to complete the operation due to an I/O error
   */
  PreDiffPlan prepareForDiffing() throws IOException {
    PreDiffPlan preDiffPlan = generatePreDiffPlan();
    List<TypedRange<JreDeflateParameters>> deltaFriendlyNewFileRecompressionPlan =
        generateDeltaFriendlyFiles(preDiffPlan);
    return new PreDiffPlan(
        preDiffPlan.getOldFileUncompressionPlan(),
        preDiffPlan.getNewFileUncompressionPlan(),
        Collections.unmodifiableList(deltaFriendlyNewFileRecompressionPlan));
  }

  /**
   * Generate the delta-friendly files and return the plan for recompressing the delta-friendly
   * new file back into the original new file.
   * @param preDiffPlan the plan to execute
   * @return as described
   * @throws IOException if anything goes wrong
   */
  private List<TypedRange<JreDeflateParameters>> generateDeltaFriendlyFiles(PreDiffPlan preDiffPlan)
      throws IOException {
    try (FileOutputStream out = new FileOutputStream(deltaFriendlyOldFile);
        BufferedOutputStream bufferedOut = new BufferedOutputStream(out)) {
      DeltaFriendlyFile.generateDeltaFriendlyFile(
          preDiffPlan.getOldFileUncompressionPlan(), originalOldFile, bufferedOut);
    }
    try (FileOutputStream out = new FileOutputStream(deltaFriendlyNewFile);
        BufferedOutputStream bufferedOut = new BufferedOutputStream(out)) {
      return DeltaFriendlyFile.generateDeltaFriendlyFile(
          preDiffPlan.getNewFileUncompressionPlan(), originalNewFile, bufferedOut);
    }
  }

  /**
   * Analyze the original old and new files and generate a plan to transform them into their
   * delta-friendly equivalents.
   * @return the plan, which does not yet contain information for recompressing the delta-friendly
   * new archive.
   * @throws IOException if anything goes wrong
   */
  private PreDiffPlan generatePreDiffPlan() throws IOException {
    Map<ByteArrayHolder, MinimalZipEntry> originalOldArchiveZipEntriesByPath =
        new HashMap<ByteArrayHolder, MinimalZipEntry>();
    Map<ByteArrayHolder, MinimalZipEntry> originalNewArchiveZipEntriesByPath =
        new HashMap<ByteArrayHolder, MinimalZipEntry>();
    Map<ByteArrayHolder, JreDeflateParameters> originalNewArchiveJreDeflateParametersByPath =
        new HashMap<ByteArrayHolder, JreDeflateParameters>();

    for (MinimalZipEntry zipEntry : MinimalZipArchive.listEntries(originalOldFile)) {
      ByteArrayHolder key = new ByteArrayHolder(zipEntry.getFileNameBytes());
      originalOldArchiveZipEntriesByPath.put(key, zipEntry);
    }

    DefaultDeflateCompressionDiviner diviner = new DefaultDeflateCompressionDiviner();
    for (DivinationResult divinationResult : diviner.divineDeflateParameters(originalNewFile)) {
      ByteArrayHolder key =
          new ByteArrayHolder(divinationResult.minimalZipEntry.getFileNameBytes());
      originalNewArchiveZipEntriesByPath.put(key, divinationResult.minimalZipEntry);
      originalNewArchiveJreDeflateParametersByPath.put(key, divinationResult.divinedParameters);
    }

    PreDiffPlanner preDiffPlanner =
        new PreDiffPlanner(
            originalOldFile,
            originalOldArchiveZipEntriesByPath,
            originalNewFile,
            originalNewArchiveZipEntriesByPath,
            originalNewArchiveJreDeflateParametersByPath);
    return preDiffPlanner.generatePreDiffPlan();
  }
}
