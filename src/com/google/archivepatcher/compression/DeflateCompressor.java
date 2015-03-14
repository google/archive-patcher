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
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * Implementation of {@link Compressor} based on deflate. Uses maximum
 * compression by default.
 */
public class DeflateCompressor implements Compressor {

    /**
     * The compression level to use. Defaults to
     * {@link Deflater#BEST_COMPRESSION}.
     */
    private int compressionLevel = Deflater.BEST_COMPRESSION;

    /**
     * The compression strategy to use. Defaults to
     * {@link Deflater#DEFAULT_STRATEGY}.
     */
    private int strategy = Deflater.DEFAULT_STRATEGY;

    /**
     * Whether or not to suppress wrapping the deflate output with the
     * standard zlib header and checksum fields. Defaults to false.
     */
    private boolean nowrap = false;

    /**
     * Returns whether or not to suppress wrapping the deflate output with the
     * standard zlib header and checksum fields.
     * @return the value
     * @see Deflater#Deflater(int, boolean)
     */
    public boolean isNowrap() {
        return nowrap;
    }

    /**
     * Sets whether or not to suppress wrapping the deflate output with the
     * standard zlib header and checksum fields. Defaults to false.
     * @param nowrap see {@link Deflater#Deflater(int, boolean)}
     */
    public void setNowrap(boolean nowrap) {
        this.nowrap = nowrap;
    }

    /**
     * Returns the compression level that will be used, in the range 0-9.
     * @return the level
     */
    public int getCompressionLevel() {
        return compressionLevel;
    }

    /**
     * Sets the compression level to be used. Defaults to
     * {@link Deflater#BEST_COMPRESSION}.
     * @param compressionLevel the level, in the range 0-9
     */
    public void setCompressionLevel(int compressionLevel) {
        if (compressionLevel < 0 || compressionLevel > 9) {
            throw new IllegalArgumentException(
                "compressionLevel must be in the range [0,9]: " +
            compressionLevel);
        }
        this.compressionLevel = compressionLevel;
    }

    /**
     * Returns the strategy that will be used, from {@link Deflater}.
     * @return the strategy
     */
    public int getStrategy() {
        return strategy;
    }

    
    /**
     * Sets the strategy that will be used. Valid values can be found in
     * {@link Deflater}. Defaults to {@link Deflater#DEFAULT_STRATEGY}
     * @param strategy the strategy to be used
     */
    public void setStrategy(int strategy) {
        this.strategy = strategy;
    }

    @Override
    public void compress(InputStream uncompressedIn, OutputStream compressedOut)
        throws IOException {
        Deflater deflater = new Deflater(compressionLevel, nowrap);
        deflater.setStrategy(strategy);
        DeflaterOutputStream deflaterOut =
            new DeflaterOutputStream(compressedOut, deflater);
        byte[] buffer = new byte[32768];
        int numRead = 0;
        while ((numRead = uncompressedIn.read(buffer)) >= 0) {
            deflaterOut.write(buffer, 0, numRead);
        }
        deflaterOut.finish();
        deflaterOut.flush();
        deflater.end();
    }

    @Override
    public int getId() {
        return BuiltInCompressionEngine.DEFLATE.getId();
    }
}