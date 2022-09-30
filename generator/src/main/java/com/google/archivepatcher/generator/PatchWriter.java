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

import com.google.archivepatcher.DeltaEntryDiagnostics;
import com.google.archivepatcher.EntryDeltaFormat;
import com.google.archivepatcher.EntryDeltaFormatExplanation;
import com.google.archivepatcher.File;
import com.google.archivepatcher.UncompressionOption;
import com.google.archivepatcher.UncompressionOptionExplanation;
import com.google.archivepatcher.shared.PatchConstants.DeltaFormat;
import com.google.archivepatcher.shared.Range;
import com.google.archivepatcher.shared.TypedRange;
import com.google.archivepatcher.shared.bytesource.ByteSource;
import com.google.common.annotations.VisibleForTesting;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/** Writes patches. */
public class PatchWriter {
  /** The patch plan. */
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
  public List<DeltaEntryDiagnostics> writePatch(OutputStream out)
      throws IOException, InterruptedException {
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

    List<DeltaEntryDiagnostics> diagnosticsList = new ArrayList<>();

    for (DeltaEntry deltaEntry : deltaEntries) {
      diagnosticsList.add(
          writeDeltaEntry(deltaEntry, oldBlob, newBlob, deltaGeneratorFactory, dataOut));
    }
    dataOut.flush();
    return diagnosticsList;
  }

  /** Writes the metadata and delta data associated with this entry into the output stream. */
  DeltaEntryDiagnostics writeDeltaEntry(
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

    DeltaEntryDiagnostics.Builder diagnostics = DeltaEntryDiagnostics.newBuilder();

    try (ByteSource inputBlobRange = oldBlob.slice(deltaEntry.oldBlobRange());
        ByteSource destBlobRange = newBlob.slice(deltaEntry.newBlobRange());
        TempBlob deltaFile = new TempBlob()) {
      try (OutputStream bufferedDeltaOut = deltaFile.openBufferedStream()) {
        DeltaGenerator deltaGenerator = deltaGeneratorFactory.create(deltaEntry.deltaFormat());
        diagnostics.addAllChildren(
            deltaGenerator.generateDeltaWithDiagnostics(
                inputBlobRange, destBlobRange, bufferedDeltaOut));
      }

      // Finally, the length of the delta and the delta itself.
      outputStream.writeLong(deltaFile.length());

      diagnostics.setTotalPatchSize(deltaFile.length());
      getDiagnostics(deltaEntry, diagnostics);

      try (ByteSource deltaSource = deltaFile.asByteSource();
          InputStream deltaIn = deltaSource.openStream()) {
        copy(deltaIn, outputStream);
      }
      return diagnostics.build();
    }
  }

  public void getDiagnostics(DeltaEntry deltaEntry, DeltaEntryDiagnostics.Builder diagnostics) {
    if (deltaEntry.deltaFormat() == DeltaFormat.BSDIFF) {
      diagnostics.setDeltaFormat(EntryDeltaFormat.DF_BSDIFF);
    } else if (deltaEntry.deltaFormat() == DeltaFormat.FILE_BY_FILE) {
      diagnostics.setDeltaFormat(EntryDeltaFormat.DF_FILE_BY_FILE);
    }

    for (DiffPlanEntry diffPlanEntry : deltaEntry.diffPlanEntries()) {
      PreDiffPlanEntry preDiffPlanEntry = diffPlanEntry.preDiffPlanEntry();
      File.Builder file = File.newBuilder();
      file.setOriginalFilename(preDiffPlanEntry.oldEntry().getFileName());
      file.setNewFilename(preDiffPlanEntry.newEntry().getFileName());
      file.setOriginalFileSize(preDiffPlanEntry.oldEntry().uncompressedSize());
      file.setNewFileSize(preDiffPlanEntry.newEntry().uncompressedSize());

      switch (preDiffPlanEntry.zipEntryUncompressionOption()) {
        case UNCOMPRESS_OLD:
          file.setUncompressionOption(UncompressionOption.UO_UNCOMPRESS_OLD);
          break;
        case UNCOMPRESS_NEW:
          file.setUncompressionOption(UncompressionOption.UO_UNCOMPRESS_NEW);
          break;
        case UNCOMPRESS_BOTH:
          file.setUncompressionOption(UncompressionOption.UO_UNCOMPRESS_BOTH);
          break;
        case UNCOMPRESS_NEITHER:
          file.setUncompressionOption(UncompressionOption.UO_UNCOMPRESS_NEITHER);
          break;
      }

      switch (preDiffPlanEntry.uncompressionOptionExplanation()) {
        case DEFLATE_UNSUITABLE:
          file.setUncompressionOptionExplanation(
              UncompressionOptionExplanation.UOE_DEFLATE_UNSUITABLE);
          break;
        case UNSUITABLE:
          file.setUncompressionOptionExplanation(UncompressionOptionExplanation.UOE_UNSUITABLE);
          break;
        case BOTH_ENTRIES_UNCOMPRESSED:
          file.setUncompressionOptionExplanation(
              UncompressionOptionExplanation.UOE_BOTH_ENTRIES_UNCOMPRESSED);
          break;
        case UNCOMPRESSED_CHANGED_TO_COMPRESSED:
          file.setUncompressionOptionExplanation(
              UncompressionOptionExplanation.UOE_UNCOMPRESSED_CHANGED_TO_COMPRESSED);
          break;
        case COMPRESSED_CHANGED_TO_UNCOMPRESSED:
          file.setUncompressionOptionExplanation(
              UncompressionOptionExplanation.UOE_COMPRESSED_CHANGED_TO_UNCOMPRESSED);
          break;
        case COMPRESSED_BYTES_CHANGED:
          file.setUncompressionOptionExplanation(
              UncompressionOptionExplanation.UOE_COMPRESSED_BYTES_CHANGED);
          break;
        case COMPRESSED_BYTES_IDENTICAL:
          file.setUncompressionOptionExplanation(
              UncompressionOptionExplanation.UOE_COMPRESSED_BYTES_IDENTICAL);
          break;
        case RESOURCE_CONSTRAINED:
          file.setUncompressionOptionExplanation(
              UncompressionOptionExplanation.UOE_RESOURCE_CONSTRAINED);
          break;
      }

      switch (preDiffPlanEntry.deltaFormatExplanation()) {
        case DEFAULT:
          file.setDeltaFormatExplanation(EntryDeltaFormatExplanation.DFE_DEFAULT);
          break;
        case FILE_TYPE:
          file.setDeltaFormatExplanation(EntryDeltaFormatExplanation.DFE_FILE_TYPE);
          break;
        case UNSUITABLE:
          file.setDeltaFormatExplanation(EntryDeltaFormatExplanation.DFE_UNSUITABLE);
          break;
        case DEFLATE_UNSUITABLE:
          file.setDeltaFormatExplanation(EntryDeltaFormatExplanation.DFE_DEFLATE_UNSUITABLE);
          break;
        case UNCHANGED:
          file.setDeltaFormatExplanation(EntryDeltaFormatExplanation.DFE_UNCHANGED);
          break;
        case RESOURCE_CONSTRAINED:
          file.setDeltaFormatExplanation(EntryDeltaFormatExplanation.DFE_RESOURCE_CONSTRAINED);
          break;
      }

      diagnostics.addFiles(file.build());
    }
  }
}

