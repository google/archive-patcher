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

import com.google.archivepatcher.parts.FileData;
import com.google.archivepatcher.parts.LocalSectionParts;
import com.google.archivepatcher.parts.CentralDirectoryFile;
import com.google.archivepatcher.parts.LocalSection;
import com.google.archivepatcher.patcher.BeginMetadata;
import com.google.archivepatcher.patcher.NewMetadata;
import com.google.archivepatcher.patcher.PatchDirective;
import com.google.archivepatcher.patcher.PatchMetadata;
import com.google.archivepatcher.patcher.PatchParser;
import com.google.archivepatcher.patcher.RefreshMetadata;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

//FIXME: We should stream the output, not buffer in memory. And we shouldn't
//       modify the old Archive's in-memory representation, either.
public class PatchApplier {
    private final Archive oldArchive;
    private final PatchParser parser;
    private final Archive newArchive;
    private final DeltaApplier deltaApplier;
    private final Map<Integer, CentralDirectoryFile> oldCDFByOffset =
            new HashMap<Integer, CentralDirectoryFile>();
    private int currentFileOffset = 0; // How far we have written

    public PatchApplier(Archive oldArchive, PatchParser parser, DeltaApplier deltaApplier) {
        this.oldArchive = oldArchive;
        this.parser = parser;
        this.deltaApplier = deltaApplier;
        this.newArchive = new Archive();
    }

    public PatchApplier(String oldArchive, String patchPath, DeltaApplier deltaApplier) throws IOException {
        this(Archive.fromFile(oldArchive), new PatchParser(new File(patchPath)), deltaApplier);
    }

    private void cacheOffsets() {
        for (CentralDirectoryFile entry : oldArchive.getCentralDirectory().entries()) {
            oldCDFByOffset.put((int) entry.getRelativeOffsetOfLocalHeader_32bit(), entry);
        }
    }

    private CentralDirectoryFile getOldCDF(final int offset) {
        return oldCDFByOffset.get(offset);
    }
    
    private LocalSectionParts getOldALP(final int offset) {
        return oldArchive.getLocal().getByPath(getOldCDF(offset).getFileName());
    }

    private CentralDirectoryFile getNewCDF(CentralDirectoryFile oldCDF) {
        return getNewCDF(oldCDF.getFileName());
    }

    private CentralDirectoryFile getNewCDF(String path) {
        return newArchive.getCentralDirectory().getByPath(path);
    }

    public Archive applyPatch() throws IOException {
        parser.init();
        cacheOffsets();
        PatchDirective directive = null;
        while ((directive = parser.read()) != null) {
            switch (directive.getCommand()) {
                case COPY:
                    applyCopy(directive.getOffset());
                    break;
                case PATCH:
                    applyPatch(directive.getOffset(), (PatchMetadata) directive.getPart());
                    break;
                case BEGIN:
                    applyBegin((BeginMetadata) directive.getPart());
                    break;
                case NEW:
                    applyNew((NewMetadata) directive.getPart());
                    break;
                case REFRESH:
                    applyRefresh(directive.getOffset(), (RefreshMetadata) directive.getPart());
                    break;
                default: throw new UnsupportedOperationException("Unsupported patch command: " + directive.getCommand());
            }
        }
        return newArchive;
    }

    private void applyCopy(final int sourceOffset) {
        CentralDirectoryFile cdf = getOldCDF(sourceOffset);
        LocalSectionParts alp = oldArchive.getLocal().getByPath(cdf.getFileName());
        newArchive.getLocal().append(alp);

        assert(currentFileOffset == getNewCDF(cdf).getRelativeOffsetOfLocalHeader_32bit());
        currentFileOffset += alp.getStructureLength();
    }

    private void applyBegin(final BeginMetadata metadata) {
        newArchive.setCentralDirectry(metadata.getCd());
        newArchive.setLocal(new LocalSection());
    }

    private void applyNew(final NewMetadata metadata) {
        LocalSectionParts alp = new LocalSectionParts();
        alp.setLocalFilePart(metadata.getLocalFilePart());
        alp.setDataDescriptorPart(metadata.getDataDescriptorPart());
        alp.setFileDataPart(metadata.getFileDataPart());
        newArchive.getLocal().append(alp);
        
        assert(currentFileOffset ==
                getNewCDF(alp.getLocalFilePart().getFileName())
                .getRelativeOffsetOfLocalHeader_32bit());
        currentFileOffset += alp.getStructureLength();
    }

    private void applyRefresh(final int sourceOffset, final RefreshMetadata metadata) {
        LocalSectionParts alp = new LocalSectionParts();
        alp.setLocalFilePart(metadata.getLocalFilePart());
        alp.setDataDescriptorPart(metadata.getDataDescriptorPart());
        alp.setFileDataPart(getOldALP(sourceOffset).getFileDataPart());
        newArchive.getLocal().append(alp);
        
        assert(currentFileOffset ==
                getNewCDF(alp.getLocalFilePart().getFileName())
                .getRelativeOffsetOfLocalHeader_32bit());
        currentFileOffset += alp.getStructureLength();
    }

    private void applyPatch(final int sourceOffset, final PatchMetadata metadata) throws IOException {
        LocalSectionParts oldAlp = getOldALP(sourceOffset);
        ByteArrayInputStream oldDataInput = new ByteArrayInputStream(
                oldAlp.getFileDataPart().getData());
        ByteArrayInputStream patchInput = new ByteArrayInputStream(
                metadata.getData());
        // TODO: stream straight to file
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        deltaApplier.applyDelta(oldDataInput, patchInput, buffer);
        LocalSectionParts newAlp = new LocalSectionParts();
        newAlp.setLocalFilePart(metadata.getLocalFilePart());
        newAlp.setFileDataPart(new FileData(buffer.toByteArray()));
        newAlp.setDataDescriptorPart(metadata.getDataDescriptorPart());
        newArchive.getLocal().append(newAlp);
        
        assert(currentFileOffset ==
                getNewCDF(newAlp.getLocalFilePart().getFileName())
                .getRelativeOffsetOfLocalHeader_32bit());
        currentFileOffset += newAlp.getStructureLength();
    }
}