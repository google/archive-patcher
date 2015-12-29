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

package com.google.archivepatcher.delta;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * An interface for implementing a delta applier. When applying patches, a
 * delta applier is configured to apply a previously-generated delta to an old
 * old versions of an entry in an archive, producing the data for the new entry
 * in the archive. 
 */
public interface DeltaApplier {
    /**
     * Applies the delta from a corresponding {@link DeltaGenerator} to the
     * data in oldData, writing the final artifact into newOut.
     * 
     * @param oldData the old data
     * @param deltaData the delta 
     * @param newOut output stream where the final result is written
     * @throws IOException if something goes awry while reading or writing
     */
    public void applyDelta(InputStream oldData, InputStream deltaData,
            OutputStream newOut) throws IOException;

    /**
     * Returns the unique ID of this delta applier. This should match the ID
     * returned by the identically-named method in the corresponding
     * DeltaGenerator implementation.
     * @return the ID, which must be greater than zero.
     */
    public abstract int getId();

}
