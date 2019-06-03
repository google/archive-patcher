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

import static com.google.archivepatcher.shared.bytesource.ByteStreams.copy;

import com.google.archivepatcher.shared.JreDeflateParameters;
import com.google.archivepatcher.shared.PatchConstants;
import com.google.archivepatcher.shared.Range;
import com.google.archivepatcher.shared.TypedRange;
import com.google.archivepatcher.shared.bytesource.ByteSource;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

/**
 * Writes patches.
 */
public class PatchWriter {
  /**
   * The patch plan.
   */
  private final PreDiffPlan plan;

  /**
   * The expected size of the delta-friendly old file, provided as a convenience for the patch
   * <strong>applier</strong> to reserve space on the filesystem for applying the patch.
   */
  private final long deltaFriendlyOldFileSize;

  /** The delta that transforms the old delta-friendly file into the new delta-friendly file. */
  private final List<DeltaEntry> deltaEntries;

  /** The delta-friendly old blob. */
  private final ByteSource oldBlob;
  /** The delta-friendly new blob. */
  private final ByteSource newBlob;
  /** The factory used to obtain delta generators. */
  private final DeltaGeneratorFactory deltaGeneratorFactory;

  /**
   * Creates a new patch writer.
   *
   * @param plan the patch plan
   * @param deltaFriendlyOldFileSize the expected size of the delta-friendly old file, provided as a
   *     convenience for the patch <strong>applier</strong> to reserve space on the filesystem for
   *     applying the patch
   * @param deltaEntries the delta entries
   * @param oldBlob
   * @param newBlob
   * @param deltaGeneratorFactory
   */
  public PatchWriter(
      PreDiffPlan plan,
      long deltaFriendlyOldFileSize,
      List<DeltaEntry> deltaEntries,
      ByteSource oldBlob,
      ByteSource newBlob,
      DeltaGeneratorFactory deltaGeneratorFactory) {
    this.plan = plan;
    this.deltaFriendlyOldFileSize = deltaFriendlyOldFileSize;
    this.deltaEntries = deltaEntries;
    this.oldBlob = oldBlob;
    this.newBlob = newBlob;
    this.deltaGeneratorFactory = deltaGeneratorFactory;
  }

  /**
   * Write a patch to the specified output stream.
   *
   * @param out the stream to write the patch to
   * @throws IOException if anything goes wrong
   */
  public void writePatch(OutputStream out) throws IOException, InterruptedException {
    // Use DataOutputStream for ease of writing. This is deliberately left open, as closing it would
    // close the output stream that was passed in and that is not part of the method's documented
    // behavior.
    @SuppressWarnings("resource")
    DataOutputStream dataOut = new DataOutputStream(out);

    dataOut.write(PatchConstants.IDENTIFIER.getBytes("US-ASCII"));
    dataOut.writeInt(0); // Flags (reserved)
    dataOut.writeLong(deltaFriendlyOldFileSize);

    // Write out all the delta-friendly old file uncompression instructions
    dataOut.writeInt(plan.getOldFileUncompressionPlan().size());
    for (Range range : plan.getOldFileUncompressionPlan()) {
      dataOut.writeLong(range.offset());
      dataOut.writeLong(range.length());
    }

    // Write out all the delta-friendly new file recompression instructions
    dataOut.writeInt(plan.getDeltaFriendlyNewFileRecompressionPlan().size());
    for (TypedRange<JreDeflateParameters> range : plan.getDeltaFriendlyNewFileRecompressionPlan()) {
      dataOut.writeLong(range.offset());
      dataOut.writeLong(range.length());
      // Write the deflate information
      dataOut.write(PatchConstants.CompatibilityWindowId.DEFAULT_DEFLATE.patchValue);
      dataOut.write(range.getMetadata().level);
      dataOut.write(range.getMetadata().strategy);
      dataOut.write(range.getMetadata().nowrap ? 1 : 0);
    }

    // Now the delta section
    // First write the number of deltas present in the patch.
    dataOut.writeInt(deltaEntries.size());

    for (DeltaEntry deltaEntry : deltaEntries) {
      writeDeltaEntry(deltaEntry, oldBlob, newBlob, deltaGeneratorFactory, dataOut);
    }
    dataOut.flush();
  }

  /** Writes the metadata and delta data associated with this entry into the output stream. */
  void writeDeltaEntry(
      DeltaEntry deltaEntry,
      ByteSource oldBlob,
      ByteSource newBlob,
      DeltaGeneratorFactory deltaGeneratorFactory,
      DataOutputStream outputStream)
      throws IOException, InterruptedException {
    outputStream.write(deltaEntry.deltaFormat().patchValue);

    // Write the working ranges.
    outputStream.writeLong(
        deltaEntry
            .oldBlobRange()
            .offset()); // i.e., start of the working range in the delta-friendly old file
    outputStream.writeLong(
        deltaEntry.oldBlobRange().length()); // i.e., length of the working range in old
    outputStream.writeLong(
        deltaEntry
            .newBlobRange()
            .offset()); // i.e., start of the working range in the delta-friendly new file
    outputStream.writeLong(
        deltaEntry.newBlobRange().length()); // i.e., length of the working range in new

    try (ByteSource inputBlobRange = oldBlob.slice(deltaEntry.oldBlobRange());
        ByteSource destBlobRange = newBlob.slice(deltaEntry.newBlobRange());
        TempFileHolder deltaFile = new TempFileHolder()) {
      try (FileOutputStream deltaFileOut = new FileOutputStream(deltaFile.file);
          BufferedOutputStream bufferedDeltaOut = new BufferedOutputStream(deltaFileOut)) {
        DeltaGenerator deltaGenerator = deltaGeneratorFactory.create(deltaEntry.deltaFormat());
        deltaGenerator.generateDelta(inputBlobRange, destBlobRange, bufferedDeltaOut);
      }

      // Finally, the length of the delta and the delta itself.
      outputStream.writeLong(deltaFile.file.length());
      try (FileInputStream deltaIn = new FileInputStream(deltaFile.file)) {
        copy(deltaIn, outputStream);
      }
    }
  }

}

