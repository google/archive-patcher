// Copyright 2014 Google Inc. All rights reserved.
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

package com.google.archivepatcher.meta;

/**
 * An enumeration of all the compression methods listed in the ZIP
 * "appnote" specification at https://www.pkware.com/support/zip-app-note.
 * See the appnote documentation for details on algorithms and meanings of
 * the individual values.
 */
public enum CompressionMethod {
    @SuppressWarnings("javadoc")
    NO_COMPRESSION(0),
    @SuppressWarnings("javadoc")
    SHRUNK(1),
    @SuppressWarnings("javadoc")
    COMPRESSION_FACTOR_1(2),
    @SuppressWarnings("javadoc")
    COMPRESSION_FACTOR_2(3),
    @SuppressWarnings("javadoc")
    COMPRESSION_FACTOR_3(4),
    @SuppressWarnings("javadoc")
    COMPRESSION_FACTOR_4(5),
    @SuppressWarnings("javadoc")
    IMPLODED(6),
    @SuppressWarnings("javadoc")
    __RESERVED7(7),
    @SuppressWarnings("javadoc")
    DEFLATED(8),
    @SuppressWarnings("javadoc")
    DEFLATED_WITH_DEFLATE64(9),
    @SuppressWarnings("javadoc")
    PKWARE_IMPLODING(10),
    @SuppressWarnings("javadoc")
    __RESERVED11(11),
    @SuppressWarnings("javadoc")
    BZIP2(12),
    @SuppressWarnings("javadoc")
    __RESERVED13(13),
    @SuppressWarnings("javadoc")
    LZMA_EFS(14),
    @SuppressWarnings("javadoc")
    __RESERVED15(15),
    @SuppressWarnings("javadoc")
    __RESERVED16(16),
    @SuppressWarnings("javadoc")
    __RESERVED17(17),
    @SuppressWarnings("javadoc")
    NEW_IBM_TERSE(18),
    @SuppressWarnings("javadoc")
    IBM_LZ77_Z_ARCHITECTURE_PFS(19),
    @SuppressWarnings("javadoc")
    WAV_PACK(97),
    @SuppressWarnings("javadoc")
    PPMD_VERSION_I_REV_1(98);

    /**
     * The value of this constant as represented in a ZIP header. Note that
     * the value is really only 16 bits, and therefore cannot exceed 65535.
     * An integer is used so that values in the range [32768, 65535] can be
     * safely represented.
     */
    public final int value;

    /**
     * Constructs a new emuerated constant whose binary equivalent has the
     * specified value.
     * @param value the binary form of this constant
     */
    private CompressionMethod(final int value) {
        if (value > 65535) throw new IllegalArgumentException(
            value + " > 65535");
        this.value = value;
    }

    /**
     * Parses a constant from its equivalent binary form in an archive.
     * @param value the value to parse; note that the specification allows
     * only 16 bits, so the maximum allowed value is 65535. An integer is used
     * so that values in the range [32768, 65535] can be safely represented.
     * @return the compression method
     * @throws IllegalArgumentException if the specified value is outside the
     * allowed range [0, 65535] or is value not defined by the specification.
     */
    public static CompressionMethod fromHeaderValue(final int value) {
        if (value > 65535) throw new IllegalArgumentException(
            value + " > 65535");
        switch (value) {
            case 0: return NO_COMPRESSION;
            case 1: return SHRUNK;
            case 2: return COMPRESSION_FACTOR_1;
            case 3: return COMPRESSION_FACTOR_2;
            case 4: return COMPRESSION_FACTOR_3;
            case 5: return COMPRESSION_FACTOR_4;
            case 6: return IMPLODED;
            case 7: return __RESERVED7;
            case 8: return DEFLATED;
            case 9: return DEFLATED_WITH_DEFLATE64;
            case 10: return PKWARE_IMPLODING;
            case 11: return __RESERVED11;
            case 12: return BZIP2;
            case 13: return __RESERVED13;
            case 14: return LZMA_EFS;
            case 15: return __RESERVED15;
            case 16: return __RESERVED16;
            case 17: return __RESERVED17;
            case 18: return NEW_IBM_TERSE;
            case 19: return IBM_LZ77_Z_ARCHITECTURE_PFS;
            case 97: return WAV_PACK;
            case 98: return PPMD_VERSION_I_REV_1;
            default: throw new IllegalArgumentException(
                "unknown compression method: " + value);
        }
    }
}