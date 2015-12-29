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

package com.google.archivepatcher.tools.policy;

import java.util.Comparator;

import com.google.archivepatcher.parts.CentralDirectoryFile;

/**
 * A comparator that compares central directory file entries by the offsets
 * of their local parts.
 */
public final class CentralDirectoryRelativeOffsetComparator
implements Comparator<CentralDirectoryFile> {
    public int compare(CentralDirectoryFile object1,
        CentralDirectoryFile object2) {
        Long offset1 =
            object1.getRelativeOffsetOfLocalHeader_32bit();
        Long offset2 =
            object2.getRelativeOffsetOfLocalHeader_32bit();
        return offset1.compareTo(offset2);
    }
}