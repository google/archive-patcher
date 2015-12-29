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

package com.google.archivepatcher.enforcer;

import com.google.archivepatcher.parts.CentralDirectoryFile;

/**
 * Disallows the use of anything other than file separators and non-control
 * characters in path names. This is a little more strict than the PKZIP
 * APPNOTE, whose requirements are as follows:
 * <ul>
 * <li>The path stored MUST not contain a drive or device letter, or a leading
 *     slash.
 * <li>All slashes MUST be forward slashes '/'.
 * <li>If input came from standard input, there is no file name field.  
 * </ul>
 * This class will reject an empty path. For simplicity, it only accepts
 * letters, digits, whitespace and the following characters:
 * '/', '.', '_' and '-'. 
 */
public class DisallowNonTrivialPathsRule extends ArchiveRule {

    @Override
    protected void checkInternal() {
        for (CentralDirectoryFile cdf :
            archive.getCentralDirectory().entries()) {
            final String path = cdf.getFileName();
            checkPath(path);
        }
    }

    /**
     * Checks if the path is ok or not.
     * @param path the path to check
     */
    private final void checkPath(final String path) {
        if (path.length() == 0) {
            notOk("<empty path>", "path is empty");
            return;
        }
        if (path.startsWith("/")) {
            notOk(path, "path may not have a leading slash");
        }
        if (path.contains("..")) {
            // Valid, but dangerous as some systems probably won't attempt
            // to escape reserved stuff like this in the path.
            notOk(path, "path contains relative indirection");
        }
        for (char c : path.toCharArray()) {
            if (c == '\\') {
                notOk(path, "file name contains backslash");
            } else if (Character.isISOControl(c)) {
                notOk(path, "file name contains control code");
            } else if (!isSaneChar(c)) {
                notOk(path, "file name contains weird text");
            }
        }
    }

    /**
     * Accept letters, digits, whitespace, and a few punctuation characters.
     * @param c the character to check
     * @return true if the character is sane
     */
    private final static boolean isSaneChar(char c) {
        return Character.isLetterOrDigit(c) || Character.isWhitespace(c) ||
            ("/._-".indexOf(c) >= 0);
    }
}