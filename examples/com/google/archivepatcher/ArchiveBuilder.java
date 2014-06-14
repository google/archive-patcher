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

import com.google.archivepatcher.util.SimpleArchive;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A feature-poor, simplistic implementation of an archive builder.
 * This is a toy meant for use as an example of how to build a zip and is in
 * no way suitable for production use.
 */
public class ArchiveBuilder {
    public static void main(String... args) throws Exception {
        if (args.length < 2) throw new RuntimeException("Usage: archivebuilder [archivename] [files...]");
        String archiveName = args[0];
        Set<File> filesToArchive = new LinkedHashSet<File>();
        for (int x=1; x<args.length; x++) {
            File file = new File(args[x]);
            if (file.isDirectory()) {
                addRecursive(file, filesToArchive);
            } else {
                filesToArchive.add(file);
            }
        }
        SimpleArchive archive = new SimpleArchive();
        final File cwd = new File(".");
        final String cwdPath = cwd.getCanonicalPath() + File.separatorChar;
        System.out.println("cwd=" + cwdPath);
        for (File file : filesToArchive) {
            String path = file.getCanonicalPath();
            if (path.startsWith(cwdPath)) {
                path = path.substring(cwdPath.length());
            }
            System.out.println("add: " + path);
            FileInputStream in = new FileInputStream(path);
            archive.add(path, in);
            in.close();
        }
        FileOutputStream out = new FileOutputStream(archiveName);
        archive.writeArchive(out);
        out.flush();
        out.close();
    }

    private static void addRecursive(final File file, Collection<File> destination) {
        for (File child : file.listFiles()) {
            if (child.isDirectory()) {
                addRecursive(child, destination);
            } else {
                destination.add(child);
            }
        }
    }
}