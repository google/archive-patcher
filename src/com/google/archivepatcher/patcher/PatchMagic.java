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

package com.google.archivepatcher.patcher;

import com.google.archivepatcher.util.IOUtils;

import java.io.DataInput;
import java.io.IOException;

public class PatchMagic {
    private final static String MAGIC = "ARCPAT";
    private final static int VERSION = 1;
    private final static String VERSION_STRING = "01";
    private final static String MAGIC_REGEX = "ARCPAT[0-9][0-9]";
    public static String getStandardHeader() {
        return MAGIC + VERSION_STRING;
    }
    public static String readStandardHeader(DataInput in) throws IOException {
        String header = IOUtils.readUTF8(in, getStandardHeader().length());
        if (!header.matches(MAGIC_REGEX)) throw new IllegalArgumentException(
                "Bad header: " + header);
        return header;
    }
    public static int getVersion() {
        return VERSION;
    }
    public static int getVersion(String header) {
        if (!header.matches(MAGIC_REGEX)) throw new IllegalArgumentException(
                "Bad header: " + header);
        return Integer.parseInt(header.substring(MAGIC.length()));
    }
}
