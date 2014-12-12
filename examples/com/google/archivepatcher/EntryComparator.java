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

package com.google.archivepatcher;

import com.google.archivepatcher.util.DescriptiveComparator;
import com.google.archivepatcher.util.SimpleArchive;


/**
 * A tool for comparing corresponding entries in two archives for equivalence
 * and describing any differences found in a human-readable manner.
 */
public class EntryComparator extends AbstractArchiveTool {

    /**
     * Main method. For usage instructions, run with "--help".
     * 
     * @param args arguments to the program
     * @throws Exception if anything goes wrong
     */
    public static void main(String... args) throws Exception {
        new EntryComparator().run(args);
    }

    @Override
    public void configureOptions(MicroOptions options) {
        super.configureOptions(options);
        options.option("archive1").isRequired().describedAs(
            "the first archive to compare");
        options.option("path1").isRequired().describedAs(
            "the path in the first archive of the entry to compare");
        options.option("archive2").isRequired().describedAs(
            "the second archive to compare");
        options.option("path2").describedAs(
            "the path in the second archive of the entry to compare " +
            "(optional; if unspecified, defaults to path1)");
    }

    @Override
    protected void run(MicroOptions options) throws Exception {
        // Extract and process options
        final String archive1Path = options.getArg("archive1");
        final Archive a1 = SimpleArchive.fromFile(archive1Path);
        final String archive2Path = options.getArg("archive2");
        final Archive a2 = SimpleArchive.fromFile(archive2Path);
        final String path1 = options.getArg("path1");
        final String path2 = options.getArg("path2", path1);

        // Do it.
        DescriptiveComparator comparator = new DescriptiveComparator();
        final boolean same = comparator.compare(a1, path1, a2, path2);
        if (!same) System.exit(-1);
        log("No differences.");
    }

}