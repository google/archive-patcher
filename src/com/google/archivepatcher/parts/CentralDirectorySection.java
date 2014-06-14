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

public class CentralDirectorySection {
    private List<CentralDirectoryFile> entries = new ArrayList<CentralDirectoryFile>();
    private Map<String, Integer> entryIndexByPath = new HashMap<String, Integer>();
    private EndOfCentralDirectory eocd;

    public void setEocd(EndOfCentralDirectory eocd) {
        this.eocd = eocd;
    }

    public EndOfCentralDirectory getEocd() {
        return eocd;
    }

    public CentralDirectoryFile getByPath(String path) {
        Integer index = entryIndexByPath.get(path);
        if (index == null) return null;
        return entries.get(index);
    }

    public int indexOf(String path) {
        Integer index = entryIndexByPath.get(path);
        if (index == null) return -1;
        return index;
    }

    public void append(CentralDirectoryFile entry) {
        CentralDirectoryFile old = getByPath(entry.getFileName());
        if (old != null) throw new IllegalStateException("path already exists: " + entry.getFileName());
        entries.add(entry);
        entryIndexByPath.put(entry.getFileName(), entries.size() - 1);
    }

    public List<CentralDirectoryFile> entries() {
        return Collections.unmodifiableList(entries);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((entries == null) ? 0 : entries.hashCode());
        result = prime * result + ((entryIndexByPath == null) ? 0 : entryIndexByPath.hashCode());
        result = prime * result + ((eocd == null) ? 0 : eocd.hashCode());
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
        CentralDirectorySection other = (CentralDirectorySection) obj;
        if (entries == null) {
            if (other.entries != null)
                return false;
        } else if (!entries.equals(other.entries))
            return false;
        if (entryIndexByPath == null) {
            if (other.entryIndexByPath != null)
                return false;
        } else if (!entryIndexByPath.equals(other.entryIndexByPath))
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
        return "ArchiveCentralDirectory [entries=" + entries + ", entryIndexByPath="
                + entryIndexByPath + ", eocd=" + eocd + "]";
    }
}