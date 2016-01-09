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

// TODO: This should be a Part.

/**
 * Represents the "central directory" section of an archive, which includes an
 * ordered list of {@link CentralDirectoryFile} entries and an
 * {@link EndOfCentralDirectory} record. Convenience methods are provided to
 * look up entries by their path information as well; these methods are
 * implemented for efficient lookups, and may generally be used without concern
 * for performance. The order of entries in the central directory is not
 * necessarily the same order as the order of entries in the
 * {@link LocalSection}.
 * <p>
 * Note that most values in the ZIP specification are unsigned, but Java only
 * provides signed values. As a result, most 16-bit values from the
 * specification are handled using "int" (32-bit) and most 32-bit values from
 * the specification are handled using "long" (64-bit). This allows the full
 * range of values from the specification to be utilized, but may mislead
 * callers into thinking that larger values can be used where they cannot.
 * <p>
 * To help avoid confusion, all getter and setter methods have a suffix
 * indicating the number of bits that can actually be used, i.e. "_16bit" and
 * "_32bit". These suffixes are <em>always</em> authoritative. Where practical,
 * runtime checks are added to make sure that illegal values are disallowed
 * (e.g., a value higher than 65535 passed to a "_16bit" setter method should
 * result in an {@link IllegalArgumentException}.
 */
public class CentralDirectorySection {
    private final List<CentralDirectoryFile> entries =
        new ArrayList<CentralDirectoryFile>();
    private final Map<String, Integer> entryIndexByPath =
        new HashMap<String, Integer>();
    private EndOfCentralDirectory eocd;

    /**
     * Looks up a {@link CentralDirectoryFile} entry by path. The
     * implementation ensures that these lookups are efficient and do not
     * require traversal of the entire central directory.
     * @param path the path to look up
     * @return the entry whose path exactly matches the specified argument, if
     * any; otherwise, null.
     * @see #indexOf(String)
     */
    public CentralDirectoryFile getByPath(final String path) {
        final Integer index = entryIndexByPath.get(path);
        if (index == null) return null;
        return entries.get(index);
    }

    /**
     * Returns the index of the entry having the specified path. The
     * implementation ensures that these lookups are efficient and do not
     * require traversal of the entire central directory.
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
     * Appends a new entry to the end of the central directory and indexes it
     * for efficient lookups.
     * @param entry the entry to add
     * @throws IllegalStateException if the central directory already contains
     * an entry with the specified path
     */
    public void append(final CentralDirectoryFile entry) {
        final String path = entry.getFileName();
        final CentralDirectoryFile old = getByPath(path);
        if (old != null) throw new IllegalStateException(
            "an entry with this path already exists: " + path);
        entries.add(entry);
        entryIndexByPath.put(path, entries.size() - 1);
    }

    /**
     * Returns an unmodifiable and <em>live</em> view of the entries in the
     * central directory. The order of the returned list is the same as the
     * order in the central directory.
     * @return such a list
     */
    public List<CentralDirectoryFile> entries() {
        return Collections.unmodifiableList(entries);
    }

    // Customization: Don't include the entryIndexByPath collection, as it is
    // just a shadow of the entries collection.
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((entries == null) ? 0 : entries.hashCode());
        result = prime * result + ((eocd == null) ? 0 : eocd.hashCode());
        return result;
    }

    // Customization: Don't include the entryIndexByPath collection, as it is
    // just a shadow of the entries collection.
    @Override
    public boolean equals(final Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        CentralDirectorySection other = (CentralDirectorySection) obj;
        if (entries == null) {
            if (other.entries != null)
                return false;
        } else if (!entries.equals(other.entries))
            return false;
        if (eocd == null) {
            if (other.eocd != null)
                return false;
        } else if (!eocd.equals(other.eocd))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "CentralDirectorySection [entries=" + entries +
            ", entryIndexByPath=" + entryIndexByPath +
            ", eocd=" + eocd + "]";
    }

    /**
     * Sets the {@link EndOfCentralDirectory} record for the central directory.
     * @param eocd the record to set
     */
    public void setEocd(final EndOfCentralDirectory eocd) {
        this.eocd = eocd;
    }

    /**
     * Returns the {@link EndOfCentralDirectory} record for the central
     * directory.
     * @return the record
     */
    public EndOfCentralDirectory getEocd() {
        return eocd;
    }

    /**
     * Returns the number of bytes that this section contains, which is the
     * sum of the lengths of all the parts within it.
     * @return as described
     */
    public int getStructureLength() {
        int length = 0;
        for (CentralDirectoryFile cdf : entries) {
            length += cdf.getStructureLength();
        }
        length += eocd.getStructureLength();
        return length;
    }
}