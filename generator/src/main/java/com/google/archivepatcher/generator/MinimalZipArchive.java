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

import com.google.archivepatcher.shared.bytesource.ByteSource;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipException;

/**
 * A simplified, structural representation of a zip or zip-like (jar, apk, etc) archive. The class
 * provides the minimum structural information needed for patch generation and is not suitable as a
 * general zip-processing library. In particular, there is little or no verification that any of the
 * zip structure is correct or sane; it assumed that the input is sane.
 */
public class MinimalZipArchive {

  /**
   * Sorts {@link MinimalZipEntry} objects by {@link MinimalZipEntry#fileOffsetOfLocalEntry()} in
   * ascending order.
   */
  private static final Comparator<MinimalZipEntry.Builder> LOCAL_ENTRY_OFFSET_COMAPRATOR =
      (o1, o2) -> Long.compare(o1.fileOffsetOfLocalEntry(), o2.fileOffsetOfLocalEntry());

  /**
   * Generate a listing of all of the files in a zip archive in file order and return it. Each entry
   * is a {@link MinimalZipEntry}, which has just enough information to generate a patch.
   * @param file the zip file to read
   * @return such a listing
   * @throws IOException if anything goes wrong while reading
   */
  public static List<MinimalZipEntry> listEntries(File file) throws IOException {
    try (ByteSource byteSource = ByteSource.fromFile(file)) {
      return listEntries(byteSource);
    }
  }

  /**
   * Generate a listing of all of the files in a zip archive in file order and return it. Each entry
   * is a {@link MinimalZipEntry}, which has just enough information to generate a patch.
   *
   * @param data the zip file to read
   * @return such a listing
   * @throws IOException if anything goes wrong while reading
   */
  public static List<MinimalZipEntry> listEntries(ByteSource data) throws IOException {
    // Step 1: Locate the end-of-central-directory record header.
    long offsetOfEocd = MinimalZipParser.locateStartOfEocd(data, 32768);
    if (offsetOfEocd == -1) {
      // Archive is weird, abort.
      throw new ZipException("EOCD record not found in last 32k of archive, giving up");
    }

    // Step 2: Parse the end-of-central-directory data to locate the central directory itself
    MinimalCentralDirectoryMetadata centralDirectoryMetadata;
    try (InputStream inputStream = data.sliceFrom(offsetOfEocd).openStream()) {
      centralDirectoryMetadata = MinimalZipParser.parseEocd(inputStream);
    }

    // Step 3: Extract a list of all central directory entries (contiguous data stream)
    List<MinimalZipEntry.Builder> partialMinimalZipEntries =
        new ArrayList<>(centralDirectoryMetadata.getNumEntriesInCentralDirectory());
    try (InputStream inputStream =
        data.slice(
                centralDirectoryMetadata.getOffsetOfCentralDirectory(),
                centralDirectoryMetadata.getLengthOfCentralDirectory())
            .openStream()) {
      for (int x = 0; x < centralDirectoryMetadata.getNumEntriesInCentralDirectory(); x++) {
        partialMinimalZipEntries.add(MinimalZipParser.parseCentralDirectoryEntry(inputStream));
      }
    }

    // Step 4: Sort the entries in file order, not central directory order.
    Collections.sort(partialMinimalZipEntries, LOCAL_ENTRY_OFFSET_COMAPRATOR);

    // Step 5: Seek out each local entry and calculate the offset of the compressed data within
    List<MinimalZipEntry> minimalZipEntries = new ArrayList<>(partialMinimalZipEntries.size());
    for (int x = 0; x < partialMinimalZipEntries.size(); x++) {
      MinimalZipEntry.Builder entryBuilder = partialMinimalZipEntries.get(x);
      long offsetOfNextEntry;
      if (x < partialMinimalZipEntries.size() - 1) {
        // Don't allow reading past the start of the next entry, for sanity.
        offsetOfNextEntry = partialMinimalZipEntries.get(x + 1).fileOffsetOfLocalEntry();
      } else {
        // Last entry. Don't allow reading into the central directory, for sanity.
        offsetOfNextEntry = centralDirectoryMetadata.getOffsetOfCentralDirectory();
      }
      long rangeLength = offsetOfNextEntry - entryBuilder.fileOffsetOfLocalEntry();
      entryBuilder.lengthOfLocalEntry(rangeLength);
      try (InputStream inputStream =
          data.slice(entryBuilder.fileOffsetOfLocalEntry(), rangeLength).openStream()) {
        long relativeDataOffset =
            MinimalZipParser.parseLocalEntryAndGetCompressedDataOffset(inputStream);
        entryBuilder.fileOffsetOfCompressedData(
            entryBuilder.fileOffsetOfLocalEntry() + relativeDataOffset);
      }
      minimalZipEntries.add(entryBuilder.build());
    }

    // Done!
    return minimalZipEntries;
  }
}
