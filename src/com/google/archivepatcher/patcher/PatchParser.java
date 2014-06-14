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

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

public class PatchParser {
    public static interface PartResolver {
        public Part partFor(PatchCommand command);
    }

    public static class DefaultPartResolver implements PartResolver {
        @Override
        public Part partFor(PatchCommand command) {
            switch (command) {
                case COPY: throw new IllegalArgumentException("copy command takes no part!");
                case BEGIN: return new BeginMetadata();
                case NEW: return new NewMetadata();
                case REFRESH: return new RefreshMetadata();
                case PATCH: return new PatchMetadata();
                default:
                    throw new IllegalArgumentException("Unknown command: " + command);
            }
        }
    }

    private final DataInput in;
    private final PartResolver resolver;

    public PatchParser(File in) throws IOException {
        this(new DataInputStream(new FileInputStream(in)));
    }

    public PatchParser(DataInput in) {
        this(in, new DefaultPartResolver());
    }

    public PatchParser(DataInput in, PartResolver resolver) {
        this.in = in;
        this.resolver = resolver;
    }

    public void init() throws IOException {
        final String magic = PatchMagic.readStandardHeader(in);
        final int version = PatchMagic.getVersion(magic);
        if (version < 1) {
            throw new IllegalStateException("bad version: " + version);
        }
    }

    public List<PatchDirective> readAll() throws IOException {
        List<PatchDirective> result = new LinkedList<PatchDirective>();
        PatchDirective directive = null;
        do {
            directive = read();
            if (directive != null) result.add(directive);
        } while (directive != null);
        return result;
    }

    /**
     * @return the next directive, or null if the end of the stream is reached.
     * @throws IOException if something goes wrong while reading
     */
    public PatchDirective read() throws IOException {
        final int commandBinary;
        try { 
           commandBinary = in.readUnsignedByte();
        } catch (EOFException e) {
            return null;
        }
        final PatchCommand command = PatchCommand.fromBinaryFormat(commandBinary);
        Part part = null;
        switch(command) {
            case COPY: // "COPY @OFFSET"
                return PatchDirective.COPY((int) IOUtils.readUnsignedInt(in));
            case BEGIN: // "END [partLength] [part=[EOCD]]"
                IOUtils.readUnsignedInt(in); // TODO: Guard part from reading past this boundary
                part = resolver.partFor(command);
                part.read(in);
                return PatchDirective.BEGIN(part);
            case NEW: // "NEW [partLength] [part=[CDFH][LFH][DATA][DD]?]"
                IOUtils.readUnsignedInt(in); // TODO: Guard part from reading past this boundary
                part = resolver.partFor(command);
                part.read(in);
                return PatchDirective.NEW(part);
            case REFRESH: // "REFRESH @OFFSET [partLength] [part=[META]]"
                part = resolver.partFor(command);
                int refreshOffset = (int) IOUtils.readUnsignedInt(in);
                IOUtils.readUnsignedInt(in); // TODO: Guard part from reading past this boundary
                part.read(in);
                return PatchDirective.REFRESH(refreshOffset, part);
            case PATCH: // "PATCH @OFFSET [patchLength] [patch]"
                part = resolver.partFor(command);
                int patchOffset = (int) IOUtils.readUnsignedInt(in);
                IOUtils.readUnsignedInt(in); // TODO: Guard part from reading past this boundary
                part.read(in);
                return PatchDirective.PATCH(patchOffset, part);
            default:
                throw new IllegalArgumentException(
                        "Unsupported command: " + command);
        }
    }
}