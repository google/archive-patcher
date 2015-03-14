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

import java.util.ArrayList;
import java.util.List;

import com.google.archivepatcher.compression.Compressor;
import com.google.archivepatcher.compression.DeflateCompressor;
import com.google.archivepatcher.compression.DeflateUncompressor;
import com.google.archivepatcher.compression.Uncompressor;

/**
 * Utilities for delta generation and application.
 */
public class DeltaUtils {

    /**
     * Create and return a list of all built-in {@link DeltaGenerator}
     * implementations.
     * @return such a list
     */
    public static List<DeltaGenerator> getBuiltInDeltaGenerators() {
        List<DeltaGenerator> deltaGenerators = new ArrayList<DeltaGenerator>();
        deltaGenerators.add(new JxdDeltaGenerator());
        deltaGenerators.add(new BsDiffDeltaGenerator());
        return deltaGenerators;
    }

    /**
     * Create and return a list of all built-in {@link Compressor}
     * implementations.
     * @return such a list
     */
    public static List<Compressor> getBuiltInCompressors() {
        List<Compressor> compressors = new ArrayList<Compressor>();
        compressors.add(new DeflateCompressor());
        return compressors;
    }

    /**
     * Create and return a list of all built-in {@link Uncompressor}s
     * implementations.
     * @return such a list
     */
    public static List<Uncompressor> getBuiltInUncompressors() {
        List<Uncompressor> uncompressors = new ArrayList<Uncompressor>();
        uncompressors.add(new DeflateUncompressor());
        return uncompressors;
    }

    /**
     * Create and return a list of all built-in {@link DeltaApplier}
     * implementations.
     * @return such a list
     */
    public static List<DeltaApplier> getBuiltInDeltaAppliers() {
        List<DeltaApplier> deltaAppliers = new ArrayList<DeltaApplier>();
        deltaAppliers.add(new JxdDeltaApplier());
        deltaAppliers.add(new BsDiffDeltaApplier());
        return deltaAppliers;
    }

}
