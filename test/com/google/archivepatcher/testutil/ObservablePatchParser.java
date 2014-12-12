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

package com.google.archivepatcher.testutil;

import java.io.DataInput;
import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import com.google.archivepatcher.patcher.PartResolver;
import com.google.archivepatcher.patcher.PatchDirective;
import com.google.archivepatcher.patcher.PatchParseException;
import com.google.archivepatcher.patcher.PatchParser;


/**
 * Utility class to monitor patch parsing and check that patches contain
 * expected behavior.
 */
@SuppressWarnings("javadoc")
public class ObservablePatchParser extends PatchParser {
    /** Number of times init() has been invoked. */
    public int initInvoactionCount = 0;

    /** Number of times readAll() has been invoked. */
    public int readAllInvocationCount = 0;

    /** Number of times read() has been invoked. */
    public int readInvocationCount = 0;

    /** All the directives that have been parsed. */
    public final List<PatchDirective> directivesRead =
        new LinkedList<PatchDirective>();

    public ObservablePatchParser(DataInput in, PartResolver resolver) {
        super(in, resolver);
    }

    public ObservablePatchParser(DataInput in) {
        super(in);
    }

    public ObservablePatchParser(File in) throws IOException {
        super(in);
    }

    @Override
    public PatchParser init() throws PatchParseException {
        initInvoactionCount++;
        return super.init();
    }

    @Override
    public List<PatchDirective> readAll() throws PatchParseException {
        readAllInvocationCount++;
        return super.readAll();
    }

    @Override
    public PatchDirective read() throws PatchParseException {
        readInvocationCount++;
        PatchDirective result = super.read();
        if (result != null) directivesRead.add(result);
        return result;
    }

}
