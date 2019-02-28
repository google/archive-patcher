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

import java.io.UnsupportedEncodingException;
import java.util.Arrays;

/**
 * A class that contains <em>just enough data</em> to generate a patch.
 */
public class MinimalZipEntry {
  /**
   * The compression method that was used, typically 8 (for deflate) or 0 (for stored).
   */
  private final int compressionMethod;

  /**
   * The CRC32 of the <em>uncompressed</em> data.
   */
  private final long crc32OfUncompressedData;

  /**
   * The size of the data as it exists in the archive. For compressed entries, this is the size of
   * the compressed data; for uncompressed entries, this is the same as {@link #uncompressedSize}.
   */
  private final long compressedSize;

  /**
   * The size of the <em>uncompressed</em data.
   */
  private final long uncompressedSize;

  /**
   * The file name for the entry. By convention, names ending with '/' denote directories. The
   * encoding is controlled by the general purpose flags, bit 11. See {@link #getFileName()} for
   * more information.
   */
  private final byte[] fileNameBytes;

  /**
   * The value of the 11th bit of the general purpose flag, which controls the encoding of file
   * names and comments. See {@link #getFileName()} for more information.
   */
  private final boolean generalPurposeFlagBit11;

  /**
   * The file offset at which the first byte of the local entry header begins.
   */
  private final long fileOffsetOfLocalEntry;

  /**
   * The file offset at which the first byte of the data for the entry begins. For compressed data,
   * this is the first byte of the deflated data; for uncompressed data, this is the first byte of
   * the uncompressed data.
   */
  private long fileOffsetOfCompressedData = -1;

  /**
   * Create a new Central Directory entry with the corresponding data.
   * @param compressionMethod the method used to compress the data
   * @param crc32OfUncompressedData the CRC32 of the uncompressed data
   * @param compressedSize the size of the data in its compressed form
   * @param uncompressedSize the size of the data in its uncompressed form
   * @param fileNameBytes the name of the file, as a byte array; see {@link #getFileName()} for
   * information on encoding
   * @param generalPurposeFlagBit11 the value of the 11th bit of the general purpose flag, which
   * nominally controls the default character encoding for file names and comments; see
   * {@link #getFileName()} for more information on encoding
   * @param fileOffsetOfLocalEntry the file offset at which the local entry begins
   */
  public MinimalZipEntry(
      int compressionMethod,
      long crc32OfUncompressedData,
      long compressedSize,
      long uncompressedSize,
      byte[] fileNameBytes,
      boolean generalPurposeFlagBit11,
      long fileOffsetOfLocalEntry) {
    this.compressionMethod = compressionMethod;
    this.crc32OfUncompressedData = crc32OfUncompressedData;
    this.compressedSize = compressedSize;
    this.uncompressedSize = uncompressedSize;
    this.fileNameBytes = fileNameBytes == null ? null : fileNameBytes.clone();
    this.generalPurposeFlagBit11 = generalPurposeFlagBit11;
    this.fileOffsetOfLocalEntry = fileOffsetOfLocalEntry;
  }

  /**
   * Sets the file offset at which the data for this entry begins.
   * @param offset the offset
   */
  public void setFileOffsetOfCompressedData(long offset) {
    fileOffsetOfCompressedData = offset;
  }

  /**
   * Returns the compression method that was used, typically 8 (for deflate) or 0 (for stored).
   * @return as described
   */
  public int getCompressionMethod() {
    return compressionMethod;
  }

  /**
   * Returns the CRC32 of the uncompressed data.
   * @return as described
   */
  public long getCrc32OfUncompressedData() {
    return crc32OfUncompressedData;
  }

  /**
   * Returns the size of the data as it exists in the archive. For compressed entries, this is the
   * size of the compressed data; for uncompressed entries, this is the same as
   * {@link #getUncompressedSize()}.
   * @return as described
   */
  public long getCompressedSize() {
    return compressedSize;
  }

  /**
   * Returns the size of the uncompressed data.
   * @return as described
   */
  public long getUncompressedSize() {
    return uncompressedSize;
  }

  /**
   * Returns a copy of the bytes of the file name, exactly the same as they were in the archive
   * file. See {@link #getFileName()} for an explanation of why this is useful.
   * @return as described
   */
  public byte[] getFileNameBytes() {
    return fileNameBytes == null ? null : fileNameBytes.clone();
  }

  /**
   * Returns a best-effort conversion of the file name into a string, based on strict adherence to
   * the PKWARE APPNOTE that defines this behavior. If the value of the 11th bit of the general
   * purpose flag was set to 1, these bytes should be encoded with the UTF8 character set; otherwise
   * the character set should be Cp437. Adherence to this standard varies significantly, and some
   * systems use the default character set for the environment instead of Cp437 when writing these
   * bytes. For such instances, callers can obtain the raw bytes by using
   * {@link #getFileNameBytes()} instead and checking the value of the 11th bit of the general
   * purpose bit flag for a hint using {@link #getGeneralPurposeFlagBit11()}. There is also
   * something called EFS ("0x0008 extra field storage") that specifies additional behavior for
   * character encoding, but this tool doesn't support it as the use is not standardized.
   * @return as described
   */
  // TODO: Support EFS
  public String getFileName() {
    String charsetName = generalPurposeFlagBit11 ? "UTF8" : "Cp437";
    try {
      return new String(fileNameBytes, charsetName);
    } catch (UnsupportedEncodingException e) {
      // Cp437 has been supported at least since JDK 1.6.0, so this should rarely occur in practice.
      // Older versions of the JDK also support Cp437, but as part of charsets.jar, which didn't
      // ship in every distribution; it is conceivable that those systems might have problems here.
      throw new RuntimeException("System doesn't support " + charsetName, e);
    }
  }

  /**
   * Returns the value of the 11th bit of the general purpose flag; true for 1, false for 0. See
   * {@link #getFileName()} for more information on the usefulness of this flag.
   * @return as described
   */
  public boolean getGeneralPurposeFlagBit11() {
    return generalPurposeFlagBit11;
  }

  /**
   * Returns the file offset at which the first byte of the local entry header begins.
   * @return as described
   */
  public long getFileOffsetOfLocalEntry() {
    return fileOffsetOfLocalEntry;
  }

  /**
   * Returns the file offset at which the first byte of the data for the entry begins. For
   * compressed data, this is the first byte of the deflated data; for uncompressed data, this is
   * the first byte of the uncompressed data.
   * @return as described
   */
  public long getFileOffsetOfCompressedData() {
    return fileOffsetOfCompressedData;
  }

  /**
   * Convenience methods that returns true if and only if the entry is compressed with deflate.
   * @return as described
   */
  public boolean isDeflateCompressed() {
    // 8 is deflate according to the zip spec.
    if (getCompressionMethod() != 8) {
      return false;
    }
    // Some tools may list compression method deflate but set level to zero (store), so they will
    // have a compressed size equal to the uncompresesd size. Don't consider such things to be
    // compressed, even if they are "deflated".
    return getCompressedSize() != getUncompressedSize();
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + (int) (compressedSize ^ (compressedSize >>> 32));
    result = prime * result + compressionMethod;
    result = prime * result + (int) (crc32OfUncompressedData ^ (crc32OfUncompressedData >>> 32));
    result = prime * result + Arrays.hashCode(fileNameBytes);
    result =
        prime * result + (int) (fileOffsetOfCompressedData ^ (fileOffsetOfCompressedData >>> 32));
    result = prime * result + (int) (fileOffsetOfLocalEntry ^ (fileOffsetOfLocalEntry >>> 32));
    result = prime * result + (generalPurposeFlagBit11 ? 1231 : 1237);
    result = prime * result + (int) (uncompressedSize ^ (uncompressedSize >>> 32));
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    MinimalZipEntry other = (MinimalZipEntry) obj;
    if (compressedSize != other.compressedSize) {
      return false;
    }
    if (compressionMethod != other.compressionMethod) {
      return false;
    }
    if (crc32OfUncompressedData != other.crc32OfUncompressedData) {
      return false;
    }
    if (!Arrays.equals(fileNameBytes, other.fileNameBytes)) {
      return false;
    }
    if (fileOffsetOfCompressedData != other.fileOffsetOfCompressedData) {
      return false;
    }
    if (fileOffsetOfLocalEntry != other.fileOffsetOfLocalEntry) {
      return false;
    }
    if (generalPurposeFlagBit11 != other.generalPurposeFlagBit11) {
      return false;
    }
    if (uncompressedSize != other.uncompressedSize) {
      return false;
    }
    return true;
  }
}
