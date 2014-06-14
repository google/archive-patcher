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

package com.google.archivepatcher.patcher;

import com.google.archivepatcher.parts.Part;
import com.google.archivepatcher.util.IOUtils;

import java.io.DataOutput;
import java.io.IOException;
import java.util.Collection;

public class PatchWriter {
    private final DataOutput out;

    public PatchWriter(DataOutput out) {
        this.out = out;
    }

    public PatchWriter init() throws IOException {
        IOUtils.writeUTF8(out, PatchMagic.getStandardHeader());
        return this;
    }

    public int write(PatchDirective directive) throws IOException {
        final PatchCommand command = directive.getCommand();
        out.write(command.binaryFormat);
        int written = 1;
        Part part = null;
        int partLength = -1;
        switch(directive.getCommand()) {
            case COPY: // "COPY @OFFSET"
                IOUtils.writeUnsignedInt(out, directive.getOffset());
                return written + 4;
            case BEGIN: // "END [partLength] [part=[EOCD]]"
            case NEW: // "NEW [partLength] [part=[CDFH][LFH][DATA][DD]?]"
                part = directive.getPart();
                partLength = part.getStructureLength();
                IOUtils.writeUnsignedInt(out, partLength);
                part.write(out);
                return written + 4 + partLength;
            case REFRESH: // "REFRESH @OFFSET [partLength] [part=[METADATA]]"
                part = directive.getPart();
                partLength = part.getStructureLength();
                IOUtils.writeUnsignedInt(out, directive.getOffset());
                IOUtils.writeUnsignedInt(out, partLength);
                part.write(out);
                return written + 4 + 4 + partLength;
            case PATCH: // "PATCH "OFFSET [patchLength] [patch]"
                part = directive.getPart();
                partLength = part.getStructureLength();
                IOUtils.writeUnsignedInt(out, directive.getOffset());
                IOUtils.writeUnsignedInt(out, partLength);
                part.write(out);
                return written + 4 + 4 + partLength;
            default:
                throw new IllegalArgumentException(
                        "Unsupported command: " + directive.getCommand());
        }
    }

    public int write(Collection<PatchDirective> directives) throws IOException {
        int totalWritten = 0;
        for (PatchDirective directive : directives) {
            totalWritten += write(directive);
        }
        return totalWritten;
    }

    public static int write(Collection<PatchDirective> directives, DataOutput out) throws IOException {
        return new PatchWriter(out).init().write(directives);
    }
}