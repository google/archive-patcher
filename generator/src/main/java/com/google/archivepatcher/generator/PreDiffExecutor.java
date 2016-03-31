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
public class PreDiffExecutor {

  /**
   * Prepare resources for diffing and returns the completed plan. This is equivalent to calling
   * {@link #generatePreDiffPlan(File, File)} and
   * {@link #generateDeltaFriendlyFiles(PreDiffPlan, File, File, File, File)} in order and returning
   * a {@link PreDiffPlan} that contains the information from both calls.
   * @param originalOldFile the original old file to read (will not be modified)
   * @param originalNewFile the original new file to read (will not be modified)
   * @param deltaFriendlyOldFile the file to write the delta-friendly version of the original old
   * file to (will be created, overwriting if it already exists)
   * @param deltaFriendlyNewFile the file to write the delta-friendly version of the original new
   * file to (will be created, overwriting if it already exists)
   * @throws IOException if unable to complete the operation due to an I/O error
   */
  public static PreDiffPlan prepareForDiffing(
      File originalOldFile,
      File originalNewFile,
      File deltaFriendlyOldFile,
      File deltaFriendlyNewFile) throws IOException {
    PreDiffPlan preDiffPlan = generatePreDiffPlan(originalOldFile, originalNewFile);
    List<TypedRange<JreDeflateParameters>> deltaFriendlyNewFileRecompressionPlan =
        generateDeltaFriendlyFiles(
            preDiffPlan,
            originalOldFile,
            originalNewFile,
            deltaFriendlyOldFile,
            deltaFriendlyNewFile);
    return new PreDiffPlan(
        preDiffPlan.getQualifiedRecommendations(),
        preDiffPlan.getOldFileUncompressionPlan(),
        preDiffPlan.getNewFileUncompressionPlan(),
        Collections.unmodifiableList(deltaFriendlyNewFileRecompressionPlan));
  }

  /**
   * Generate the delta-friendly files and return the plan for recompressing the delta-friendly
   * new file back into the original new file.
   * @param preDiffPlan the plan to execute
   * @param originalOldFile the original old file to read (will not be modified)
   * @param originalNewFile the original new file to read (will not be modified)
   * @param deltaFriendlyOldFile the file to write the delta-friendly version of the original old
   * file to (will be created, overwriting if it already exists)
   * @param deltaFriendlyNewFile the file to write the delta-friendly version of the original new
   * file to (will be created, overwriting if it already exists)
   * @return as described
   * @throws IOException if anything goes wrong
   */
  public static List<TypedRange<JreDeflateParameters>> generateDeltaFriendlyFiles(
      PreDiffPlan preDiffPlan,
      File originalOldFile,
      File originalNewFile,
      File deltaFriendlyOldFile,
      File deltaFriendlyNewFile)
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
   * @param originalOldFile the original old file to read (will not be modified)
   * @param originalNewFile the original new file to read (will not be modified)
   * @return the plan, which does not yet contain information for recompressing the delta-friendly
   * new archive.
   * @throws IOException if anything goes wrong
   */
  public static PreDiffPlan generatePreDiffPlan(
      File originalOldFile,
      File originalNewFile) throws IOException {
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
