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

package com.google.archivepatcher.parts;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * Convenience interface implemented by all logically distinct components of
 * a ZIP-like archive, such as local file headers and the
 * end-of-central-directory record.
 * <p>
 * Subclasses should make sure to implement {@link #hashCode()} and
 * {@link #equals(Object)}, as these interfaces nay be required in order for
 * patch generation to function correctly.
 */
public interface Part {
    /**
     * Reads the part from the specified input source.
     * 
     * @param input the source to read from
     * @throws IOException if unable to read the part
     */
    public void read(DataInput input) throws IOException;

    /**
     * Writes the part to the specified output location.
     * 
     * @param output the output to write to
     * @throws IOException if unable to write the part
     */
    public void write(DataOutput output) throws IOException;

    /**
     * Returns the number of bytes that this part would read if
     * {@link #read(DataInput)} was called, which is equal to the number of
     * bytes that would be written if {@link #write(DataOutput)} was called.
     * 
     * @return as described above
     */
    public int getStructureLength();

    // Redeclaration to make it clear that subclasses should extend.
    @Override public boolean equals(Object other);

    // Redeclaration to make it clear that subclasses should extend.
    @Override public int hashCode();
}