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
import com.google.archivepatcher.generator.FileByFileV1DeltaGenerator;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * Simple command-line tool for generating and applying patches.
 */
public class FileByFileTool extends AbstractTool {

  /**
   * Usage instructions for the command line.
   */
  private static final String USAGE =
      "java -cp <classpath> com.google.archivepatcher.tools.FileByFileTool <options>\n"
          + "\nOptions:\n"
          + "  --generate      generate a patch\n"
          + "  --apply         apply a patch\n"
          + "  --old           the old file\n"
          + "  --new           the new file\n"
          + "  --patch         the patch file\n"
          + "\nExamples:\n"
          + "  To generate a patch from OLD to NEW, saving the patch in PATCH:\n"
          + "    java -cp <classpath> com.google.archivepatcher.tools.FileByFileTool --generate \\\n"
          + "      --old OLD --new NEW --patch PATCH\n"
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
   * @param args command line arguments
   * @throws IOException if anything goes wrong
   */
  public static void main(String... args) throws IOException {
    new FileByFileTool().run(args);
  }

  /**
   * Run the tool.
   * @param args command line arguments
   * @throws IOException if anything goes wrong
   */
  public void run(String... args) throws IOException {
    String oldPath = null;
    String newPath = null;
    String patchPath = null;
    Mode mode = null;
    Iterator<String> argIterator = new LinkedList<String>(Arrays.asList(args)).iterator();
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
      } else {
        exitWithUsage("unknown argument: " + arg);
      }
    }
    if (oldPath == null || newPath == null || patchPath == null || mode == null) {
      exitWithUsage("missing required argument(s)");
    }
    File oldFile = getRequiredFileOrDie(oldPath, "old file");
    if (mode == Mode.GENERATE) {
      File newFile = getRequiredFileOrDie(newPath, "new file");
      generatePatch(oldFile, newFile, new File(patchPath));
    } else { // mode == Mode.APPLY
      File patchFile = getRequiredFileOrDie(patchPath, "patch file");
      applyPatch(oldFile, patchFile, new File(newPath));
    }
  }

  /**
   * Generate a specified patch to transform the specified old file to the specified new file.
   * @param oldFile the old file (will be read)
   * @param newFile the new file (will be read)
   * @param patchFile the patch file (will be written)
   * @throws IOException if anything goes wrong
   */
  public static void generatePatch(File oldFile, File newFile, File patchFile) throws IOException {
    FileByFileV1DeltaGenerator generator = new FileByFileV1DeltaGenerator();
    try (FileOutputStream patchOut = new FileOutputStream(patchFile);
        BufferedOutputStream bufferedPatchOut = new BufferedOutputStream(patchOut)) {
      generator.generateDelta(oldFile, newFile, bufferedPatchOut);
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
