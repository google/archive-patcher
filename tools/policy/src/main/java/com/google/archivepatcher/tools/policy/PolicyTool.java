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

import java.io.File;
import java.util.Collections;
import java.util.List;

import com.google.archivepatcher.AbstractArchiveTool;
import com.google.archivepatcher.Archive;
import com.google.archivepatcher.MicroOptions;
import com.google.archivepatcher.util.MiscUtils;

/**
 * A tool that checks an archive for compliance with policies.
 */
public class PolicyTool extends AbstractArchiveTool {
    @Override
    public void configureOptions(MicroOptions options) {
        super.configureOptions(options);
        options.option("archive").describedAs(
            "one archive to scan for");
        options.option("archive-list").describedAs(
            "path to a file containing a list of archives to be processed. " +
            "When using a list file, paths should be either absolute or " +
            "relative, in which case they are relative to the directory in " +
            "which the archive list file resides.");
    }

    /**
     * Main method. For usage instructions, run with "--help".
     * 
     * @param args arguments to the program
     * @throws Exception if anything goes wrong
     */
    public static void main(String... args) throws Exception {
        new PolicyTool().run(args);
    }

    @Override
    protected void run(MicroOptions options) throws Exception {
        if (!options.has("archive") && !options.has("archive-list")) {
            throw new IllegalArgumentException("specify one of --archive " +
                "or --archive-list");
        }
        if (options.has("archive") && options.has("archive-list")) {
            throw new IllegalArgumentException("specify one of --archive " +
                "or --archive-list, but not both");
        }
        final List<File> archives;
        if (options.has("archive")) {
            archives = Collections.singletonList(
                new File(options.getArg("archive")));
        } else {
            archives = MiscUtils.getFileList(options.getArg("archive-list"));
        }

        Policy policy = new Policy();
        final boolean allowRedundantDataDescriptors = true;
        policy.addRules(
            new DisallowArchiveCommentsRule(),
            new DisallowArchiveGapsRule(),
            new DisallowCentralDirectoryExtrasRule(),
            new DisallowDuplicatePathsRule(),
            new DisallowEntriesFromOtherDisksRule(),
            new DisallowEntryCommentsRule(),
            new DisallowMismatchedDataRule(allowRedundantDataDescriptors),
            new DisallowMultiDiskRule(),
            new DisallowNonAlignmentLocalExtrasRule(),
            new DisallowNonDeflateCompressionRule(),
            new DisallowNonNullExtrasRule(),
            new DisallowNonTrivialPathsRule());
        for (final File archiveFile : archives) {
            Archive archive = Archive.fromFile(archiveFile.getAbsolutePath());
            ComplianceReport result = policy.checkCompliance(archive);
            System.out.println(result);
        }
    }
}
