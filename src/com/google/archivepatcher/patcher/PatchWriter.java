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

/**
 * Generates a patch, converting the an ordered sequence of
 * {@link PatchDirective}s into a stream of bytes that can be read by a
 * {@link PatchParser}.
 * @see PatchParser
 */
public class PatchWriter {

    /**
     * The output stream.
     */
    private final DataOutput out;

    /**
     * Whether or not we have initialized by writing patch magic.
     */
    private boolean initialized = false;

    /**
     * Creates a new writer that will write to the specified output destination.
     * @param out the output destination to write to
     */
    public PatchWriter(DataOutput out) {
        this.out = out;
    }

    /**
     * Initializes the writer, writing the patch signature metadata to the
     * destination. Subseqeunt invocations are no-ops.
     * @return this object
     * @throws IOException if unable to write the metadata
     */
    public PatchWriter init() throws IOException {
        if (initialized) return this;
        initialized = true;
        IOUtils.writeUTF8(out, PatchMagic.getStandardHeader());
        return this;
    }

    /**
     * Writes one {@link PatchDirective} to the destination.
     * @param directive the directive to write
     * @return the number of bytes written to the destination
     * @throws IOException if unable to write to the destination
     * @throws IllegalArgumentException if the specified directive is not
     * supported
     */
    public int write(final PatchDirective directive) throws IOException {
        init();
        final PatchCommand command = directive.getCommand();
        out.write(command.signature);
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

    /**
     * Writes a {@link Collection} of {@link PatchDirective} objects to the
     * destination in the iteration order of the {@link Collection} (typically
     * a {@link java.util.List}.
     * @param directives the directives to write
     * @return the total number of bytes written to the destination
     * @throws IOException if unable to write to the destination
     */
    public int write(Collection<PatchDirective> directives) throws IOException {
        int totalWritten = 0;
        for (PatchDirective directive : directives) {
            totalWritten += write(directive);
        }
        return totalWritten;
    }

    /**
     * Convenience method that initializes a new {@link PatchWriter} and
     * immediately writes all records to the specified destination. This is the
     * same as calling the constructor, then {@link #init()} and
     * {@link #write(Collection)}.
     * @param directives the directives to write
     * @param out the destination to write to
     * @return the total number of bytes written to the destination
     * @throws IOException if unable to write to the destination
     */
    public static int write(Collection<PatchDirective> directives,
        DataOutput out) throws IOException {
        return new PatchWriter(out).write(directives);
    }
}