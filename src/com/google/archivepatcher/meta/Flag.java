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
 * An enumeration of all the flag bitmask-based options listed in the ZIP
 * "appnote" specification at https://www.pkware.com/support/zip-app-note.
 * See the appnote documentation for details on algorithms and meanings of
 * the individual values.
 */
public enum Flag {
    @SuppressWarnings("javadoc")
    ENCRYPTED(0),
    @SuppressWarnings("javadoc")
    DEFLATE_COMPRESSION_BIT1(1),   // IFF compression method = 8|9 (Deflate)
    @SuppressWarnings("javadoc")
    EOS_MARKER_PRESENT(1),         // IFF compression method = 14  (LZMA)
    @SuppressWarnings("javadoc")
    USED_8K_SLIDING_DICTIONARY(1), // IFF compression method = 6   (Implode)
    @SuppressWarnings("javadoc")
    USED_3_SANNON_FANO_TREES(2),   // IFF compression method = 6   (Implode)
    @SuppressWarnings("javadoc")
    DEFLATE_COMPRESSION_BIT2(2),   // IFF compression method = 8|9 (Deflate)
    @SuppressWarnings("javadoc")
    USE_DATA_DESCRIPTOR_FOR_SIZES_AND_CRC32(3),
    @SuppressWarnings("javadoc")
    IS_COMPRESSED_PATCHED_DATA(5), // TODO: What's this?
    @SuppressWarnings("javadoc")
    USES_STRONG_ENCRYPTION(6),
    @SuppressWarnings("javadoc")
    FILENAME_AND_COMMENT_MUST_BE_UTF8(11), // We always use UTF-8, so meh
    @SuppressWarnings("javadoc")
    ENCRYPTED_CENTRAL_DIRECTORY_WITH_LOCAL_HEADER_VALUE_MASKING(13);

    /**
     * The bit position of the flag. Zero corresponds to the rightmost or
     * least-significant bit.
     */
    private final int position;

    /**
     * Declares a new enumerated constant at the specified bit position.
     * 
     * @param position the position
     */
    private Flag(final int position) {
        this.position = position;
    }

    /**
     * Returns a bitmask with exactly one bit set. The position of the set bit
     * is defined by the specification.
     * @return such a mask
     */
    public short getMask() {
        return (short) (1 << position);
    }

    /**
     * Returns true iff the specified "flags" value has the specified flag set.
     * @param flag the flag to check for the presence of
     * @param flags a flags bitset to scan for the flag within
     * @return as described
     */
    public static boolean has(final Flag flag, final short flags) {
        return (flag.getMask() & flags) != 0;
    }

    /**
     * Sets the specified flag in the specified "flags" value and returns the
     * new "flags" value with the flag set.
     * @param flag the flag to toggle on
     * @param flags a flags bitset to be updated and returned
     * @return as described
     */
    public static short set(Flag flag, short flags) {
        return (short) (flags | flag.getMask());
    }

    /**
     * Un-sets the specified flag in the specified "flags" value and returns the
     * new "flags" value with the flag cleared.
     * @param flag the flag to toggle off
     * @param flags a flags bitset to be updated and returned
     * @return as described
     */
    public static short unset(Flag flag, short flags) {
        return (short) (flags & (~flag.getMask()));
    }

    /**
     * Sets the specified {@link CompressionMethod} in the specified "flags"
     * bitset.
     * @param option the option to set
     * @param flags the "flags" bitset to be modified
     * @return the updated flags value 
     * @see CompressionMethod
     */
    public static short setCompressionOption(
        DeflateCompressionOption option, short flags) {
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

    /**
     * Returns the {@link CompressionMethod} set in the specified "flags"
     * bitset.
     * @param flags the "flags" bitset to be scanned
     * @return the compression option
     */
    public static DeflateCompressionOption getCompressionOption(short flags) {
        if (has(Flag.DEFLATE_COMPRESSION_BIT1, flags)) {
            if (has(Flag.DEFLATE_COMPRESSION_BIT2, flags)) {
                return DeflateCompressionOption.SUPERFAST;
            }
            return DeflateCompressionOption.MAXIMUM;
        }
        if (has(Flag.DEFLATE_COMPRESSION_BIT2, flags)) {
            return DeflateCompressionOption.FAST;
        }
        return DeflateCompressionOption.NORMAL;
    }
}
