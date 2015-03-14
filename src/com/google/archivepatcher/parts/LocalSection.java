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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents the "local" section of an archive, which includes an ordered list
 * of {@link LocalSectionParts} entries. Convenience methods are provided to
 * look up entries by their path information as well; these methods are
 * implemented for efficient lookups, and may generally be used without concern
 * for performance. The order of entries in the local section is not necessarily
 * the same order as the order of entries in the
 * {@link CentralDirectorySection}.
 */
public class LocalSection {
    /**
     * The entries, in archive order, within this local section.
     */
    private final List<LocalSectionParts> entries =
        new ArrayList<LocalSectionParts>();

    /**
     * Efficient lookup table mapping paths to entries.
     */
    private final Map<String, Integer> entryIndexByPath =
        new HashMap<String, Integer>();

    /**
     * Looks up a {@link LocalSectionParts} entry by path. The
     * implementation ensures that these lookups are efficient and do not
     * require traversal of the entire local section.
     * @param path the path to look up
     * @return the entry whose path exactly matches the specified argument, if
     * any; otherwise, null.
     * @see #indexOf(String)
     */
    public LocalSectionParts getByPath(final String path) {
        final Integer index = entryIndexByPath.get(path);
        if (index == null) return null;
        return entries.get(index);
    }

    /**
     * Returns the index of the entry having the specified path. The
     * implementation ensures that these lookups are efficient and do not
     * require traversal of the entire local section.
     * @param path the path to look up
     * @return the index of the entry whose path exactly matches the specified
     * argument, if any; otherwise, -1.
     */
    public int indexOf(final String path) {
        final Integer index = entryIndexByPath.get(path);
        if (index == null) return -1;
        return index;
    }

    /**
     * Appends a new entry to the end of the local section and indexes it for
     * efficient lookups.
     * @param entry the entry to add
     * @throws IllegalStateException if the local section already contains an
     * entry with the specified path
     */
    public void append(LocalSectionParts entry) {
        final String path = entry.getLocalFilePart().getFileName();
        final LocalSectionParts old = getByPath(path);
        if (old != null) throw new IllegalStateException(
            "an entry with this path already exists: " + path);
        entries.add(entry);
        entryIndexByPath.put(path, entries.size() - 1);
    }

    /**
     * Returns an unmodifiable and <em>live</em> view of the entries in the
     * local section. The order of the returned list is the same as the order
     * in the local section.
     * @return such a list
     */
    public List<LocalSectionParts> entries() {
        return Collections.unmodifiableList(entries);
    }

    // Customization: Don't include the entryIndexByPath collection, as it is
    // just a shadow of the entries collection.
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((entries == null) ? 0 : entries.hashCode());
        return result;
    }

    // Customization: Don't include the entryIndexByPath collection, as it is
    // just a shadow of the entries collection.
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        LocalSection other = (LocalSection) obj;
        if (entries == null) {
            if (other.entries != null)
                return false;
        } else if (!entries.equals(other.entries))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "LocalSection [entries=" + entries
            + ", entryIndexByPath=" + entryIndexByPath + "]";
    }

    /**
     * Returns the number of bytes that this section contains, which is the
     * sum of the lengths of all the parts within it.
     * @return as described
     */
    public int getStructureLength() {
        int length = 0;
        for (LocalSectionParts lsp : entries) {
            length += lsp.getStructureLength();
        }
        return length;
    }
}