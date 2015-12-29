//Copyright 2014 Google Inc. All rights reserved.
//
//Licensed under the Apache License, Version 2.0 (the "License");
//you may not use this file except in compliance with the License.
//You may obtain a copy of the License at
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
//Unless required by applicable law or agreed to in writing, software
//distributed under the License is distributed on an "AS IS" BASIS,
//WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//See the License for the specific language governing permissions and
//limitations under the License.
package com.google.archivepatcher;

@SuppressWarnings("all")

// This class was copied from:
// https://raw.githubusercontent.com/andrewhayden/uopt4j/master/src/MicroOptions.java

/**
 * A simple class for configuring and parsing command-line options.
 * @author Andrew Hayden
 * @version 1.1
 */
public class MicroOptions {
    /**
     * All option-related exceptions are instances of this base class.
     */
    public static class OptionException extends RuntimeException {
        public OptionException(String message) { super(message); } }

    /**
     * Thrown when the parser encounters an option that has not been defined.
     */
    public static class UnsupportedOptionException extends OptionException {
        public UnsupportedOptionException(String o) {
            super("Unsupported option '" + o + "'"); } }

    /**
     * Thrown when the parser can't find the argument for a non-unary option.
     */
    public static class MissingArgException extends OptionException {
        public MissingArgException(String o) {
            super("Missing argument for option '" + o + "'"); } }

    /**
     * Thrown when the parser can't find a required option.
     */
    public static class RequiredOptionException extends OptionException {
        public RequiredOptionException(String o) {
            super("Missing required option '" + o + "'"); } }

    /**
     * Core class for defining options using builder-style methods.
     */
    public class Option{
        private String n,d; // name, description
        private boolean u,r; // unary, required
        private Option(String n) { this.n = n; }

        /**
         * Provides a human-readable description for this option.
         * @param d the description
         * @return this option
         */
        public Option describedAs(String d) { this.d = d; return this; }

        /**
         * Specifies that this option is required.
         * @return this option
         */
        public Option isRequired() { this.r = true; return this; }

        /**
         * Specifies that this option takes no arguments.
         * @return this option
         */
        public Option isUnary() { this.u = true; return this; }
    }

    // All option metadata, sorted by option name.
    private final java.util.Map<String,Option> opts =
            new java.util.TreeMap<String,Option>();
    // All option values that have been parsed.
    private final java.util.Map<String,String> args =
            new java.util.HashMap<String, String>();

    /**
     * Creates an initially-empty set of options.
     */
    public MicroOptions() { super(); }

    /**
     * @return a human-readable usage string
     */
    public String usageString() {
        int max = 0; // max length of any option name, for alignment.
        for (String s : opts.keySet()) max = Math.max(s.length(), max);
        StringBuilder b = new StringBuilder();
        java.util.Iterator<Option> i = opts.values().iterator();
        while (i.hasNext()) {
            Option o = i.next();
            b.append(o.n.length() == 1 ? " -" : "--");
            b.append(String.format("%1$-" + max + "s", o.n));
            b.append(o.u ? "          " : " [ARG]    ");
            b.append(o.d == null ? "" : o.d + " ");
            b.append(o.r ? "(required)" : "(optional)");
            if (i.hasNext()) b.append('\n');
        }
        return b.toString();
    }

    /**
     * Parses the specified array of Strings and populates values for all
     * configured options.
     * @param strings e.g., the arguments on the command line
     */
    public void parse(String... strings) {
        for (int i = 0; i < strings.length; i++) {
            String k = strings[i]; String value = null;
            if (k.matches("-[[^\\s]&&[^-]]")) { k = k.substring(1); }
            else if (k.matches("--[\\S]{2,}")) { k = k.substring(2); }
            else throw new UnsupportedOptionException(k);
            if (!opts.containsKey(k)) throw new UnsupportedOptionException(k);
            Option o = opts.get(k);
            if (!o.u) {
                if (i + 1 >= strings.length) throw new MissingArgException(k);
                value = strings[++i];
            }
            args.put(k, value);
        }
        for (Option o : opts.values()) // ensure required opts are present
            if (o.r && !args.containsKey(o.n))
                throw new RequiredOptionException(o.n);
    }

    /**
     * Configure an optional, no-arg option having the specified name.
     * The name must be non-null, non-empty, and must not start with a hypen.
     * Spaces are allowed but discouraged since they complicate command-line
     * construction in most environments.
     * @param name the name to assign to the option
     * @return the option object, which can be used for further configuration
     * @throws UnsupportedOperationException if the name is null, the empty
     * string, or starts with a hyphen
     * @see MicroOptions.Option#describedAs(String)
     * @see MicroOptions.Option#isRequired()
     * @see MicroOptions.Option#isUnary()
     */
    public Option option(String name) {
        checkName(name);
        Option o = new Option(name); opts.put(name, o); return o; }

    /**
     * Checks a name for validity and throws an UnsupportedOptionException
     * if it is invalid.
     * @param name the name to check
     * @throws UnsupportedOperationException if the name is null, the empty
     * string, or starts with a hyphen 
     */
    private void checkName(String name) {
        if (name == null || name.length() == 0 || name.charAt(0) == '-')
            throw new UnsupportedOptionException("illegal name: " + name);
    }

    /**
     * Returns true iff the specified option was encountered during parsing.
     * @param option the option to look for
     * @return true if so, otherwise false
     * @throws UnsupportedOptionException if the option hasn't been defined
     * via {@link #option(String)};
     * also thrown if the name passed to the method is null, the empty string,
     * or starts with a hyphen
     */
    public boolean has(String option) {
        checkName(option);
        if (!opts.containsKey(option))
            throw new UnsupportedOptionException(option);
        return args.containsKey(option);
    }

    /**
     * Returns the argument that was associated with the specified option.
     * @param option the option to look up the argument for
     * @return the argument, iff the option takes and argument (is not unary)
     * and was encountered during parsing; otherwise, null.
     * @throws UnsupportedOptionException if the option hasn't been defined
     * via {@link #option(String)} or is unary (cannot have an argument);
     * also thrown if the name passed to the method is null, the empty string,
     * or starts with a hyphen
     */
    public String getArg(String option) { return getArg(option, null); }

    /**
     * Returns the argument that was associated with the specified option,
     * or the specified default value.
     * @param option the option to look up the argument for
     * @param defaultValue the value to return if the specified option was
     * not encountered during parsing
     * @return the argument, iff the option takes and argument and was not
     * encountered during parsing; otherwise, the specified default value
     * @throws UnsupportedOptionException if the option hasn't been defined
     * via {@link #option(String)} or is unary (cannot have an argument);
     * also thrown if the name passed to the method is null, the empty string,
     * or starts with a hyphen
     */
    public String getArg(String option, String defaultValue) {
        checkName(option);
        if (!opts.containsKey(option))
            throw new UnsupportedOptionException(option);
        if (opts.get(option).u)
            throw new OptionException("Option takes no arguments: " + option);
        return args.containsKey(option) ? args.get(option) : defaultValue;
    }
}