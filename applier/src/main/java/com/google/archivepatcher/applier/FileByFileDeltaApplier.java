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

package com.google.archivepatcher.applier;

import com.google.archivepatcher.applier.bsdiff.BsDiffDeltaApplier;
import com.google.archivepatcher.shared.DeltaFriendlyFile;
import com.google.archivepatcher.shared.PatchConstants.DeltaFormat;
import com.google.archivepatcher.shared.RandomAccessFileOutputStream;
import com.google.archivepatcher.shared.Range;
import com.google.archivepatcher.shared.SafeTempFiles;
import com.google.archivepatcher.shared.bytesource.ByteSource;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Applies patches. */
public class FileByFileDeltaApplier extends DeltaApplier {

  /**
   * Default size of the buffer to use for copying bytes in the recompression stream.
   */
  private static final int DEFAULT_COPY_BUFFER_SIZE = 32768;

  /**
   * The temp directory to use.
   */
  private final File tempDir;

  /**
   * Creates a new delta applier that will use the default temp directory for working files. This is
   * equivalent to calling {@link #FileByFileDeltaApplier(File)} with a <code>null</code> file
   * argument.
   */
  public FileByFileDeltaApplier() {
    this(null);
  }

  /**
   * Creates a new delta applier that will use the specified temp directory.
   *
   * @param tempDir a temp directory where the delta-friendly old blob can be written during the
   *     patch application process; if null, the system's default temporary directory is used
   */
  public FileByFileDeltaApplier(File tempDir) {
    if (tempDir == null) {
      tempDir = new File(System.getProperty("java.io.tmpdir"));
    }
    this.tempDir = tempDir;
  }

  @Override
  public void applyDelta(ByteSource oldBlob, InputStream deltaIn, OutputStream newBlobOut)
      throws IOException {
    if (!tempDir.exists()) {
      // Be nice, try to create the temp directory. Don't bother to check return value as the code
      // will fail when it tries to create the file in a few more lines anyways.
      tempDir.mkdirs();
    }
    File tempFile = SafeTempFiles.createTempFile("gfbfv1", "old", tempDir);
    try {
      applyDeltaInternal(oldBlob, tempFile, deltaIn, newBlobOut);
    } finally {
      tempFile.delete();
    }
  }

  /**
   * Does the work for applying a delta.
   *
   * @param oldBlob the old blob
   * @param deltaFriendlyOldBlob the location in which to store the delta-friendly old blob
   * @param deltaIn the patch stream
   * @param newBlobOut the stream to write the new blob to after applying the delta
   * @throws IOException if anything goes wrong
   */
  private void applyDeltaInternal(
      ByteSource oldBlob, File deltaFriendlyOldBlob, InputStream deltaIn, OutputStream newBlobOut)
      throws IOException {

    // First, read the patch plan from the patch stream.
    PatchApplyPlan plan = PatchReader.readPatchApplyPlan(deltaIn);
    writeDeltaFriendlyOldBlob(plan, oldBlob, deltaFriendlyOldBlob);
    try (ByteSource oldBlobByteSource = ByteSource.fromFile(deltaFriendlyOldBlob)) {
      // Don't close this stream, as it would close the underlying OutputStream (that we don't own).
      @SuppressWarnings("resource")
      PartiallyCompressingOutputStream recompressingNewBlobOut =
          new PartiallyCompressingOutputStream(
              plan.getDeltaFriendlyNewFileRecompressionPlan(),
              newBlobOut,
              DEFAULT_COPY_BUFFER_SIZE);
      // Apply the delta.
      Range previousNewBlobRange = Range.of(0, 0);
      for (int i = 0; i < plan.getNumberOfDeltas(); i++) {
        DeltaDescriptor descriptor = PatchReader.readDeltaDescriptor(deltaIn);

        // Validate that the delta-friendly new blob ranges are contiguous
        // Note that the fact that we interleaved delta-descriptors with delta data means we might
        // be doing wasted work if there is an error in later delta descriptors.
        Range newBlobRange = descriptor.deltaFriendlyNewFileRange();
        if (newBlobRange.offset() != previousNewBlobRange.endOffset()) {
          throw new PatchFormatException(
              String.format(
                  "Gap in delta record. Previous delta-friendly new blob range: %s. Current"
                      + " delta-friendly new blob range: %s",
                  previousNewBlobRange, newBlobRange));
        }
        previousNewBlobRange = newBlobRange;

        DeltaApplier deltaApplier = getDeltaApplier(descriptor.deltaFormat());
        // Don't close this stream, as it is just a limiting wrapper.
        @SuppressWarnings("resource")
        LimitedInputStream limitedDeltaIn =
            new LimitedInputStream(deltaIn, descriptor.deltaLength());
        deltaApplier.applyDelta(
            oldBlobByteSource.slice(descriptor.deltaFriendlyOldFileRange()),
            limitedDeltaIn,
            recompressingNewBlobOut);
      }
      recompressingNewBlobOut.flush();
    }
  }

  /**
   * Writes the delta-friendly old blob to temporary storage.
   *
   * @param plan the plan to use for uncompressing
   * @param oldBlob the blob to turn into a delta-friendly blob
   * @param deltaFriendlyOldBlob where to write the blob
   * @throws IOException if anything goes wrong
   */
  private void writeDeltaFriendlyOldBlob(
      PatchApplyPlan plan, ByteSource oldBlob, File deltaFriendlyOldBlob) throws IOException {
    try (RandomAccessFileOutputStream deltaFriendlyOldFileOut =
        new RandomAccessFileOutputStream(
            deltaFriendlyOldBlob, plan.getDeltaFriendlyOldFileSize())) {
      DeltaFriendlyFile.generateDeltaFriendlyFile(
          plan.getOldFileUncompressionPlan(), oldBlob, deltaFriendlyOldFileOut);
    }
  }

  /**
   * Return an instance of a {@link DeltaApplier} suitable for applying the deltas within the patch
   * stream for the {@link DeltaFormat} given.
   */
  // Visible for testing only
  protected DeltaApplier getDeltaApplier(DeltaFormat deltaFormat) {
    switch (deltaFormat) {
      case BSDIFF:
        return new BsDiffDeltaApplier();
      case FILE_BY_FILE:
        return new FileByFileDeltaApplier(tempDir);
    }
    throw new IllegalArgumentException("Unexpected delta format " + deltaFormat);
  }
}
