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

import static com.google.archivepatcher.shared.PatchConstants.USE_NATIVE_BSDIFF_BY_DEFAULT;

import com.google.archivepatcher.generator.bsdiff.BsDiffDeltaGenerator;
import com.google.archivepatcher.shared.PatchConstants.DeltaFormat;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Generates file-by-file patches. */
public class FileByFileDeltaGenerator implements DeltaGenerator {

  /** Modifiers for planning and patch generation. */
  private final List<PreDiffPlanEntryModifier> preDiffPlanEntryModifiers;

  /**
   * List of supported delta formats. For more info, see the "delta descriptor record" section of
   * the patch format spec.
   */
  private final Set<DeltaFormat> supportedDeltaFormats;

  private final boolean useNativeBsDiff;

  /**
   * Constructs a new generator for File-by-File patches, using the specified configuration.
   *
   * @param preDiffPlanEntryModifiers optionally, {@link PreDiffPlanEntryModifier}s to use for
   *     modifying the planning phase of patch generation. These can be used to, e.g., limit the
   *     total amount of recompression that a patch applier needs to do. Modifiers are applied in
   *     the order they are specified.
   * @param supportedDeltaFormats the set of supported delta formats to use in the patch
   */
  public FileByFileDeltaGenerator(
      List<PreDiffPlanEntryModifier> preDiffPlanEntryModifiers,
      Set<DeltaFormat> supportedDeltaFormats) {
    this(preDiffPlanEntryModifiers, supportedDeltaFormats, USE_NATIVE_BSDIFF_BY_DEFAULT);
  }

  /**
   * Constructs a new generator for File-by-File patches, using the specified configuration.
   *
   * @param preDiffPlanEntryModifiers optionally, {@link PreDiffPlanEntryModifier}s to use for
   *     modifying the planning phase of patch generation. These can be used to, e.g., limit the
   *     total amount of recompression that a patch applier needs to do. Modifiers are applied in
   *     the order they are specified.
   * @param supportedDeltaFormats the set of supported delta formats to use in the patch
   * @param useNativeBsDiff whether to use the native implementation of BSDIFF internally
   */
  public FileByFileDeltaGenerator(
      List<PreDiffPlanEntryModifier> preDiffPlanEntryModifiers,
      Set<DeltaFormat> supportedDeltaFormats,
      boolean useNativeBsDiff) {
    this.preDiffPlanEntryModifiers = getImmutableListCopy(preDiffPlanEntryModifiers);
    this.supportedDeltaFormats = getImmutableSetCopy(supportedDeltaFormats);
    this.useNativeBsDiff = useNativeBsDiff;
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
      PreDiffPlan preDiffPlan =
          generatePreDiffPlan(
              oldFile, newFile, deltaFriendlyOldFile, deltaFriendlyNewFile, supportedDeltaFormats);
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
      patchWriter.writePatch(patchOut);
    }
  }

  /**
   * Generate a V1 patch pre diffing plan.
   *
   * @param oldFile the original old file to read (will not be modified)
   * @param newFile the original new file to read (will not be modified)
   * @throws IOException if unable to complete the operation due to an I/O error
   */
  public PreDiffPlan generatePreDiffPlan(File oldFile, File newFile) throws IOException {
    try (TempFileHolder deltaFriendlyOldFile = new TempFileHolder();
        TempFileHolder deltaFriendlyNewFile = new TempFileHolder()) {
      return generatePreDiffPlan(
          oldFile, newFile, deltaFriendlyOldFile, deltaFriendlyNewFile, supportedDeltaFormats);
    }
  }

  private PreDiffPlan generatePreDiffPlan(
      File oldFile,
      File newFile,
      TempFileHolder deltaFriendlyOldFile,
      TempFileHolder deltaFriendlyNewFile,
      Set<DeltaFormat> supportedDeltaFormats)
      throws IOException {
    PreDiffExecutor executor =
        new PreDiffExecutor.Builder()
            .readingOriginalFiles(oldFile, newFile)
            .writingDeltaFriendlyFiles(deltaFriendlyOldFile.file, deltaFriendlyNewFile.file)
            .addPreDiffPlanEntryModifiers(preDiffPlanEntryModifiers)
            .addSupportedDeltaFormats(supportedDeltaFormats)
            .build();

    return executor.prepareForDiffing();
  }

  // Visible for testing only
  protected DeltaGenerator getDeltaGenerator() {
    return new BsDiffDeltaGenerator(useNativeBsDiff);
  }

  private static <T> List<T> getImmutableListCopy(List<T> input) {
    if (input != null) {
      return Collections.unmodifiableList(new ArrayList<>(input));
    } else {
      return Collections.emptyList();
    }
  }

  private static <T> Set<T> getImmutableSetCopy(Set<T> input) {
    if (input != null) {
      return Collections.unmodifiableSet(new HashSet<>(input));
    } else {
      return Collections.emptySet();
    }
  }
}
