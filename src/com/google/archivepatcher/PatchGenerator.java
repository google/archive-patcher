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

package com.google.archivepatcher;


import com.google.archivepatcher.parts.LocalSectionParts;
import com.google.archivepatcher.parts.CentralDirectoryFile;
import com.google.archivepatcher.patcher.BeginMetadata;
import com.google.archivepatcher.patcher.NewMetadata;
import com.google.archivepatcher.patcher.PatchDirective;
import com.google.archivepatcher.patcher.PatchMetadata;
import com.google.archivepatcher.patcher.PatchWriter;
import com.google.archivepatcher.patcher.RefreshMetadata;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutput;
import java.io.IOException;

public class PatchGenerator {
    private final PatchWriter patchWriter;
    private final Archive oldArchive;
    private final Archive newArchive;
    private final DeltaGenerator deltaGenerator;

    public PatchGenerator(Archive oldArchive, Archive newArchive, DataOutput patchOut, DeltaGenerator deltaGenerator) throws IOException {
        this.oldArchive = oldArchive;
        this.newArchive = newArchive;
        this.deltaGenerator = deltaGenerator;
        patchWriter = new PatchWriter(patchOut);
        patchWriter.init();
    }

    public PatchGenerator(String oldPath, String newPath, DataOutput patchOut, DeltaGenerator deltaGenerator) throws IOException {
        this(Archive.fromFile(oldPath), Archive.fromFile(newPath), patchOut, deltaGenerator);
    }

    public static boolean dataSame(LocalSectionParts lsp1, LocalSectionParts lsp2) {
        return lsp1.getFileDataPart().equals(lsp2.getFileDataPart());
    }

    public static boolean nonDataSame(CentralDirectoryFile cdf1,
            LocalSectionParts lsp1,
            CentralDirectoryFile cdf2,
            LocalSectionParts lsp2) {
        if (!cdf1.positionIndependentEquals(cdf2)) return false;
        if (!lsp1.getLocalFilePart().equals(lsp2.getLocalFilePart())) return false;
        if (lsp1.hasDataDescriptor() != lsp2.hasDataDescriptor()) return false;
        if (!lsp1.hasDataDescriptor()) return true; // nothing left to compare
        return lsp1.getDataDescriptorPart().equals(lsp2.getDataDescriptorPart());
    }

    public void generatePatch() throws IOException {
        // Output the central directory.
        patchWriter.write(PatchDirective.BEGIN(
                new BeginMetadata(newArchive.getCentralDirectory())));

        for (LocalSectionParts newLSP : newArchive.getLocal().entries()) {
            final CentralDirectoryFile newCDF =
                    newArchive.getCentralDirectory().getByPath(
                            newLSP.getLocalFilePart().getFileName());
            final CentralDirectoryFile oldCDF =
                    oldArchive.getCentralDirectory().getByPath(
                            newLSP.getLocalFilePart().getFileName());
            PatchDirective directive = null;
            if (oldCDF != null) {
                final LocalSectionParts oldLSP =
                        oldArchive.local.getByPath(
                                newLSP.getLocalFilePart().getFileName());
                if (dataSame(oldLSP, newLSP)) {
                    // Data is the same. We can at least refresh, maybe pure copy.
                    if (nonDataSame(oldCDF, oldLSP, newCDF, newLSP)) {
                        // Identical resource: COPY
                        directive = PatchDirective.COPY(
                                (int) oldCDF.getRelativeOffsetOfLocalHeader_32bit());
                    } else {
                        // Identical resource except for metadata: REFRESH
                        directive = PatchDirective.REFRESH(
                                (int) oldCDF.getRelativeOffsetOfLocalHeader_32bit(),
                                new RefreshMetadata(
                                        newLSP.getLocalFilePart(),
                                        newLSP.getDataDescriptorPart()));
                    }
                } else {
                    // Data has changed: NEW
                    // TODO: PATCH if we can delta-patch.
                    if (deltaGenerator != null) {
                        ByteArrayOutputStream deltaBuffer = new ByteArrayOutputStream();
                        ByteArrayInputStream oldIn = new ByteArrayInputStream(oldLSP.getFileDataPart().getData());
                        ByteArrayInputStream newIn = new ByteArrayInputStream(newLSP.getFileDataPart().getData());
                        deltaGenerator.makeDelta(oldIn, newIn, deltaBuffer);
                        int deltaSize = deltaBuffer.size();
                        int originalSize = newLSP.getFileDataPart().getData().length;
                        int savings = originalSize - deltaSize;
                        if (savings > 0) {
                            System.out.println("DELTA WINS: " + oldCDF.getFileName() + " delta saves " + savings  + " bytes of " + originalSize + " compared to copying.");
                            directive = PatchDirective.PATCH(
                                    (int) oldCDF.getRelativeOffsetOfLocalHeader_32bit(),
                                    new PatchMetadata(
                                            newLSP.getLocalFilePart(),
                                            newLSP.getDataDescriptorPart(),
                                            deltaBuffer.toByteArray()));
                        } else {
                            System.out.println("DELTA LOSES: " + oldCDF.getFileName() + " delta adds " + (-1 * savings)  + " bytes on top of " + originalSize + " compared to copying.");
                        }
                    }

                    if (directive == null) {
                        directive = PatchDirective.NEW(
                                new NewMetadata(
                                        newLSP.getLocalFilePart(),
                                        newLSP.getFileDataPart(),
                                        newLSP.getDataDescriptorPart()));
                    }
                }
            } else {
                // Totally new resource: NEW
                directive = PatchDirective.NEW(
                        new NewMetadata(
                                newLSP.getLocalFilePart(),
                                newLSP.getFileDataPart(),
                                newLSP.getDataDescriptorPart()));
            }
            patchWriter.write(directive);
        } // for-loop
    }
}