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

import com.google.archivepatcher.generator.DefaultDeflateCompressionDiviner.DivinationResult;
import com.google.archivepatcher.shared.DeltaFriendlyFile;
import com.google.archivepatcher.shared.JreDeflateParameters;
import com.google.archivepatcher.shared.PatchConstants.DeltaFormat;
import com.google.archivepatcher.shared.TypedRange;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Prepares resources for differencing.
 */
public class PreDiffExecutor {

  /** A helper class to build a {@link PreDiffExecutor} with a variety of configurations. */
  public static final class Builder {
    private final List<PreDiffPlanEntryModifier> preDiffPlanEntryModifiers = new ArrayList<>();
    private final Set<DeltaFormat> supportedDeltaFormats = new HashSet<>();

    private File originalOldFile;
    private File originalNewFile;
    private File deltaFriendlyOldFile;
    private File deltaFriendlyNewFile;

    /**
     * Sets the original, read-only input files to the patch generation process. This has to be
     * called at least once, and both arguments must be non-null.
     *
     * @param originalOldFile the original old file to read (will not be modified).
     * @param originalNewFile the original new file to read (will not be modified).
     */
    public Builder readingOriginalFiles(File originalOldFile, File originalNewFile) {
      if (originalOldFile == null || originalNewFile == null) {
        throw new IllegalStateException("do not set nul original input files");
      }
      this.originalOldFile = originalOldFile;
      this.originalNewFile = originalNewFile;
      return this;
    }

    /**
     * Sets the output files that will hold the delta-friendly intermediate binaries used in patch
     * generation. If called, both arguments must be non-null.
     *
     * @param deltaFriendlyOldFile the intermediate file to write (will be overwritten if it exists)
     * @param deltaFriendlyNewFile the intermediate file to write (will be overwritten if it exists)
     */
    public Builder writingDeltaFriendlyFiles(File deltaFriendlyOldFile, File deltaFriendlyNewFile) {
      if (deltaFriendlyOldFile == null || deltaFriendlyNewFile == null) {
        throw new IllegalStateException("do not set null delta-friendly files");
      }
      this.deltaFriendlyOldFile = deltaFriendlyOldFile;
      this.deltaFriendlyNewFile = deltaFriendlyNewFile;
      return this;
    }

    /**
     * Appends an optional {@link PreDiffPlanEntryModifier} to be used during the generation of the
     * {@link PreDiffPlan} and/or delta-friendly blobs.
     *
     * @param preDiffPlanEntryModifier the modifier to set
     */
    public Builder addPreDiffPlanEntryModifier(PreDiffPlanEntryModifier preDiffPlanEntryModifier) {
      if (preDiffPlanEntryModifier == null) {
        throw new IllegalArgumentException("cannot add null preDiffPlanEntryModifier");
      }
      this.preDiffPlanEntryModifiers.add(preDiffPlanEntryModifier);
      return this;
    }

    /**
     * Appends a collection of {@link PreDiffPlanEntryModifier}s to be used during the generation of
     * the {@link PreDiffPlan} and/or delta-friendly blobs.
     *
     * @param preDiffPlanEntryModifiers the modifier to set
     */
    public Builder addPreDiffPlanEntryModifiers(
        Collection<? extends PreDiffPlanEntryModifier> preDiffPlanEntryModifiers) {
      if (preDiffPlanEntryModifiers == null) {
        throw new IllegalArgumentException("preDiffPlanEntryModifiers cannot be null");
      }
      this.preDiffPlanEntryModifiers.addAll(preDiffPlanEntryModifiers);
      return this;
    }

    /**
     * Amends a collection of {@link DeltaFormat}s to be used in the generation of the {@link
     * PreDiffPlan}.
     */
    public Builder addSupportedDeltaFormats(Collection<DeltaFormat> supportedDeltaFormats) {
      if (supportedDeltaFormats == null) {
        throw new IllegalArgumentException("supportedDeltaFormats cannot be null");
      }
      this.supportedDeltaFormats.addAll(supportedDeltaFormats);
      return this;
    }

    /**
     * Builds and returns a {@link PreDiffExecutor} according to the currnet configuration.
     */
    public PreDiffExecutor build() {
      if (originalOldFile == null) {
        // readingOriginalFiles() ensures old and new are non-null when called, so check either.
        throw new IllegalStateException("original input files cannot be null");
      }
      return new PreDiffExecutor(
          originalOldFile,
          originalNewFile,
          deltaFriendlyOldFile,
          deltaFriendlyNewFile,
          preDiffPlanEntryModifiers,
          supportedDeltaFormats);
    }
  }

  /** The original old file to read (will not be modified). */
  private final File originalOldFile;

  /** The original new file to read (will not be modified). */
  private final File originalNewFile;

  /**
   * Optional file to write the delta-friendly version of the original old file to (will be created,
   * overwriting if it already exists). If null, only the read-only planning step can be performed.
   */
  private final File deltaFriendlyOldFile;

  /**
   * Optional file to write the delta-friendly version of the original new file to (will be created,
   * overwriting if it already exists). If null, only the read-only planning step can be performed.
   */
  private final File deltaFriendlyNewFile;

  /**
   * Optional {@link PreDiffPlanEntryModifier}s to be used for modifying the patch to be generated.
   */
  private final List<PreDiffPlanEntryModifier> preDiffPlanEntryModifiers;

  /** {@link DeltaFormat}s supported for generating the patch. */
  private final Set<DeltaFormat> supportedDeltaFormats;

  /** Constructs a new PreDiffExecutor to work with the specified configuration. */
  private PreDiffExecutor(
      File originalOldFile,
      File originalNewFile,
      File deltaFriendlyOldFile,
      File deltaFriendlyNewFile,
      List<PreDiffPlanEntryModifier> preDiffPlanEntryModifiers,
      Set<DeltaFormat> supportedDeltaFormats) {
    this.originalOldFile = originalOldFile;
    this.originalNewFile = originalNewFile;
    this.deltaFriendlyOldFile = deltaFriendlyOldFile;
    this.deltaFriendlyNewFile = deltaFriendlyNewFile;
    this.preDiffPlanEntryModifiers = preDiffPlanEntryModifiers;
    this.supportedDeltaFormats = supportedDeltaFormats;
  }

  /**
   * Prepare resources for diffing and returns the completed plan.
   *
   * @throws IOException if unable to complete the operation due to an I/O error
   */
  public PreDiffPlan prepareForDiffing() throws IOException {
    PreDiffPlan preDiffPlan = generatePreDiffPlan();
    List<TypedRange<JreDeflateParameters>> deltaFriendlyNewFileRecompressionPlan = null;
    if (deltaFriendlyOldFile != null) {
      // Builder.writingDeltaFriendlyFiles() ensures old and new are non-null when called, so a
      // check on either is sufficient.
      deltaFriendlyNewFileRecompressionPlan =
          Collections.unmodifiableList(generateDeltaFriendlyFiles(preDiffPlan));
    }
    return new PreDiffPlan(
        preDiffPlan.getPreDiffPlanEntries(),
        preDiffPlan.getOldFileUncompressionPlan(),
        preDiffPlan.getNewFileUncompressionPlan(),
        deltaFriendlyNewFileRecompressionPlan);
  }

  /**
   * Generate the delta-friendly files and return the plan for recompressing the delta-friendly new
   * file back into the original new file.
   *
   * @param preDiffPlan the plan to execute
   * @throws IOException if anything goes wrong
   */
  private List<TypedRange<JreDeflateParameters>> generateDeltaFriendlyFiles(PreDiffPlan preDiffPlan)
      throws IOException {
    try (BufferedOutputStream bufferedOut =
        new BufferedOutputStream(new FileOutputStream(deltaFriendlyOldFile))) {
      DeltaFriendlyFile.generateDeltaFriendlyFile(
          preDiffPlan.getOldFileUncompressionPlan(), originalOldFile, bufferedOut);
    }
    try (BufferedOutputStream bufferedOut =
        new BufferedOutputStream(new FileOutputStream(deltaFriendlyNewFile))) {
      return DeltaFriendlyFile.generateDeltaFriendlyFile(
          preDiffPlan.getNewFileUncompressionPlan(), originalNewFile, bufferedOut);
    }
  }

  /**
   * Analyze the original old and new files and generate a plan to transform them into their
   * delta-friendly equivalents.
   *
   * @return the plan, which does not yet contain information for recompressing the delta-friendly
   *     new archive.
   * @throws IOException if anything goes wrong
   */
  private PreDiffPlan generatePreDiffPlan() throws IOException {
    List<MinimalZipEntry> originalOldArchiveZipEntries =
        MinimalZipArchive.listEntries(originalOldFile);
    Map<ByteArrayHolder, MinimalZipEntry> originalOldArchiveZipEntriesByPath =
        new HashMap<ByteArrayHolder, MinimalZipEntry>(originalOldArchiveZipEntries.size());
    for (MinimalZipEntry zipEntry : originalOldArchiveZipEntries) {
      ByteArrayHolder key = new ByteArrayHolder(zipEntry.getFileNameBytes());
      originalOldArchiveZipEntriesByPath.put(key, zipEntry);
    }

    List<DivinationResult> divinationResults =
        DefaultDeflateCompressionDiviner.divineDeflateParameters(originalNewFile);
    Map<ByteArrayHolder, MinimalZipEntry> originalNewArchiveZipEntriesByPath =
        new HashMap<ByteArrayHolder, MinimalZipEntry>(divinationResults.size());
    Map<ByteArrayHolder, JreDeflateParameters> originalNewArchiveJreDeflateParametersByPath =
        new HashMap<ByteArrayHolder, JreDeflateParameters>(divinationResults.size());
    for (DivinationResult divinationResult : divinationResults) {
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
            originalNewArchiveJreDeflateParametersByPath,
            preDiffPlanEntryModifiers,
            supportedDeltaFormats);
    return preDiffPlanner.generatePreDiffPlan();
  }
}
