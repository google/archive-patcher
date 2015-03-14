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
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/**
 * Implementation of {@link Uncompressor} based on deflate.
 */
public class DeflateUncompressor implements Uncompressor {
    /**
     * Whether to skip the standard zlib header and checksum fields when
     * reading. Defaults to false.
     */
    private boolean nowrap = false;

    /**
     * Returns whether to skip the standard zlib header and checksum fields when
     * reading.
     * @return the value
     * @see Inflater#Inflater(boolean)
     */
    public boolean isNowrap() {
        return nowrap;
    }

    /**
     * Sets whether or not to suppress wrapping the deflate output with the
     * standard zlib header and checksum fields. Defaults to false.
     * @param nowrap see {@link Inflater#Inflater(boolean)}
     */
    public void setNowrap(boolean nowrap) {
        this.nowrap = nowrap;
    }

    @Override
    public void uncompress(InputStream compressedIn, OutputStream uncompressedOut) throws IOException {
        Inflater inflater = new Inflater(nowrap);
        InflaterInputStream inflaterIn = new InflaterInputStream(compressedIn, inflater);
        byte[] buffer = new byte[32768];
        int numRead = 0;
        while ((numRead = inflaterIn.read(buffer)) >= 0) {
            uncompressedOut.write(buffer, 0, numRead);
        }
        inflater.end();
    }

    @Override
    public int getId() {
        return BuiltInCompressionEngine.DEFLATE.getId();
    }

}
