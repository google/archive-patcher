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

package com.google.archivepatcher.delta;

/**
 * Enumeration of built-in delta engines. For backwards compatibility, always
 * add new entries with higher IDs. Zero is reserved and invalid. Values above
 * 255 are set aside for private implementations, values 1 through 254
 * (inclusive) are reserved for built-in implementations.
 */
public enum BuiltInDeltaEngine {
    /**
     * No-op delta engine.
     */
    NONE(0),

    /**
     * bsdiff-based DeltaGenerator/DeltaApplier
     */
    BSDIFF(1),

    /**
     * javaxdelta-based DeltaGenerator/DeltaApplier
     */
    JAVAXDELTA(2);

    private final int id;
    private BuiltInDeltaEngine(final int id) {
        this.id = id;
    }

    /**
     * Returns the unique ID for this delta implementation, which should be used
     * in both the DeltaGenerator and DeltaApplier implementations.
     * @return the id
     */
    public int getId() {
        return id;
    }

    /**
     * Returns the delta engine whose ID is specified.
     * 
     * @param id the ID of the engine to look up
     * @return the engine, or INVALID if there is no engine with the specified
     * ID
     */
    public static BuiltInDeltaEngine getById(final int id) {
        switch(id) {
            case 1: return BSDIFF;
            case 2: return JAVAXDELTA;
            default: return NONE;
        }
    }
}