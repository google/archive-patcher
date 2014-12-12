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

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import com.google.archivepatcher.DeltaGenerator;
import com.google.archivepatcher.bsdiff.BsDiff;
import com.google.archivepatcher.util.IOUtils;


/**
 * Implementation of a {@link DeltaGenerator} based on bsdiff.
 */
public class BsDiffDeltaGenerator extends DeltaGenerator {

    @Override
    public void makeDelta(InputStream oldData, InputStream newData, OutputStream deltaOut)
        throws IOException {
        // bsdiff uses some extra padding on the end of its data arrays.
        // The "oldsize" and "newsize" values used throughout the code do NOT
        // include these extra bytes, but the bytes are NECESSARY for some code
        // that can walk off the end of the buffer by one byte.
        final byte[] oldBytes = IOUtils.readAll(oldData);
        final byte[] newBytes = IOUtils.readAll(newData);
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        {
            final DataOutputStream dataOut = new DataOutputStream(deltaOut);
            IOUtils.writeUTF8(dataOut, "HAYDEN/BSDIFF43 ");
            IOUtils.writeUnsignedInt(dataOut, newBytes.length);
            dataOut.flush();
        }
        DeflaterOutputStream deflaterOut = new DeflaterOutputStream(deltaOut, deflater);
        BsDiff.generatePatch(oldBytes, newBytes, deflaterOut);
        deflaterOut.finish();
        deflaterOut.flush();
    }
}
