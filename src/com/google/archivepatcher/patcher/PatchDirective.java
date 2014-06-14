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

public class PatchDirective {
    private final PatchCommand command;
    private Part part = null;
    private Integer offset = null;
    
    private PatchDirective(PatchCommand command) {
        this.command = command;
    }

    public static PatchDirective NEW(Part part) {
        PatchDirective result = new PatchDirective(PatchCommand.NEW);
        result.part = part;
        return result;
    }

    public static PatchDirective COPY(int offset) {
        PatchDirective result = new PatchDirective(PatchCommand.COPY);
        result.offset = offset;
        return result;
    }

    public static PatchDirective REFRESH(int offset, Part part) {
        PatchDirective result = new PatchDirective(PatchCommand.REFRESH);
        result.offset = offset;
        result.part = part;
        return result;
    }

    public static PatchDirective BEGIN(Part part) {
        PatchDirective result = new PatchDirective(PatchCommand.BEGIN);
        result.part = part;
        return result;
    }

    public static PatchDirective PATCH(int offset, Part part) {
        PatchDirective result = new PatchDirective(PatchCommand.PATCH);
        result.offset = offset;
        result.part = part;
        return result;
    }

    public int getOffset() {
        if (offset == null) throw new UnsupportedOperationException(
                command.name() + " doesn't support offset");
        return offset;
    }

    public Part getPart() {
        if (part == null) throw new UnsupportedOperationException(
                command.name() + " doesn't support part");
        return part;
    }
    
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
    public boolean equals(Object obj) {
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
        return "PatchDirective [command=" + command + ", part=" + part + ", offset=" + offset
                + "]";
    }
}