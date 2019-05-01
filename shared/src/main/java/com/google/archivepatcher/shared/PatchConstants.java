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

/**
 * Constants used in reading and writing patches.
 */
public class PatchConstants {
  /**
   * The identifier that begins all patches of this type.
   */
  public static final String IDENTIFIER = "GFbFv1_0"; // Google File-by-File v1.0

  /**
   * Whether we should use native bsdiff by default.
   *
   * <p>Note that we couldn't get JNI to work with gradle so this will be changed to false in github
   * repo.
   */
  // TODO: Get gradle to work with JNI
  public static final boolean USE_NATIVE_BSDIFF_BY_DEFAULT = false;

  /**
   * The total recompression size limit for the embedded archive.
   *
   * @see com.google.archivepatcher.generator.TotalRecompressionLimiter
   */
  public static final long TOTAL_RECOMPRESSION_LIMIT_EMBEDDED_ARCHIVE = 50 * 1024 * 1024; // 50 MB

  /**
   * The delta-friendly old blob size limit for the embedded archive.
   *
   * @see com.google.archivepatcher.generator.DeltaFriendlyOldBlobSizeLimiter
   */
  public static final long DELTA_FRIENDLY_OLD_BLOB_SIZE_LIMIT_EMBEDDED_ARCHIVE =
      50 * 1024 * 1024; // 50 MB

  /**
   * All available compatibility windows. The {@link #patchValue} field specifies the value for each
   * constant as represented in a patch file.
   */
  public enum CompatibilityWindowId {
    /**
     * The {@link com.google.archivepatcher.shared.DefaultDeflateCompatibilityWindow}.
     */
    DEFAULT_DEFLATE((byte) 0);

    /**
     * The representation of this enumerated constant in patch files.
     */
    public final byte patchValue;

    /** Construct a new enumerated constant with the specified value in patch files. */
    CompatibilityWindowId(byte patchValue) {
      this.patchValue = patchValue;
    }

    /**
     * Parse a patch value and return the corresponding enumerated constant.
     * @param patchValue the patch value to parse
     * @return the corresponding enumerated constant, null if unmatched
     */
    public static CompatibilityWindowId fromPatchValue(byte patchValue) {
      switch (patchValue) {
        case 0:
          return DEFAULT_DEFLATE;
        default:
          return null;
      }
    }
  }

  /**
   * All available delta formats. The {@link #patchValue} field specifies the value for each
   * constant as represented in a patch file.
   */
  public enum DeltaFormat {
    /** The bsdiff delta format. */
    BSDIFF((byte) 0, true),

    /** The bsdiff delta format. */
    FILE_BY_FILE((byte) 1, false);

    /** The representation of this enumerated constant in patch files. */
    public final byte patchValue;

    /**
     * Whether this delta algorithm can be applied to multiple entries at once (e.g., BSDIFF) or
     * does it have to be applied to one entry at a time (e.g., FBF).
     */
    public final boolean supportsMultiEntryDelta;

    /** Construct a new enumerated constant with the specified value in patch files. */
    DeltaFormat(byte patchValue, boolean supportsMultiEntryDelta) {
      this.patchValue = patchValue;
      this.supportsMultiEntryDelta = supportsMultiEntryDelta;
    }

    /**
     * Parse a patch value and return the corresponding enumerated constant.
     * @param patchValue the patch value to parse
     * @return the corresponding enumerated constant, null if unmatched
     */
    public static DeltaFormat fromPatchValue(byte patchValue) {
      switch (patchValue) {
        case 0:
          return BSDIFF;
        case 1:
          return FILE_BY_FILE;
      }
      throw new IllegalArgumentException("Unknown patch value " + patchValue);
    }
  }

  /** All available compression method in a zip archive. */
  public enum CompressionMethod {
    /** The entry is compressed with DEFLATE. */
    DEFLATE,

    /** The entry is not compressed. */
    STORED,
    /** The entry's compression method cannot be determined. */
    UNKNOWN;

    private static final int DEFLATE_VALUE = 8;
    private static final int STORED_VALUE = 0;

    /**
     * Obtain the corresponding {@link CompressionMethod} for the byte representation in ZIP spec.
     */
    public static CompressionMethod fromValue(int value) {
      switch (value) {
        case DEFLATE_VALUE:
          return DEFLATE;
        case STORED_VALUE:
          return STORED;
        default:
          return UNKNOWN;
      }
    }

    /**
     * Obtain the value of the current compression method as specified in the ZIP format.
     *
     * @throws IllegalArgumentException if the compression method is {@link #UNKNOWN}.
     */
    public int value() {
      switch (this) {
        case DEFLATE:
          return DEFLATE_VALUE;
        case STORED:
          return STORED_VALUE;
        default:
          throw new IllegalArgumentException("Unknown compression method");
      }
    }
  }
}
