// Copyright 2015 Google Inc. All rights reserved.
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

package com.google.archivepatcher.compression;

import java.util.EnumSet;
import java.util.Set;

/**
 * Enumeration of built-in compression engines. For backwards compatibility,
 * always add new entries with higher IDs. Zero is reserved and invalid. Values
 * above 255 are set aside for private implementations, values 1 through 254
 * (inclusive) are reserved for built-in implementations.
 */
public enum BuiltInCompressionEngine {
    /**
     * No-op delta engine.
     */
    NONE(0),

    /**
     * Deflate-based compression.
     */
    DEFLATE(1);

    private final int id;
    private final int zipCompressionMethodId;
    private BuiltInCompressionEngine(final int id) {
        this(id, -1);
    }
    private BuiltInCompressionEngine(final int id, final int zipCompressionMethodId) {
        this.id = id;
        this.zipCompressionMethodId =zipCompressionMethodId;
    }

    /**
     * Returns the unique ID for this compression implementation, which should
     * be used in both the Compressor and Uncompressor implementations.
     * @return the id
     */
    public int getId() {
        return id;
    }

    /**
     * Returns the ID from the PKZIP "APPNOTE" v6.3.4 specification that applies
     * to this compression engine, if any; otherwise, returns -1. For example,
     * the DEFLATE builtin returns 8. For more IDs, see APPNOTE section 4.4.5
     * ("compression method").
     * @return as described
     */
    public int getZipCompressionMethodId() {
        return zipCompressionMethodId;
    }

    /**
     * Returns the compression engine whose ID is specified.
     * 
     * @param id the ID of the engine to look up
     * @return the engine, or INVALID if there is no engine with the specified
     * ID
     */
    public static BuiltInCompressionEngine getById(final int id) {
        switch(id) {
            case 1: return DEFLATE;
            default: return NONE;
        }
    }

    /**
     * Returns a set of all the ZIP-compatible compression engines. All the
     * returns engines are guaranteed to have a non-negative return value from
     * {@link #getZipCompressionMethodId()}.
     * @return such a set
     */
    public static Set<BuiltInCompressionEngine> getZipCompressionEngines() {
        return EnumSet.of(DEFLATE);
    }
}