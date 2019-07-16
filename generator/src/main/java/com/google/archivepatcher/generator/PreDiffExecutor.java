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
import com.google.archivepatcher.shared.bytesource.ByteSource;
import java.io.IOException;
import java.io.OutputStream;
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

    private ByteSource originalOldBlob;
    private ByteSource originalNewBlob;
    private TempBlob deltaFriendlyOldBlob;
    private TempBlob deltaFriendlyNewBlob;

    /**
     * Sets the original, read-only input files to the patch generation process. This has to be
     * called at least once, and both arguments must be non-null.
     *
     * @param originalOldBlob the original old blob to read (will not be modified).
     * @param originalNewBlob the original new blob to read (will not be modified).
     */
    public Builder readingOriginalFiles(ByteSource originalOldBlob, ByteSource originalNewBlob) {
      if (originalOldBlob == null || originalNewBlob == null) {
        throw new IllegalStateException("do not set nul original input files");
      }
      this.originalOldBlob = originalOldBlob;
      this.originalNewBlob = originalNewBlob;
      return this;
    }

    /**
     * Sets the output blobs that will hold the delta-friendly intermediate binaries used in patch
     * generation. If called, both arguments must be non-null.
     *
     * @param deltaFriendlyOldBlob the intermediate blob to write (will be overwritten if it exists)
     * @param deltaFriendlyNewBlob the intermediate blob to write (will be overwritten if it exists)
     */
    public Builder writingDeltaFriendlyFiles(
        TempBlob deltaFriendlyOldBlob, TempBlob deltaFriendlyNewBlob) {
      if (deltaFriendlyOldBlob == null || deltaFriendlyNewBlob == null) {
        throw new IllegalStateException("do not set null delta-friendly files");
      }
      this.deltaFriendlyOldBlob = deltaFriendlyOldBlob;
      this.deltaFriendlyNewBlob = deltaFriendlyNewBlob;
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
      if (originalOldBlob == null) {
        // readingOriginalFiles() ensures old and new are non-null when called, so check either.
        throw new IllegalStateException("original input files cannot be null");
      }
      return new PreDiffExecutor(
          originalOldBlob,
          originalNewBlob,
          deltaFriendlyOldBlob,
          deltaFriendlyNewBlob,
          preDiffPlanEntryModifiers,
          supportedDeltaFormats);
    }
  }

  /** The original old file to read (will not be modified). */
  private final ByteSource originalOldBlob;

  /** The original new file to read (will not be modified). */
  private final ByteSource originalNewBlob;

  /**
   * Optional blob to write the delta-friendly version of the original old file to (will be created,
   * overwriting if it already exists). If null, only the read-only planning step can be performed.
   */
  private final TempBlob deltaFriendlyOldBlob;

  /**
   * Optional blob to write the delta-friendly version of the original new file to (will be created,
   * overwriting if it already exists). If null, only the read-only planning step can be performed.
   */
  private final TempBlob deltaFriendlyNewBlob;

  /**
   * Optional {@link PreDiffPlanEntryModifier}s to be used for modifying the patch to be generated.
   */
  private final List<PreDiffPlanEntryModifier> preDiffPlanEntryModifiers;

  /** {@link DeltaFormat}s supported for generating the patch. */
  private final Set<DeltaFormat> supportedDeltaFormats;

  /** Constructs a new PreDiffExecutor to work with the specified configuration. */
  private PreDiffExecutor(
      ByteSource originalOldBlob,
      ByteSource originalNewBlob,
      TempBlob deltaFriendlyOldBlob,
      TempBlob deltaFriendlyNewBlob,
      List<PreDiffPlanEntryModifier> preDiffPlanEntryModifiers,
      Set<DeltaFormat> supportedDeltaFormats) {
    this.originalOldBlob = originalOldBlob;
    this.originalNewBlob = originalNewBlob;
    this.deltaFriendlyOldBlob = deltaFriendlyOldBlob;
    this.deltaFriendlyNewBlob = deltaFriendlyNewBlob;
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
    if (deltaFriendlyOldBlob != null) {
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
    try (OutputStream bufferedOut =
        deltaFriendlyOldBlob.openBufferedStream()) {
      DeltaFriendlyFile.generateDeltaFriendlyFile(
          preDiffPlan.getOldFileUncompressionPlan(), originalOldBlob, bufferedOut);
    }
    try (OutputStream bufferedOut = deltaFriendlyNewBlob.openBufferedStream()) {
      return DeltaFriendlyFile.generateDeltaFriendlyFileWithInverse(
          preDiffPlan.getNewFileUncompressionPlan(), originalNewBlob, bufferedOut);
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
        MinimalZipArchive.listEntries(originalOldBlob);
    Map<ByteArrayHolder, MinimalZipEntry> originalOldArchiveZipEntriesByPath =
        new HashMap<ByteArrayHolder, MinimalZipEntry>(originalOldArchiveZipEntries.size());
    for (MinimalZipEntry zipEntry : originalOldArchiveZipEntries) {
      ByteArrayHolder key = new ByteArrayHolder(zipEntry.fileNameBytes());
      originalOldArchiveZipEntriesByPath.put(key, zipEntry);
    }

    List<DivinationResult> divinationResults =
        DefaultDeflateCompressionDiviner.divineDeflateParameters(originalNewBlob);
    Map<ByteArrayHolder, MinimalZipEntry> originalNewArchiveZipEntriesByPath =
        new HashMap<ByteArrayHolder, MinimalZipEntry>(divinationResults.size());
    Map<ByteArrayHolder, JreDeflateParameters> originalNewArchiveJreDeflateParametersByPath =
        new HashMap<ByteArrayHolder, JreDeflateParameters>(divinationResults.size());
    for (DivinationResult divinationResult : divinationResults) {
      ByteArrayHolder key = new ByteArrayHolder(divinationResult.minimalZipEntry.fileNameBytes());
      originalNewArchiveZipEntriesByPath.put(key, divinationResult.minimalZipEntry);
      originalNewArchiveJreDeflateParametersByPath.put(key, divinationResult.divinedParameters);
    }

    PreDiffPlanner preDiffPlanner =
        new PreDiffPlanner(
            originalOldBlob,
            originalOldArchiveZipEntriesByPath,
            originalNewBlob,
            originalNewArchiveZipEntriesByPath,
            originalNewArchiveJreDeflateParametersByPath,
            preDiffPlanEntryModifiers,
            supportedDeltaFormats);
    return preDiffPlanner.generatePreDiffPlan();
  }
}
