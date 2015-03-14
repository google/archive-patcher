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

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.google.archivepatcher.bsdiff.BsPatch;
import com.google.archivepatcher.util.IOUtils;


/**
 * Implementation of a {@link DeltaApplier} based on bsdiff.
 */
public class BsDiffDeltaApplier implements DeltaApplier {
    @Override
    public void applyDelta(InputStream oldData, InputStream deltaData,
        OutputStream newOut) throws IOException {
        final DataInputStream dataIn = new DataInputStream(deltaData);
        final String header_string = IOUtils.readUTF8(dataIn, 16);
        if (!header_string.equals("HAYDEN/BSDIFF43 ")) {
            throw new IOException("Corrupt header");
        }
        final int new_size = (int) IOUtils.readUnsignedInt(dataIn);
        if (new_size < 0) {
            throw new IOException("Corrupt patch, new_size=" + new_size);
        }
        final byte[] oldBytes = IOUtils.readAll(oldData);
        final byte[] newBytes = new byte[new_size];
        BsPatch.applyPatch(oldBytes, newBytes, deltaData);
        newOut.write(newBytes, 0, new_size);
    }

    @Override
    public int getId() {
        return BuiltInDeltaEngine.BSDIFF.getId();
    }
}
