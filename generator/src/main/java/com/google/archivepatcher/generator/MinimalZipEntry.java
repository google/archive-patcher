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
import com.google.auto.value.AutoValue;
import java.io.UnsupportedEncodingException;

/** A class that contains <em>just enough data</em> to generate a patch. */
@AutoValue
public abstract class MinimalZipEntry {
  /** The compression method that was used, typically 8 (for deflate) or 0 (for stored). */
  public abstract CompressionMethod compressionMethod();

  /** The CRC32 of the <em>uncompressed</em> data. */
  public abstract long crc32OfUncompressedData();

  /**
   * The size of the data as it exists in the archive. For compressed entries, this is the size of
   * the compressed data; for uncompressed entries, this is the same as {@link #uncompressedSize}.
   */
  public abstract long compressedSize();

  /** The size of the <em>uncompressed</em data. */
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
   * The value of the 11th bit of the general purpose flag, which controls the encoding of file
   * names and comments. See {@link #getFileName()} for more information.
   */
  public abstract boolean generalPurposeFlagBit11();

  /** The file offset at which the first byte of the local entry header begins. */
  public abstract long fileOffsetOfLocalEntry();

  /**
   * The file offset at which the first byte of the data for the entry begins. For compressed data,
   * this is the first byte of the deflated data; for uncompressed data, this is the first byte of
   * the uncompressed data.
   */
  public abstract long fileOffsetOfCompressedData();

  public static Builder builder() {
    return new AutoValue_MinimalZipEntry.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    /** @see #compressionMethod() */
    abstract Builder compressionMethod(CompressionMethod compressionMethod);

    /** @see #crc32OfUncompressedData() */
    abstract Builder crc32OfUncompressedData(long crc32OfUncompressedData);

    /** @see #compressedSize() */
    abstract Builder compressedSize(long compressedSize);

    /** @see #uncompressedSize() */
    abstract Builder uncompressedSize(long uncompressedSize);

    /** @see #fileNameBytes() */
    abstract Builder fileNameBytes(byte[] fileNameBytes);

    /** @see #generalPurposeFlagBit11() */
    abstract Builder generalPurposeFlagBit11(boolean generalPurposeFlagBit11);

    /** @see #fileOffsetOfLocalEntry() */
    abstract Builder fileOffsetOfLocalEntry(long fileOffsetOfLocalEntry);

    /**
     * Getter for the {@link #fileOffsetOfLocalEntry()}. We need this to generate {@link
     * #fileOffsetOfCompressedData()} when parsing the entries..
     */
    public abstract long fileOffsetOfLocalEntry();

    /** @see #fileOffsetOfCompressedData() */
    abstract Builder fileOffsetOfCompressedData(long fileOffsetOfCompressedData);

    abstract MinimalZipEntry build();
  }

  /**
   * Returns a best-effort conversion of the file name into a string, based on strict adherence to
   * the PKWARE APPNOTE that defines this behavior. If the value of the 11th bit of the general
   * purpose flag was set to 1, these bytes should be encoded with the UTF8 character set; otherwise
   * the character set should be Cp437. Adherence to this standard varies significantly, and some
   * systems use the default character set for the environment instead of Cp437 when writing these
   * bytes. For such instances, callers can obtain the raw bytes by using {@link #fileNameBytes()}
   * instead and checking the value of the 11th bit of the general purpose bit flag for a hint using
   * {@link #generalPurposeFlagBit11()}. There is also something called EFS ("0x0008 extra field
   * storage") that specifies additional behavior for character encoding, but this tool doesn't
   * support it as the use is not standardized.
   *
   * @return as described
   */
  // TODO: Support EFS
  public String getFileName() {
    String charsetName = generalPurposeFlagBit11() ? "UTF8" : "Cp437";
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
