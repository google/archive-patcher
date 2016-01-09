// Copyright 2016 Google Inc. All rights reserved.
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

package com.google.archivepatcher.tools.transformer;

import java.util.LinkedList;
import java.util.List;

import com.google.archivepatcher.AbstractArchiveTool;
import com.google.archivepatcher.MicroOptions;

/**
 * A single point of entry for both {@link UncompressTransformerTool} and
 * {@link CompressTransformerTool}.
 */
public class TransformTool extends AbstractArchiveTool {

    /**
     * Main method. For usage instructions, run with "--help".
     * 
     * @param args arguments to the program
     * @throws Exception if anything goes wrong
     */
    public static void main(String... args) throws Exception {
        new TransformTool().run(args);
    }

    @Override
    public void configureOptions(MicroOptions options) {
        super.configureOptions(options);
        options.option("transform-to").isRequired().describedAs(
            "either 'uncompressed', to transform to the uncompressed space, " +
            "or 'original' to transform back to the original space.");
        options.option("archive").describedAs(
            "one archive to transform");
        options.option("archive-list").describedAs(
            "path to a file containing a list of archives to be transformed. " +
            "When using a list file, paths should be either absolute or " +
            "relative, in which case they are relative to the directory in " +
            "which the archive list file resides.");
        options.option("jobs").describedAs("run up to this many jobs in " +
            "parallel. The default is 1.");
        options.option("output-dir").isRequired().describedAs(
             "Write transformed archives to the specified output directory " +
             "with the suffix '.trx', along with transformation records " +
             "with the suffix '.rcd'.");
        options.option("metadata-in-dir").describedAs(
            "Read directives file or transformation record for each archive " +
            "processed from the specified directory. The metadata files " +
            "should have the same names as the archives, but should be " +
            "suffixed with '.directives' for directives files (--uncompress) " +
            "or 'rcd' for transformation records (--compress). Defaults to " +
            "whatever directory the archives are in.");
        options.option("verify").isUnary().describedAs(
            "Verify that the transformation is correct. This may be slow. " +
            "For --uncompress, the transformed archive will be recompressed " +
            "to ensure that the transformation is invertible. For " +
            "--compress, the signature of the archive is checked against " +
            "the signature of the original archive that was recorded in the " +
            "transformation record.");
    }

    @Override
    protected void run(MicroOptions options) throws Exception {
        final List<String> args = new LinkedList<String>();
        if (options.has("archive")) {
            args.add("--archive");
            args.add(options.getArg("archive"));
        } else if (options.has("archive-list")) {
            args.add("--archive-list");
            args.add(options.getArg("archive-list"));
        }
        args.add("--output-dir");
        args.add(options.getArg("output-dir"));
        args.add("--jobs");
        args.add(options.getArg("jobs", "1"));
        if (options.has("verbose")) {
            args.add("--verbose");
        }

        final String mode = options.getArg("transform-to");
        final AbstractArchiveTool tool;
        if ("uncompressed".equals(mode)) {
            tool = new UncompressTransformerTool();
            if (options.has("metadata-in-dir")) {
                args.add("--directives-in-dir");
                args.add(options.getArg("metadata-in-dir"));
            }
            if (options.has("verify")) {
                args.add("--verify");
            }
        } else if ("original".equals(mode)) {
            tool = new CompressTransformerTool();
            if (options.has("metadata-in-dir")) {
                args.add("--records-in-dir");
                args.add(options.getArg("metadata-in-dir"));
            }
        } else {
            throw new RuntimeException("Unsupported --transform-to: " + mode);
        }
        tool.run(args.toArray(new String[]{}));
    }
}
