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

/**
 * A representation of one {@link PatchCommand} with its associated
 * {@link PatchPart}. An ordered sequence of these objects describes a series of
 * transformations that will convert on archive to another.
 */
public class PatchDirective {
    /**
     * The {@link PatchCommand} for this directive.
     */
    private final PatchCommand command;

    /**
     * The {@link PatchPart} corresponding to the {@link #command}.
     */
    private PatchPart part = null;

    /**
     * The offset, for {@link PatchCommand}s that need an offset (such as the
     * offset in the source file from which to begin copying data, as in the
     * case of {@link PatchCommand#COPY}). If no offset is required, this is
     * always null. 
     */
    private Integer offset = null;

    /**
     * Creates a new directive with the specified values.
     * @param command the command to set
     * @param part the part for the command, if any
     * @param offset the offset for the command, if any
     */
    private PatchDirective(final PatchCommand command, final PatchPart part,
        final Integer offset) {
        this.command = command;
        this.part = part;
        this.offset = offset;
    }

    /**
     * Creates a new directive representing {@link PatchCommand#NEW}.
     * @param part the part for the directive
     * @return the directive
     */
    public static PatchDirective NEW(final PatchPart part) {
        if (part == null) {
            throw new IllegalArgumentException("Part must not be null.");
        }
        if (!(part instanceof NewMetadata)) {
            throw new IllegalArgumentException("Part should be a " +
                NewMetadata.class.getName() + ", but was " +
                part.getClass().getName());
        }
        return new PatchDirective(PatchCommand.NEW, part, null);
    }

    /**
     * Creates a new directive representing {@link PatchCommand#COPY}.
     * @param offset the offset of the original resource in the source archive
     * @return the directive
     */
    public static PatchDirective COPY(final int offset) {
        return new PatchDirective(PatchCommand.COPY, null, offset);
    }

    /**
     * Creates a new directive representing {@link PatchCommand#REFRESH}.
     * @param part the part for the directive
     * @param offset the offset of the original resource in the source archive
     * @return the directive
     */
    public static PatchDirective REFRESH(final int offset,
        final PatchPart part) {
        if (part == null) {
            throw new IllegalArgumentException("Part must not be null.");
        }
        if (!(part instanceof RefreshMetadata)) {
            throw new IllegalArgumentException("Part should be a " +
                RefreshMetadata.class.getName() + ", but was " +
                part.getClass().getName());
        }
        return new PatchDirective(PatchCommand.REFRESH, part, offset);
    }

    /**
     * Creates a new directive representing {@link PatchCommand#BEGIN}.
     * @param part the part for the directive
     * @return the directive
     */
    public static PatchDirective BEGIN(final PatchPart part) {
        if (part == null) {
            throw new IllegalArgumentException("Part must not be null.");
        }
        if (!(part instanceof BeginMetadata)) {
            throw new IllegalArgumentException("Part should be a " +
                BeginMetadata.class.getName() + ", but was " +
                part.getClass().getName());
        }
        return new PatchDirective(PatchCommand.BEGIN, part, null);
    }

    /**
     * Creates a new directive representing {@link PatchCommand#PATCH}.
     * @param part the part for the directive
     * @param offset the offset of the original resource in the source archive
     * @return the directive
     */
    public static PatchDirective PATCH(final int offset,
        final PatchPart part) {
        if (part == null) {
            throw new IllegalArgumentException("Part must not be null.");
        }
        if (!(part instanceof PatchMetadata)) {
            throw new IllegalArgumentException("Part should be a " +
                PatchMetadata.class.getName() + ", but was " +
                part.getClass().getName());
        }
        return new PatchDirective(PatchCommand.PATCH, part, offset);
    }

    /**
     * If this directive makes use of an offset value, returns that offset
     * value; otherwise, throws an {@link UnsupportedOperationException}.
     * @return the offset, if an offset is used
     */
    public int getOffset() {
        if (offset == null) throw new UnsupportedOperationException(
                command.name() + " doesn't make use of an offset");
        return offset;
    }

    /**
     * If this directive makes use of a {@link PatchPart}, returns that part;
     * otherwise, throws an {@link UnsupportedOperationException}.
     * @return the part, if a part is used
     */
    public PatchPart getPart() {
        if (part == null) throw new UnsupportedOperationException(
                command.name() + " doesn't make use of a part");
        return part;
    }

    /**
     * Returns the command that this directive represents.
     * @return the command
     */
    public final PatchCommand getCommand() {
        return command;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((command == null) ? 0 : command.hashCode());
        result = prime * result + ((offset == null) ? 0 : offset.hashCode());
        result = prime * result + ((part == null) ? 0 : part.hashCode());
        return result;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        PatchDirective other = (PatchDirective) obj;
        if (command != other.command)
            return false;
        if (offset == null) {
            if (other.offset != null)
                return false;
        } else if (!offset.equals(other.offset))
            return false;
        if (part == null) {
            if (other.part != null)
                return false;
        } else if (!part.equals(other.part))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "PatchDirective [" +
            "command=" + command +
            ", part=" + part +
            ", offset=" + offset + "]";
    }
}