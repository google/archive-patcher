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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * An interface for implementing a compressor. A compressor may be used to
 * compress arbitrary binary data.
 * <p>
 * In practice compressors are used to compress the deltas produced by a
 * {@link com.google.archivepatcher.delta.DeltaGenerator}. The most compact
 * output wins. The compressor can also be used to re-compress data after
 * applying a delta to uncompressed content.
 * <p>
 */
public interface Compressor {

    /**
     * Compresses data, writing the compressed data into compressedOut.
     * 
     * @param uncompressedIn the uncompressed data
     * @param compressedOut the compressed data
     * @throws IOException if something goes awry while reading or writing
     */
    public void compress(InputStream uncompressedIn, OutputStream compressedOut) throws IOException;

    /**
     * Returns the unique ID of this compressor. This should match the ID
     * returned by the identically-named method in the corresponding
     * {@link Uncompressor} implementation.
     * @return the ID, which must be greater than zero.
     */
    public abstract int getId();

}
