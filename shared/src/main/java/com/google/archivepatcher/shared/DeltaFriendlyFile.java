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

package com.google.archivepatcher.shared;

import com.google.archivepatcher.shared.bytesource.ByteSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Utilities for generating delta-friendly files.
 */
public class DeltaFriendlyFile {

  /**
   * The default size of the copy buffer to use for copying between streams.
   */
  public static final int DEFAULT_COPY_BUFFER_SIZE = 32768;

  /**
   * Invoke {@link #generateDeltaFriendlyFile(List, ByteSource, OutputStream, boolean, int)} with
   * <code>
   * generateInverse</code> set to <code>true</code> and a copy buffer size of {@link
   * #DEFAULT_COPY_BUFFER_SIZE}.
   *
   * @param <T> the type of the data associated with the ranges
   * @param rangesToUncompress the ranges to be uncompressed during transformation to a
   *     delta-friendly form
   * @param data the original archive
   * @param deltaFriendlyOut a stream to write the delta-friendly file to
   * @return the ranges in the delta-friendly file that correspond to the ranges in the original
   *     file, with identical metadata and in the same order
   * @throws IOException if anything goes wrong
   */
  public static <T> List<TypedRange<T>> generateDeltaFriendlyFile(
      List<TypedRange<T>> rangesToUncompress, ByteSource data, OutputStream deltaFriendlyOut)
      throws IOException {
    return generateDeltaFriendlyFile(
        rangesToUncompress, data, deltaFriendlyOut, true, DEFAULT_COPY_BUFFER_SIZE);
  }

  /**
   * Generate one delta-friendly file and (optionally) return the ranges necessary to invert the
   * transform, in file order. There is a 1:1 correspondence between the ranges in the input list
   * and the returned list, but the offsets and lengths will be different (the input list represents
   * compressed data, the output list represents uncompressed data). The ability to suppress
   * generation of the inverse range and to specify the size of the copy buffer are provided for
   * clients that desire a minimal memory footprint.
   *
   * @param <T> the type of the data associated with the ranges
   * @param rangesToUncompress the ranges to be uncompressed during transformation to a
   *     delta-friendly form
   * @param blob the blob to read from
   * @param deltaFriendlyOut a stream to write the delta-friendly file to
   * @param generateInverse if <code>true</code>, generate and return a list of inverse ranges in
   *     file order; otherwise, do all the normal work but return null instead of the inverse ranges
   * @param copyBufferSize the size of the buffer to use for copying bytes between streams
   * @return if <code>generateInverse</code> was true, returns the ranges in the delta-friendly file
   *     that correspond to the ranges in the original file, with identical metadata and in the same
   *     order; otherwise, return null
   * @throws IOException if anything goes wrong
   */
  public static <T> List<TypedRange<T>> generateDeltaFriendlyFile(
      List<TypedRange<T>> rangesToUncompress,
      ByteSource blob,
      OutputStream deltaFriendlyOut,
      boolean generateInverse,
      int copyBufferSize)
      throws IOException {
    List<TypedRange<T>> inverseRanges = null;
    if (generateInverse) {
      inverseRanges = new ArrayList<TypedRange<T>>(rangesToUncompress.size());
    }
    long lastReadOffset = 0;
    try (PartiallyUncompressingPipe filteredOut =
        new PartiallyUncompressingPipe(deltaFriendlyOut, copyBufferSize)) {
      for (TypedRange<T> rangeToUncompress : rangesToUncompress) {
        long gap = rangeToUncompress.getOffset() - lastReadOffset;
        if (gap > 0) {
          // Copy bytes up to the range start point
          try (InputStream in = blob.slice(lastReadOffset, gap).openStream()) {
            filteredOut.pipe(in, PartiallyUncompressingPipe.Mode.COPY);
          }
        }

        // Now uncompress the range.
        long inverseRangeStart = filteredOut.getNumBytesWritten();
        try (InputStream in =
            blob.slice(rangeToUncompress.getOffset(), rangeToUncompress.getLength()).openStream()) {
          // TODO: Support nowrap=false here? Never encountered in practice.
          // This would involve catching the ZipException, checking if numBytesWritten is still
          // zero,
          // resetting the stream and trying again.
          filteredOut.pipe(in, PartiallyUncompressingPipe.Mode.UNCOMPRESS_NOWRAP);
        }
        lastReadOffset = rangeToUncompress.getOffset() + rangeToUncompress.getLength();

        if (generateInverse) {
          long inverseRangeEnd = filteredOut.getNumBytesWritten();
          long inverseRangeLength = inverseRangeEnd - inverseRangeStart;
          TypedRange<T> inverseRange =
              new TypedRange<T>(
                  inverseRangeStart, inverseRangeLength, rangeToUncompress.getMetadata());
          inverseRanges.add(inverseRange);
        }
      }
      // Finish the final bytes of the file
      long bytesLeft = blob.length() - lastReadOffset;
      if (bytesLeft > 0) {
        try (InputStream in = blob.slice(lastReadOffset, bytesLeft).openStream()) {
          filteredOut.pipe(in, PartiallyUncompressingPipe.Mode.COPY);
        }
      }
    }
    return inverseRanges;
  }
}
