// Copyright 2015 Google Inc. All rights reserved.
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

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * Convenience interface implemented by all logically distinct components of
 * an archive patch, such as various metadata objects and commands.
 * <p>
 * Subclasses should make sure to implement {@link #hashCode()} and
 * {@link #equals(Object)}, as these interfaces may be required in order for
 * patch generation to function correctly.
 */
public abstract class PatchPart {
    /**
     * By default parts are created with the latest version. When read, the
     * version may be downgraded.
     */
    private ArchivePatcherVersion patchVersionForReading =
        ArchivePatcherVersion.buildVersion();

    /**
     * Reads the part from the specified input source.
     * 
     * @param input the source to read from
     * @param patchVersion the version of the patch being read
     * @throws IOException if unable to read the part
     */
    public void read(DataInput input, ArchivePatcherVersion patchVersion)
        throws IOException {
        this.setPatchVersionForReading(patchVersion);
    }

    /**
     * Writes the part to the specified output location. Always writes the
     * latest version format.
     * 
     * @param output the output to write to
     * @throws IOException if unable to write the part
     */
    public abstract void write(DataOutput output) throws IOException;

    /**
     * Returns the number of bytes that this part would read if
     * {@link #read(DataInput, ArchivePatcherVersion)} was called, which is
     * equal to the number of bytes that would be written if
     * {@link #write(DataOutput)} was called.
     * 
     * @return as described above
     */
    public abstract int getStructureLength();

    // Redeclaration to make it clear that subclasses should extend.
    @Override public abstract boolean equals(Object other);

    // Redeclaration to make it clear that subclasses should extend.
    @Override public abstract int hashCode();

    /**
     * Returns the version of the patch for reading the part. The part is always
     * written in the latest format.  By default, the version is set to the
     * latest supported.
     * @return the version
     */
    protected ArchivePatcherVersion getPatchVersionForReading() {
        return patchVersionForReading;
    }

    /**
     * Sets the version of the patch for reading the part. By default, the
     * version is set to the latest supported.
     * @param patchVersion the version to use
     */
    protected void setPatchVersionForReading(
        ArchivePatcherVersion patchVersion) {
        this.patchVersionForReading = patchVersion;
    }
}
