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

public enum CompressionMethod {
    NO_COMPRESSION(0),
    SHRUNK(1),
    COMPRESSION_FACTOR_1(2),
    COMPRESSION_FACTOR_2(3),
    COMPRESSION_FACTOR_3(4),
    COMPRESSION_FACTOR_4(5),
    IMPLODED(6),
    __RESERVED7(7),
    DEFLATED(8),
    DEFLATED_WITH_DEFLATE64(9),
    PKWARE_IMPLODING(10),
    __RESERVED11(11),
    BZIP2(12),
    __RESERVED13(13),
    LZMA_EFS(14),
    __RESERVED15(15),
    __RESERVED16(16),
    __RESERVED17(17),
    NEW_IBM_TERSE(18),
    IBM_LZ77_Z_ARCHITECTURE_PFS(19),
    WAV_PACK(97),
    PPMD_VERSION_I_REV_1(98);
    
    public final int value;
    private CompressionMethod(int value) {
        this.value = value;
    }
    public int toHeaderValue() {
        return value;
    }
    public static CompressionMethod fromHeaderValue(int value) {
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
            default: throw new IllegalArgumentException("illegal value: " + value);
        }
    }
}