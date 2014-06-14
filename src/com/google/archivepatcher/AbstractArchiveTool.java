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


/**
 * Base class for tools.
 */
public abstract class AbstractArchiveTool {
    private final MicroOptions options = new MicroOptions();
    private boolean isVerbose = false;

    /**
     * Runs the tool. Subclasses should override or implement
     * {@link #configureOptions(MicroOptions)} and
     * {@link #run(MicroOptions)} to define behavior.
     * @param args the args passed to the tool
     */
    public final void run(String... args) throws Exception {
        configureOptions(options);
        try {
            options.parse(args);
        } catch (MicroOptions.OptionException e) {
            System.err.println(e.getMessage());
            System.err.println(options.usageString());
            System.exit(1);
        }
        if (options.has("help")) {
            System.out.println(options.usageString());
            System.exit(0);
        }
        if (options.has("verbose")) {
            isVerbose = true;
        }
        try {
            run(options);
        } catch (MicroOptions.OptionException e) {
            System.err.println(e.getMessage());
            System.err.println(options.usageString());
            System.exit(1);
        }
    }

    /**
     * Unconditionally log a message.
     * @param message message to log
     */
    protected final void log(String message) {
        System.out.println(message);
    }

    /**
     * Log a message if the tool is running in verbose mode.
     * @param message message to log
     */
    protected final void logVerbose(String message) {
        if (isVerbose) log(message);
    }

    /**
     * Subclasses can check the verbosity flag with this convenience method.
     * @return true if the tool should be verbose, otherwise false
     */
    protected final boolean isVerbose() {
        return isVerbose;
    }

    /**
     * Subclasses should configure options here. The default implementation
     * defines "--help" and "--verbose".
     */
    protected void configureOptions(MicroOptions options) {
        options.option("verbose").isUnary().describedAs("be verbose");
        options.option("help").isUnary().describedAs("show help and exit");
    }

    /**
     * Subclasses implement their run logic here.
     * @throws Exception if anything goes wrong. If it's an OptionException,
     * the usage string is printed as well.
     */
    protected abstract void run(MicroOptions options) throws Exception;
}
