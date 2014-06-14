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

public enum Flag {
    ENCRYPTED(0),
    DEFLATE_COMPRESSION_BIT1(1),   // IFF compression method = 8|9 (Deflate)
    EOS_MARKER_PRESENT(1),         // IFF compression method = 14  (LZMA)
    USED_8K_SLIDING_DICTIONARY(1), // IFF compresison method = 6   (Implode)
    USED_3_SANNON_FANO_TREES(2),   // IFF compression method = 6   (Implode)
    DEFLATE_COMPRESSION_BIT2(2),   // IFF compression method = 8|9 (Deflate)
    USE_DATA_DESCRIPTOR_FOR_SIZES_AND_CRC32(3),
    IS_COMPRESSED_PATCHED_DATA(5), // TODO: What's this?
    USES_STRONG_ENCRYPTION(6),
    FILENAME_AND_COMMENT_MUST_BE_UTF8(11), // We always use UTF-8, so meh
    ENCRYPTED_CENTRAL_DIRECTORY_WITH_LOCAL_HEADER_VALUE_MASKING(13);

    private final int position;
    private Flag(int position) {
        this.position = position;
    }

    public short getMask() {
        return (short) (1 << position);
    }
    public static boolean has(Flag flag, short flags) {
        return (flag.getMask() & flags) != 0;
    }
    public static short set(Flag flag, short flags) {
        return (short) (flags | flag.getMask());
    }
    public static short unset(Flag flag, short flags) {
        return (short) (flags & (~flag.getMask()));
    }

    public static short setCompressionOption(DeflateCompressionOption option, short flags) {
        switch(option) {
            case FAST:
                flags &= unset(Flag.DEFLATE_COMPRESSION_BIT1, flags);
                flags |= set(Flag.DEFLATE_COMPRESSION_BIT2, flags);
                break;
            case MAXIMUM:
                flags |= set(Flag.DEFLATE_COMPRESSION_BIT1, flags);
                flags &= unset(Flag.DEFLATE_COMPRESSION_BIT2, flags);
                break;
            case NORMAL:
                flags &= unset(Flag.DEFLATE_COMPRESSION_BIT1, flags);
                flags &= unset(Flag.DEFLATE_COMPRESSION_BIT2, flags);
                break;
            case SUPERFAST:
                flags |= set(Flag.DEFLATE_COMPRESSION_BIT1, flags);
                flags |= set(Flag.DEFLATE_COMPRESSION_BIT2, flags);
                break;
            default: throw new IllegalArgumentException();
        }
        return flags;
    }

    public static DeflateCompressionOption getCompressionOption(short flags) {
        if (has(Flag.DEFLATE_COMPRESSION_BIT1, flags)) {
            if (has(Flag.DEFLATE_COMPRESSION_BIT2, flags)) {
                return DeflateCompressionOption.SUPERFAST;
            }
            return DeflateCompressionOption.MAXIMUM;
        } else {
            if (has(Flag.DEFLATE_COMPRESSION_BIT2, flags)) {
                return DeflateCompressionOption.FAST;
            }
            return DeflateCompressionOption.NORMAL;
        }
    }
}
