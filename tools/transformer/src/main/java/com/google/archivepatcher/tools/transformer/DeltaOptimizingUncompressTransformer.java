// Copyright 2016 Google Inc. All rights reserved.
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

package com.google.archivepatcher.tools.transformer;

import java.io.File;
import java.io.IOException;

import com.google.archivepatcher.Archive;
import com.google.archivepatcher.compression.JreDeflateParameters;
import com.google.archivepatcher.parts.LocalSectionParts;

/**
 * An {@link UncompressTransformer} that is specifically designed to create
 * archives that are optimized for a specific delta computation. This
 * transformer takes <em>two</em> archives (an "old" and a "new") as input.
 * Instead of blindly uncompressing every resource in "old", this transformer
 * only uncompresses resources whose compressed form in "old" is strictly
 * different than in "new". The check is done by comparing the compressed form
 * of the data in "old" to the compressed form of the data in "new", using the
 * path to find the entries in both archives. This can yield significant time
 * and space savings for a patch system: transformed files are smaller and all
 * unchanged resources won't need to be recompressed after applying a patch on
 * the transformed archives.
 */
public class DeltaOptimizingUncompressTransformer
extends UncompressTransformer {
    /**
     * The "new" archive for making comparisons against.
     */
    private Archive newArchive = null;

    /**
     * Sets the "new" archive used for making comparisons against the "old"
     * archive.
     * @param archive
     */
    public void setNewArchive(Archive archive) {
        newArchive = archive;
    }

    @Override
    public TransformationRecord transform(File inputArchiveFile,
        File divinerFile, File outputArchiveFile, File outputRecordFile)
            throws IOException {
        if (newArchive == null) {
            throw new IllegalStateException("newArchive has not been set");
        }
        return super.transform(inputArchiveFile, divinerFile,
            outputArchiveFile, outputRecordFile);
    }

    @Override
    protected boolean shouldTransform(Archive oldArchive,
        LocalSectionParts oldLsp, JreDeflateParameters oldDeflateParameters) {
        final String path = oldLsp.getLocalFilePart().getFileName();
        final LocalSectionParts newLsp = newArchive.getLocal().getByPath(path);
        if (newLsp == null) {
            // Entry deleted from "new" archive, pointless to uncompress.
            return false;
        }
        final long oldCrc32 = oldLsp.getLocalFilePart().getCrc32_32bit();
        final long newCrc32 = newLsp.getLocalFilePart().getCrc32_32bit();
        if (oldCrc32 != newCrc32) {
            // Entry changed, so the compressed form must also be changed.
            // Faster than scanning the compressed bytes.
            return true;
        }
        // Entry has same CRC32, and the same path, and is presumably in a newer
        // version of the same file. While it is possible that it is different,
        // it is extremely unlikely. Assume it is unchanged, and in the worst
        // case the delta algorithm will perform suboptimally for this resource.
        // It isn't worth scanning the bytes for such a vanishingly low chance.
        return false;
    }
}
