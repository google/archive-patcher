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

package com.google.archivepatcher.reporting;

import java.util.HashMap;
import java.util.Map;

/**
 * A strategy, comprising a delta engine and a compression engine.
 */
public class Strategy {
    /**
     * The ID of the delta engine.
     */
    private final int deltaEngineId;

    /**
     * The ID of the compression engine.
     */
    private final int compressionEngineId;

    // All the built-in strategies
    private final static Map<Long, Strategy> cache = new HashMap<Long, Strategy>();

    /**
     * Returns an instance (probably cached) of a strategy matching the
     * specified configuration.
     * @param deltaEngineId the ID of the delta engine
     * @param compressionEngineId the ID of the compression engine
     * @return a strategy instance representing the IDs specified
     */
    public static Strategy getInstance(final int deltaEngineId, final int compressionEngineId) {
        final long hash = (((long) deltaEngineId) << 32) | compressionEngineId;
        Strategy result = cache.get(hash);
        if (result == null) {
            result = new Strategy(deltaEngineId, compressionEngineId);
            cache.put(hash, result);
        }
        return result;
    }

    /**
     * Creates a strategy that represents usage of the specified delta engine
     * and compression engine, using their unique IDs.
     * @param deltaEngineId the ID of the delta engine
     * @param compressionEngineId the ID of the compression engine
     */
    private Strategy(final int deltaEngineId, final int compressionEngineId) {
        this.deltaEngineId = deltaEngineId;
        this.compressionEngineId = compressionEngineId;
    }

    /**
     * Returns the ID of the delta engine for this strategy.
     * @return the ID
     */
    public int getDeltaEngineId() {
        return deltaEngineId;
    }

    /**
     * Returns the ID of the compression engine for this strategy.
     * @return the ID
     */
    public int getCompressionEngineId() {
        return compressionEngineId;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + compressionEngineId;
        result = prime * result + deltaEngineId;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        Strategy other = (Strategy) obj;
        if (compressionEngineId != other.compressionEngineId) return false;
        if (deltaEngineId != other.deltaEngineId) return false;
        return true;
    }

    @Override
    public String toString() {
        return "Strategy [deltaEngineId=" + deltaEngineId + ", compressionEngineId=" + compressionEngineId + "]";
    }
}