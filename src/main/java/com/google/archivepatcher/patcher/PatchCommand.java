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
 * An enumeration of all supported commands in the patch format handled by this
 * library. Each enumerated command has a value associated with it; this value
 * is used as the signature for the command when written to or read from a
 * patch.
 * <p>
 * Each value is documented as if discussing a patch that produces a "new"
 * archive from an "old" archive by applying a series of these command objects.
 */
public enum PatchCommand {
    /**
     * Declares a resource that is only in the "new" archive <em>or</em> whose
     * file data delta between "old" and "new" cannot be represented more
     * compactly than by embedding a complete copy of the "new" data.
     */
    NEW(1),

    /**
     * Declares a resource that is identical in both the "old" and "new"
     * archives.
     */
    COPY(2),

    /**
     * Declares a resource whose file data is identical in both the "old" and
     * "new" archives, but whose metadata (e.g., last modified date) has
     * changed.
     */
    REFRESH(3),

    /**
     * Declares the start of a patch, with associated metadata. 
     */
    BEGIN(4),

    /**
     * Declares a resource whose file data (and possibly metadata) has changed
     * in the "new" archive, where the file data delta can be more compactly
     * represented by embedding a logical "diff" than a complete copy of the
     * "new" data.
     */
    PATCH(5);

    /**
     * The signature for this command when written to or read from a patch.
     */
    public final int signature;

    /**
     * Constructs a new enumerated command having the specified value.
     * 
     * @param binaryFormat the signature
     */
    private PatchCommand(final int binaryFormat) {
        this.signature = binaryFormat;
    }

    /**
     * Returns the {@link PatchCommand} whose signature matches the specified
     * value, if any.
     * @param signature the signature to look up a command for
     * @return the command corresponding to the signature
     * @throws IllegalArgumentException if there is no command whose signature
     * matches the specified value
     */
    public final static PatchCommand fromSignature(final int signature) {
        switch (signature) {
            case 1: return NEW;
            case 2: return COPY;
            case 3: return REFRESH;
            case 4: return BEGIN;
            case 5: return PATCH;
            default:
                throw new IllegalArgumentException(
                    "No such command: " + signature);
        }
    }
}