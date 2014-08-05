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
 * Base class for tools in this package, with built-in command line parameter
 * parsing and verbosity flags.
 */
public abstract class AbstractArchiveTool {
    /**
     * The options object used for configuring command-line parameters.
     */
    private final MicroOptions options = new MicroOptions();

    /**
     * Whether or not the tool is running in verbose mode. In verbose mode,
     * extra information is typically output.
     */
    private boolean isVerbose = false;

    /**
     * Subclasses can configure their own command-line options by overriding
     * this method. The options "--help" and "--verbose" are configured
     * automatically. The default implementation does nothing.
     * @param options the options object to be configured
     */
    protected void configureOptions(MicroOptions options) {}

    /**
     * Configures default options and invokes the subclasses' implementation
     * of {@link #configureOptions(MicroOptions)}.
     */
    private void configureOptions() {
        configureOptions(options);
        options.option("verbose").isUnary().describedAs("be verbose");
        options.option("help").isUnary().describedAs("show help and exit");
    }

    /**
     * Runs the tool. Subclasses should override or implement
     * {@link #configureOptions(MicroOptions)} and
     * {@link #run(MicroOptions)} to define custom behavior.
     * @param args the args passed to the tool
     * @throws Exception if something goes wrong
     */
    public final void run(String... args) throws Exception {
        configureOptions();
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
     * Unconditionally log a message, regardless of the value of
     * {@link #isVerbose()}. Appends a newline automatically.
     * @param message message to log
     */
    protected final void log(String message) {
        System.out.println(message);
    }

    /**
     * Log a message if and only if {@link #isVerbose()} returns true.
     * Appends a newline automatically.
     * @param message message to log
     */
    protected final void logVerbose(String message) {
        if (isVerbose()) log(message);
    }

    /**
     * Subclasses can check the verbosity flag with this convenience method.
     * @return true if the tool should be verbose, otherwise false
     */
    protected final boolean isVerbose() {
        return isVerbose;
    }

    /**
     * Subclasses implement their run logic here.
     * @param options the options that were parsed from the command line
     * @throws Exception if anything goes wrong. If it's an OptionException,
     * the usage string is printed as well.
     */
    protected abstract void run(MicroOptions options) throws Exception;
}
