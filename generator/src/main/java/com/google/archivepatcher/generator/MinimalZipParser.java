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

import com.google.archivepatcher.shared.RandomAccessFileInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipException;

/**
 * A minimal set of zip-parsing utilities just adequate to produce a {@link MinimalZipEntry} and
 * update it. This parser is neither robust nor exhaustive. The parser is built to understand
 * version 2.0 of the ZIP specification, with the notable exception that it does not have support
 * for encrypted central directories.
 * <p>
 * The offsets, lengths and fields that this parser understands and exposes are based on version
 * 6.3.3 of the ZIP specification (the most recent available at the time of this writing), which may
 * be found at the following URL:
 * <br><ul><li>https://www.pkware.com/documents/APPNOTE/APPNOTE-6.3.3.TXT</li></ul>
 * <p>
 * Please note that the parser does not attempt to verify the version-needed-to-extract field, since
 * there is no guarantee that all ZIP implementations have set the value correctly to the minimum
 * needed to truly support extraction.
 */
class MinimalZipParser {

  /**
   * Standard 32-bit signature for a "end-of-central-directory" record in a ZIP-like archive. This
   * is in little-endian order.
   */
  public static final int EOCD_SIGNATURE = 0x06054b50;

  /**
   * Standard 32-bit signature for a "central directory entry" record in a ZIP-like archive. This is
   * in little-endian order.
   */
  public static final int CENTRAL_DIRECTORY_ENTRY_SIGNATURE = 0x02014b50;

  /**
   * Standard 32-bit signature for a "local file entry" in a ZIP-like archive. This is in
   * little-endian order.
   */
  public static final int LOCAL_ENTRY_SIGNATURE = 0x04034b50;

  /**
   * Read exactly one byte, throwing an exception if unsuccessful.
   * @param in the stream to read from
   * @return the byte read
   * @throws IOException if EOF is reached
   */
  private static int readByteOrDie(InputStream in) throws IOException {
    int result = in.read();
    if (result == -1) {
      throw new IOException("EOF");
    }
    return result;
  }

  /**
   * Skips exactly the specified number of bytes, throwing an exception if unsuccessful.
   * @param in the stream to read from
   * @param numBytes the number of bytes to skip
   * @throws IOException if EOF is reached or no more bytes can be skipped
   */
  private static void skipOrDie(InputStream in, long numBytes) throws IOException {
    long numLeft = numBytes;
    long numSkipped = 0;
    while ((numSkipped = in.skip(numLeft)) > 0) {
      numLeft -= numSkipped;
    }
    if (numLeft != 0) {
      throw new IOException("Unable to skip");
    }
  }

  /**
   * Reads 2 bytes from the current offset as an unsigned, 32-bit little-endian value.
   * @param in the stream to read from
   * @return the value as a java int
   * @throws IOException if unable to read
   */
  private static int read16BitUnsigned(InputStream in) throws IOException {
    int value = readByteOrDie(in);
    value |= readByteOrDie(in) << 8;
    return value;
  }

  /**
   * Reads 4 bytes from the current offset as an unsigned, 32-bit little-endian value.
   * @param in the stream to read from
   * @return the value as a java long
   * @throws IOException if unable to read
   */
  private static long read32BitUnsigned(InputStream in) throws IOException {
    long value = readByteOrDie(in);
    value |= ((long) readByteOrDie(in)) << 8;
    value |= ((long) readByteOrDie(in)) << 16;
    value |= ((long) readByteOrDie(in)) << 24;
    return value;
  }

  /**
   * Read exactly the specified amount of data into the specified buffer, throwing an exception if
   * unsuccessful.
   * @param in the stream to read from
   * @param buffer the buffer to file
   * @param offset the offset at which to start writing to the buffer
   * @param length the number of bytes to place into the buffer from the input stream
   * @throws IOException if unable to read
   */
  private static void readOrDie(InputStream in, byte[] buffer, int offset, int length)
      throws IOException {
    if (length < 0) {
      throw new IllegalArgumentException("length must be >= 0");
    }
    int numRead = 0;
    while (numRead < length) {
      int readThisRound = in.read(buffer, offset + numRead, length - numRead);
      if (numRead == -1) {
        throw new IOException("EOF");
      }
      numRead += readThisRound;
    }
  }

  /**
   * Parse one central directory entry, starting at the current file position.
   * @param in the input stream to read from, assumed to start at the first byte of the entry
   * @return the entry that was parsed
   * @throws IOException if unable to complete the parsing
   */
  public static MinimalZipEntry parseCentralDirectoryEntry(InputStream in) throws IOException {
    // *** 4 bytes encode the CENTRAL_DIRECTORY_ENTRY_SIGNATURE, verify for sanity
    // 2 bytes encode the version-made-by, ignore
    // 2 bytes encode the version-needed-to-extract, ignore
    // *** 2 bytes encode the general-purpose flags, read for language encoding. [READ THIS]
    // *** 2 bytes encode the compression method, [READ THIS]
    // 2 bytes encode the MSDOS last modified file time, ignore
    // 2 bytes encode the MSDOS last modified file date, ignore
    // *** 4 bytes encode the CRC32 of the uncompressed data [READ THIS]
    // *** 4 bytes encode the compressed size [READ THIS]
    // *** 4 bytes encode the uncompressed size [READ THIS]
    // *** 2 bytes encode the length of the file name [READ THIS]
    // *** 2 bytes encode the length of the extras, needed to skip the bytes later [READ THIS]
    // *** 2 bytes encode the length of the comment, needed to skip the bytes later [READ THIS]
    // 2 bytes encode the disk number, ignore
    // 2 bytes encode the internal file attributes, ignore
    // 4 bytes encode the external file attributes, ignore
    // *** 4 bytes encode the offset of the local section entry, where the data is [READ THIS]
    // n bytes encode the file name
    // n bytes encode the extras
    // n bytes encode the comment
    if (((int) read32BitUnsigned(in)) != CENTRAL_DIRECTORY_ENTRY_SIGNATURE) {
      throw new ZipException("Bad central directory header");
    }
    skipOrDie(in, 2 + 2); // Skip version stuff
    int generalPurposeFlags = read16BitUnsigned(in);
    int compressionMethod = read16BitUnsigned(in);
    skipOrDie(in, 2 + 2); // Skip MSDOS junk
    long crc32OfUncompressedData = read32BitUnsigned(in);
    long compressedSize = read32BitUnsigned(in);
    long uncompressedSize = read32BitUnsigned(in);
    int fileNameLength = read16BitUnsigned(in);
    int extrasLength = read16BitUnsigned(in);
    int commentLength = read16BitUnsigned(in);
    skipOrDie(in, 2 + 2 + 4); // Skip the disk number and file attributes
    long fileOffsetOfLocalEntry = read32BitUnsigned(in);
    byte[] fileNameBuffer = new byte[fileNameLength];
    readOrDie(in, fileNameBuffer, 0, fileNameBuffer.length);
    skipOrDie(in, extrasLength + commentLength);
    // General purpose flag bit 11 is an important hint for the character set used for file names.
    boolean generalPurposeFlagBit11 = (generalPurposeFlags & (0x1 << 10)) != 0;
    return new MinimalZipEntry(
        compressionMethod,
        crc32OfUncompressedData,
        compressedSize,
        uncompressedSize,
        fileNameBuffer,
        generalPurposeFlagBit11,
        fileOffsetOfLocalEntry);
  }

  /**
   * Parses one local file entry and returns the offset from the first byte at which the compressed
   * data begins
   * @param in the input stream to read from, assumed to start at the first byte of the entry
   * @return as described
   * @throws IOException if unable to complete the parsing
   */
  public static long parseLocalEntryAndGetCompressedDataOffset(InputStream in) throws IOException {
    // *** 4 bytes encode the LOCAL_ENTRY_SIGNATURE, verify for sanity
    // 2 bytes encode the version-needed-to-extract, ignore
    // 2 bytes encode the general-purpose flags, ignore
    // 2 bytes encode the compression method, ignore (redundant with central directory)
    // 2 bytes encode the MSDOS last modified file time, ignore
    // 2 bytes encode the MSDOS last modified file date, ignore
    // 4 bytes encode the CRC32 of the uncompressed data, ignore (redundant with central directory)
    // 4 bytes encode the compressed size, ignore (redundant with central directory)
    // 4 bytes encode the uncompressed size, ignore (redundant with central directory)
    // *** 2 bytes encode the length of the file name, needed to skip the bytes later [READ THIS]
    // *** 2 bytes encode the length of the extras, needed to skip the bytes later [READ THIS]
    // The rest is the data, which is the main attraction here.
    if (((int) read32BitUnsigned(in)) != LOCAL_ENTRY_SIGNATURE) {
      throw new ZipException("Bad local entry header");
    }
    int junkLength = 2 + 2 + 2 + 2 + 2 + 4 + 4 + 4;
    skipOrDie(in, junkLength); // Skip everything up to the length of the file name
    final int fileNameLength = read16BitUnsigned(in);
    final int extrasLength = read16BitUnsigned(in);

    // The file name is already known and will match the central directory, so no need to read it.
    // The extra field length can be different here versus in the central directory and is used for
    // things like zipaligning APKs. This single value is the critical part as it dictates where the
    // actual DATA for the entry begins.
    return 4 + junkLength + 2 + 2 + fileNameLength + extrasLength;
  }

  /**
   * Find the end-of-central-directory record by scanning backwards from the end of a file looking
   * for the signature of the record.
   * @param in the file to read from
   * @param searchBufferLength the length of the search buffer, starting from the end of the file
   * @return the offset in the file at which the first byte of the EOCD signature is located, or -1
   * if the signature is not found in the search buffer
   * @throws IOException if there is a problem reading
   */
  public static long locateStartOfEocd(RandomAccessFileInputStream in, int searchBufferLength)
      throws IOException {
    final int maxBufferSize = (int) Math.min(searchBufferLength, in.length());
    final byte[] buffer = new byte[maxBufferSize];
    final long rangeStart = in.length() - buffer.length;
    in.setRange(rangeStart, buffer.length);
    readOrDie(in, buffer, 0, buffer.length);
    int offset = locateStartOfEocd(buffer);
    if (offset == -1) {
      return -1;
    }
    return rangeStart + offset;
  }

  /**
   * Find the end-of-central-directory record by scanning backwards looking for the signature of the
   * record.
   * @param buffer the buffer in which to search
   * @return the offset in the buffer at which the first byte of the EOCD signature is located, or
   * -1 if the complete signature is not found
   */
  public static int locateStartOfEocd(byte[] buffer) {
    int last4Bytes = 0; // This is the 32 bits of data from the file
    for (int offset = buffer.length - 1; offset >= 0; offset--) {
      last4Bytes <<= 8;
      last4Bytes |= buffer[offset];
      if (last4Bytes == EOCD_SIGNATURE) {
        return offset;
      }
    }
    return -1;
  }

  /**
   * Parse the end-of-central-directory record and return the critical information from it.
   * @param in the input stream to read from, assumed to start at the first byte of the entry
   * @return the metadata
   * @throws IOException if unable to read
   * @throws ZipException if the metadata indicates this is a zip64 archive, which is not supported
   */
  public static MinimalCentralDirectoryMetadata parseEocd(InputStream in)
      throws IOException, ZipException {
    if (((int) read32BitUnsigned(in)) != EOCD_SIGNATURE) {
      throw new ZipException("Bad eocd header");
    }

    // *** 4 bytes encode EOCD_SIGNATURE, ignore (already found and verified).
    // 2 bytes encode disk number for this archive, ignore.
    // 2 bytes encode disk number for the central directory, ignore.
    // 2 bytes encode num entries in the central directory on this disk, ignore.
    // *** 2 bytes encode num entries in the central directory overall [READ THIS]
    // *** 4 bytes encode the length of the central directory [READ THIS]
    // *** 4 bytes encode the file offset of the central directory [READ THIS]
    // 2 bytes encode the length of the zip file comment, ignore.
    // Everything else from here to the EOF is the zip file comment, or junk. Ignore.
    skipOrDie(in, 2 + 2 + 2);
    int numEntriesInCentralDirectory = read16BitUnsigned(in);
    if (numEntriesInCentralDirectory == 0xffff) {
      // If 0xffff, this is a zip64 archive and this code doesn't handle that.
      throw new ZipException("No support for zip64");
    }
    long lengthOfCentralDirectory = read32BitUnsigned(in);
    long offsetOfCentralDirectory = read32BitUnsigned(in);
    return new MinimalCentralDirectoryMetadata(
        numEntriesInCentralDirectory, offsetOfCentralDirectory, lengthOfCentralDirectory);
  }
}
