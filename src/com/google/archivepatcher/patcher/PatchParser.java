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

/**
 * Parses a patch, converting a stream of bytes written by a {@link PatchWriter}
 * into an ordered sequence of {@link PatchDirective}s.
 * @see PatchWriter
 */
public class PatchParser {

    /**
     * The input stream.
     */
    private final DataInput in;

    /**
     * The {@link PartResolver} that converts {@link PatchCommand}s to
     * {@link Part} objects.
     */
    private final PartResolver resolver;

    /**
     * Whether or not we have initialized by reading patch magic.
     */
    private boolean initialized = false;

    /**
     * Creates a new parser that will read from the specified file using the
     * default {@link PartResolver}.
     * @param in the file to read from
     * @throws IOException if there is a problem opening the file
     */
    public PatchParser(File in) throws IOException {
        this(new DataInputStream(new FileInputStream(in)));
    }

    /**
     * Creates a new parser that will read from the specified input source
     * using the default {@link PartResolver}.
     * @param in the input source to read from
     */
    public PatchParser(DataInput in) {
        this(in, new DefaultPartResolver());
    }

    /**
     * Creates a new parser that will read from the specified input source
     * using the specified {@link PartResolver}.
     * @param in the input source to read from
     * @param resolver the resolver to use for creating {@link Part} objects
     */
    public PatchParser(DataInput in, PartResolver resolver) {
        this.in = in;
        this.resolver = resolver;
    }

    /**
     * Initializes the parser, reading the patch signature metadata from the
     * input source and aborting if the metadata is corrupt. Subsequent
     * invocations are no-ops.
     * @return this object
     * @throws PatchParseException if the patch is corrupt
     */
    public PatchParser init() throws PatchParseException {
        if (initialized) return this;
        initialized = true;
        final String magic = PatchMagic.readStandardHeader(in);
        final int version = PatchMagic.getVersion(magic);
        if (version < 1) {
            throw new PatchParseException("bad version: " + version);
        }
        return this;
    }

    /**
     * Reads all remaining {@link PatchDirective}s from the input source and
     * returns them immediately.
     * @return an ordered {@link List} of all remaining {@link PatchDirective}s
     * @throws PatchParseException if the patch is corrupt
     */
    public List<PatchDirective> readAll() throws PatchParseException {
        List<PatchDirective> result = new LinkedList<PatchDirective>();
        PatchDirective directive = null;
        do {
            directive = read();
            if (directive != null) result.add(directive);
        } while (directive != null);
        return result;
    }

    /**
     * Reads the next directive from the patch.
     * @return the next directive, or null if the end of the stream is reached.
     * @throws PatchParseException if the patch is corrupt
     */
    public PatchDirective read() throws PatchParseException {
        init();
        final int commandBinary;
        try { 
           commandBinary = in.readUnsignedByte();
        } catch (EOFException e) {
            return null;
        } catch (IOException e) {
            throw new PatchParseException(
                "Unable to read command signature", e);
        }
        final PatchCommand command =
            PatchCommand.fromSignature(commandBinary);

        // TODO: Prevent parts from reading past their bounds.
        switch(command) {
            case COPY:
                return parseCopyCommand();
            case BEGIN:
                return parseBeginCommand();
            case NEW:
                return parseNewCommand();
            case REFRESH:
                return parseRefreshCommand();
            case PATCH:
                return parsePatchCommand();
            default:
                throw new PatchParseException(
                        "Unsupported patch command: " + command);
        }
    }

    /**
     * Reads a patch source in which a {@link PatchCommand#COPY} has been
     * detected and returns the corresponding {@link PatchDirective}.
     * @return the {@link PatchDirective}
     * @throws PatchParseException if unable to parse the input source
     */
    private PatchDirective parseCopyCommand() throws PatchParseException {
        // Format: "COPY @OFFSET"
        int offset;
        try {
            offset = (int) IOUtils.readUnsignedInt(in);
        } catch (IOException e) {
            throw new PatchParseException(
                "Can't read offset for COPY command", e);
        }
        return PatchDirective.COPY(offset);
    }

    /**
     * Reads a patch source in which a {@link PatchCommand#PATCH} has been
     * detected and returns the corresponding {@link PatchDirective}.
     * @return the {@link PatchDirective}
     * @throws PatchParseException if unable to parse the input source
     */
    private PatchDirective parsePatchCommand() throws PatchParseException {
        // Format: "PATCH @OFFSET [patchLength] [patch]"
        final Part part = resolver.partFor(PatchCommand.PATCH);
        int offset;
        try {
            offset = (int) IOUtils.readUnsignedInt(in);
        } catch (IOException e) {
            throw new PatchParseException(
                "Can't read offset for PATCH command", e);
        }
        try {
            IOUtils.readUnsignedInt(in);
        } catch (IOException e) {
            throw new PatchParseException(
                "Can't read part length for PATCH command", e);
        }
        try {
            part.read(in);
        } catch (IOException e) {
            throw new PatchParseException(
                "Can't parse content for PATCH command", e);
        }
        return PatchDirective.PATCH(offset, part);
    }

    /**
     * Reads a patch source in which a {@link PatchCommand#REFRESH} has been
     * detected and returns the corresponding {@link PatchDirective}.
     * @return the {@link PatchDirective}
     * @throws PatchParseException if unable to parse the input source
     */
    private PatchDirective parseRefreshCommand() throws PatchParseException {
        // Format: "REFRESH @OFFSET [partLength] [part=[META]]"
        final Part part = resolver.partFor(PatchCommand.REFRESH);
        int offset;
        try {
            offset = (int) IOUtils.readUnsignedInt(in);
        } catch (IOException e) {
            throw new PatchParseException(
                "Can't read offset for REFRESH command", e);
        }
        try {
            IOUtils.readUnsignedInt(in);
        } catch (IOException e) {
            throw new PatchParseException(
                "Can't read part length for REFRESH command", e);
        }
        try {
            part.read(in);
        } catch (IOException e) {
            throw new PatchParseException(
                "Can't parse content for REFRESH command", e);
        }
        return PatchDirective.REFRESH(offset, part);
    }

    /**
     * Reads a patch source in which a {@link PatchCommand#NEW} has been
     * detected and returns the corresponding {@link PatchDirective}.
     * @return the {@link PatchDirective}
     * @throws PatchParseException if unable to parse the input source
     */
    private PatchDirective parseNewCommand() throws PatchParseException {
        // Format: "NEW [partLength] [part=[CDFH][LFH][DATA][DD]?]"
        final Part part = resolver.partFor(PatchCommand.NEW);
        try {
            IOUtils.readUnsignedInt(in);
        } catch (IOException e) {
            throw new PatchParseException(
                "Can't read part length for NEW command", e);
        }
        try {
            part.read(in);
        } catch (IOException e) {
            throw new PatchParseException(
                "Can't parse content for NEW command", e);
        }
        return PatchDirective.NEW(part);
    }

    /**
     * Reads a patch source in which a {@link PatchCommand#BEGIN} has been
     * detected and returns the corresponding {@link PatchDirective}.
     * @return the {@link PatchDirective}
     * @throws PatchParseException if unable to parse the input source
     */
    private PatchDirective parseBeginCommand() throws PatchParseException {
        // Format: // "BEGIN [partLength] [part=[EOCD]]"
        final Part part = resolver.partFor(PatchCommand.BEGIN);
        try {
            IOUtils.readUnsignedInt(in);
        } catch (IOException e) {
            throw new PatchParseException(
                "Can't read part length for BEGIN command", e);
        }
        try {
            part.read(in);
        } catch (IOException e) {
            throw new PatchParseException(
                "Can't parse content for BEGIN command", e);
        }
        return PatchDirective.BEGIN(part);
    }
}