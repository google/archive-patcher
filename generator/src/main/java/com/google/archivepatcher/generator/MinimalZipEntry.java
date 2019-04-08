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

import com.google.archivepatcher.shared.PatchConstants.CompressionMethod;
import com.google.archivepatcher.shared.Range;
import com.google.auto.value.AutoValue;
import java.io.UnsupportedEncodingException;

/** A class that contains <em>just enough data</em> to generate a patch. */
@AutoValue
public abstract class MinimalZipEntry {
  /** The compression method that was used, typically 8 (for deflate) or 0 (for stored). */
  public abstract CompressionMethod compressionMethod();

  /** The CRC32 of the <em>uncompressed</em> data. */
  public abstract long crc32OfUncompressedData();

  /** The range of the data as it exists in the archive. */
  public abstract Range compressedDataRange();

  /**
   * The size of the <em>uncompressed</em> data. It will be the same as the length of {@link
   * #compressedDataRange()} if the data is uncompressed.
   */
  public abstract long uncompressedSize();

  /**
   * The file name for the entry. By convention, names ending with '/' denote directories. The
   * encoding is controlled by the general purpose flags, bit 11. See {@link #getFileName()} for
   * more information.
   *
   * <p>DO NOT MODIFY the array returned.
   */
  @SuppressWarnings("mutable")
  public abstract byte[] fileNameBytes();

  /**
   * If we should use UTF8 encoding for interpreting the bytes of the filename.
   *
   * <p>This is obtained from the 11th bit of the general purpose flag, which controls the encoding
   * of file names and comments. See {@link #getFileName()} for more information.
   */
  public abstract boolean useUtf8Encoding();

  /** The range in the original archive corresponding to the local entry header. */
  public abstract Range localEntryRange();

  public static Builder builder() {
    return new AutoValue_MinimalZipEntry.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    private long fileOffsetOfCompressedData = -1;
    private long compressedSize = -1;
    private long fileOffsetOfLocalEntry = -1;
    private long lengthOfLocalEntry = -1;

    /** @see #compressionMethod() */
    abstract Builder compressionMethod(CompressionMethod compressionMethod);

    /** @see #crc32OfUncompressedData() */
    abstract Builder crc32OfUncompressedData(long crc32OfUncompressedData);

    /** @see #uncompressedSize() */
    abstract Builder uncompressedSize(long uncompressedSize);

    /** @see #fileNameBytes() */
    abstract Builder fileNameBytes(byte[] fileNameBytes);

    /** @see #useUtf8Encoding() */
    abstract Builder useUtf8Encoding(boolean useUtf8Encoding);

    /** @see #compressedDataRange() */
    abstract Builder compressedDataRange(Range compressedDataRange);

    /** Offset in original file where compressed data begins. */
    public Builder fileOffsetOfCompressedData(long fileOffsetOfCompressedData) {
      this.fileOffsetOfCompressedData = fileOffsetOfCompressedData;
      return this;
    }

    /** Size of the compressed data. */
    public Builder compressedSize(long compressedSize) {
      this.compressedSize = compressedSize;
      return this;
    }

    /** @see #localEntryRange() */
    abstract Builder localEntryRange(Range localEntryRange);

    /** Offset of local header entry in the original file. */
    public Builder fileOffsetOfLocalEntry(long fileOffsetOfLocalEntry) {
      this.fileOffsetOfLocalEntry = fileOffsetOfLocalEntry;
      return this;
    }

    /**
     * Getter for the {@link #fileOffsetOfLocalEntry()}. We need to access this to generate {@link
     * #fileOffsetOfCompressedData} when parsing the entries.
     */
    public long fileOffsetOfLocalEntry() {
      return fileOffsetOfLocalEntry;
    }

    /** Length of the local header entry. */
    public Builder lengthOfLocalEntry(long lengthOfLocalEntry) {
      this.lengthOfLocalEntry = lengthOfLocalEntry;
      return this;
    }

    abstract MinimalZipEntry autoBuild();

    public MinimalZipEntry build() {
      checkNonNegative(fileOffsetOfLocalEntry, "fileOffsetOfLocalEntry");
      checkNonNegative(lengthOfLocalEntry, "lengthOfLocalEntry");
      checkNonNegative(fileOffsetOfCompressedData, "fileOffsetOfCompressedData");
      checkNonNegative(compressedSize, "compressedSize");

      localEntryRange(Range.of(fileOffsetOfLocalEntry, lengthOfLocalEntry));
      compressedDataRange(Range.of(fileOffsetOfCompressedData, compressedSize));

      return autoBuild();
    }

    private static void checkNonNegative(long value, String name) {
      if (value < 0) {
        throw new IllegalStateException(name + " must be set and non-negative");
      }
    }
  }

  /**
   * Returns a best-effort conversion of the file name into a string, based on strict adherence to
   * the PKWARE APPNOTE that defines this behavior. If the value of the 11th bit of the general
   * purpose flag was set to 1, these bytes should be encoded with the UTF8 character set; otherwise
   * the character set should be Cp437. Adherence to this standard varies significantly, and some
   * systems use the default character set for the environment instead of Cp437 when writing these
   * bytes. For such instances, callers can obtain the raw bytes by using {@link #fileNameBytes()}
   * instead and checking the value of the 11th bit of the general purpose bit flag for a hint using
   * {@link #useUtf8Encoding()}. There is also something called EFS ("0x0008 extra field storage")
   * that specifies additional behavior for character encoding, but this tool doesn't support it as
   * the use is not standardized.
   *
   * @return as described
   */
  // TODO: Support EFS
  public String getFileName() {
    String charsetName = useUtf8Encoding() ? "UTF8" : "Cp437";
    try {
      return new String(fileNameBytes(), charsetName);
    } catch (UnsupportedEncodingException e) {
      // Cp437 has been supported at least since JDK 1.6.0, so this should rarely occur in practice.
      // Older versions of the JDK also support Cp437, but as part of charsets.jar, which didn't
      // ship in every distribution; it is conceivable that those systems might have problems here.
      throw new RuntimeException("System doesn't support " + charsetName, e);
    }
  }
}
