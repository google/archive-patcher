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

package com.google.archivepatcher.tools;

import com.google.archivepatcher.applier.FileByFileV1DeltaApplier;
import com.google.archivepatcher.generator.DeltaFriendlyOldBlobSizeLimiter;
import com.google.archivepatcher.generator.FileByFileV1DeltaGenerator;
import com.google.archivepatcher.generator.RecommendationModifier;
import com.google.archivepatcher.generator.TotalRecompressionLimiter;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * Simple command-line tool for generating and applying patches.
 */
public class FileByFileTool extends AbstractTool {

  /** Usage instructions for the command line. */
  private static final String USAGE =
      "java -cp <classpath> com.google.archivepatcher.tools.FileByFileTool <options>\n"
          + "\nOptions:\n"
          + "  --generate      generate a patch\n"
          + "  --apply         apply a patch\n"
          + "  --old           the old file\n"
          + "  --new           the new file\n"
          + "  --patch         the patch file\n"
          + "  --trl           optionally, the total bytes of recompression to allow (see below)\n"
          + "  --dfobsl        optionally, a limit on the total size of the delta-friendly old blob (see below)\n"
          + "\nTotal Recompression Limit (trl):\n"
          + "  When generating a patch, a limit can be specified on the total number of bytes to\n"
          + "  allow to be recompressed during the patch apply process. This can be for a variety\n"
          + "  of reasons, with the most obvious being to limit the amount of effort that has to\n"
          + "  be expended applying the patch on the target platform. To properly explain a\n"
          + "  patch that had such a limitation, it is necessary to specify the same limitation\n"
          + "  here. This argument is illegal for --apply, since it only applies to --generate.\n"
          + "\nDelta Friendly Old Blob Size Limit (dfobsl):\n"
          + "  When generating a patch, a limit can be specified on the total size of the delta-\n"
          + "  friendly old blob. This implicitly limits the size of the temporary file that\n"
          + "  needs to be created when applying the patch. The size limit is \"soft\" in that \n"
          + "  the delta-friendly old blob needs to at least contain the original data that was\n"
          + "  within it; but the limit specified here will constrain any attempt to uncompress\n"
          + "  the content. If the limit is less than or equal to the size of the old file, no\n"
          + "  uncompression will be performed at all. Otherwise, the old file can expand into\n"
          + "  delta-friendly old blob until the size reaches this limit.\n"
          + "\nExamples:\n"
          + "  To generate a patch from OLD to NEW, saving the patch in PATCH:\n"
          + "    java -cp <classpath> com.google.archivepatcher.tools.FileByFileTool --generate \\\n"
          + "      --old OLD --new NEW --patch PATCH\n"
          + "  To generate a patch from OLD to NEW, limiting to 1,000,000 recompress bytes:\n"
          + "    java -cp <classpath> com.google.archivepatcher.tools.FileByFileTool --generate \\\n"
          + "      --old OLD --new NEW --trl 1000000 --patch PATCH\n"
          + "  To apply a patch PATCH to OLD, saving the result in NEW:\n"
          + "    java -cp <classpath> com.google.archivepatcher.tools.FileByFileTool --apply \\\n"
          + "      --old OLD --patch PATCH --new NEW";

  /**
   * Modes of operation.
   */
  private static enum Mode {
    /**
     * Generate a patch.
     */
    GENERATE,

    /**
     * Apply a patch.
     */
    APPLY;
  }

  /**
   * Runs the tool. See usage instructions for more information.
   *
   * @param args command line arguments
   * @throws IOException if anything goes wrong
   * @throws InterruptedException if any thread has interrupted the current thread
   */
  public static void main(String... args) throws IOException, InterruptedException {
    new FileByFileTool().run(args);
  }

  /**
   * Run the tool.
   *
   * @param args command line arguments
   * @throws IOException if anything goes wrong
   * @throws InterruptedException if any thread has interrupted the current thread
   */
  public void run(String... args) throws IOException, InterruptedException {
    String oldPath = null;
    String newPath = null;
    String patchPath = null;
    Long totalRecompressionLimit = null;
    Long deltaFriendlyOldBlobSizeLimit = null;
    Mode mode = null;
    Iterator<String> argIterator = new ArrayList<String>(Arrays.asList(args)).iterator();
    while (argIterator.hasNext()) {
      String arg = argIterator.next();
      if ("--old".equals(arg)) {
        oldPath = popOrDie(argIterator, "--old");
      } else if ("--new".equals(arg)) {
        newPath = popOrDie(argIterator, "--new");
      } else if ("--patch".equals(arg)) {
        patchPath = popOrDie(argIterator, "--patch");
      } else if ("--generate".equals(arg)) {
        mode = Mode.GENERATE;
      } else if ("--apply".equals(arg)) {
        mode = Mode.APPLY;
      } else if ("--trl".equals(arg)) {
        totalRecompressionLimit = Long.parseLong(popOrDie(argIterator, "--trl"));
        if (totalRecompressionLimit < 0) {
          exitWithUsage("--trl cannot be negative: " + totalRecompressionLimit);
        }
      } else if ("--dfobsl".equals(arg)) {
        deltaFriendlyOldBlobSizeLimit = Long.parseLong(popOrDie(argIterator, "--dfobsl"));
        if (deltaFriendlyOldBlobSizeLimit < 0) {
          exitWithUsage("--dfobsl cannot be negative: " + deltaFriendlyOldBlobSizeLimit);
        }
      } else {
        exitWithUsage("unknown argument: " + arg);
      }
    }
    if (oldPath == null || newPath == null || patchPath == null || mode == null) {
      exitWithUsage("missing required argument(s)");
    }
    if (mode == Mode.APPLY && totalRecompressionLimit != null) {
      exitWithUsage("--trl can only be used with --generate");
    }
    if (mode == Mode.APPLY && deltaFriendlyOldBlobSizeLimit != null) {
      exitWithUsage("--dfobsl can only be used with --generate");
    }
    File oldFile = getRequiredFileOrDie(oldPath, "old file");
    if (mode == Mode.GENERATE) {
      File newFile = getRequiredFileOrDie(newPath, "new file");
      generatePatch(
          oldFile,
          newFile,
          new File(patchPath),
          totalRecompressionLimit,
          deltaFriendlyOldBlobSizeLimit);
    } else { // mode == Mode.APPLY
      File patchFile = getRequiredFileOrDie(patchPath, "patch file");
      applyPatch(oldFile, patchFile, new File(newPath));
    }
  }

  /**
   * Generate a specified patch to transform the specified old file to the specified new file.
   *
   * @param oldFile the old file (will be read)
   * @param newFile the new file (will be read)
   * @param patchFile the patch file (will be written)
   * @param totalRecompressionLimit optional limit for total number of bytes of recompression to
   *     allow in the resulting patch
   * @param deltaFriendlyOldBlobSizeLimit optional limit for the size of the delta-friendly old
   *     blob, which implies a limit on the temporary space needed to apply the generated patch
   * @throws IOException if anything goes wrong
   * @throws InterruptedException if any thread has interrupted the current thread
   */
  public static void generatePatch(
      File oldFile,
      File newFile,
      File patchFile,
      Long totalRecompressionLimit,
      Long deltaFriendlyOldBlobSizeLimit)
      throws IOException, InterruptedException {
    List<RecommendationModifier> recommendationModifiers = new ArrayList<RecommendationModifier>();
    if (totalRecompressionLimit != null) {
      recommendationModifiers.add(new TotalRecompressionLimiter(totalRecompressionLimit));
    }
    if (deltaFriendlyOldBlobSizeLimit != null) {
      recommendationModifiers.add(
          new DeltaFriendlyOldBlobSizeLimiter(deltaFriendlyOldBlobSizeLimit));
    }
    FileByFileV1DeltaGenerator generator =
        new FileByFileV1DeltaGenerator(
            recommendationModifiers.toArray(new RecommendationModifier[] {}));
    try (FileOutputStream patchOut = new FileOutputStream(patchFile);
        BufferedOutputStream bufferedPatchOut = new BufferedOutputStream(patchOut)) {
      generator.generateDelta(
          oldFile, newFile, bufferedPatchOut, /* generateDeltaNatively= */ false);
      bufferedPatchOut.flush();
    }
  }

  /**
   * Apply a specified patch to the specified old file, creating the specified new file.
   * @param oldFile the old file (will be read)
   * @param patchFile the patch file (will be read)
   * @param newFile the new file (will be written)
   * @throws IOException if anything goes wrong
   */
  public static void applyPatch(File oldFile, File patchFile, File newFile) throws IOException {
    // Figure out temp directory
    File tempFile = File.createTempFile("fbftool", "tmp");
    File tempDir = tempFile.getParentFile();
    tempFile.delete();
    FileByFileV1DeltaApplier applier = new FileByFileV1DeltaApplier(tempDir);
    try (FileInputStream patchIn = new FileInputStream(patchFile);
        BufferedInputStream bufferedPatchIn = new BufferedInputStream(patchIn);
        FileOutputStream newOut = new FileOutputStream(newFile);
        BufferedOutputStream bufferedNewOut = new BufferedOutputStream(newOut)) {
      applier.applyDelta(oldFile, bufferedPatchIn, bufferedNewOut);
      bufferedNewOut.flush();
    }
  }

  @Override
  protected String getUsage() {
    return USAGE;
  }
}
