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


// This class was copied from:
// https://github.com/andrewhayden/uopt4j/blob/master/altsrc-compact/MicroOptions.java
public class MicroOptions {
 public static class OptionException extends RuntimeException {
     public OptionException(String message) { super(message); } }
 public static class UnsupportedOptionException extends OptionException {
     public UnsupportedOptionException(String o) {
         super("Unsupported option '" + o + "'"); } }
 public static class MissingArgException extends OptionException {
     public MissingArgException(String o) {
         super("Missing argument for option '" + o + "'"); } }
 public static class RequiredOptionException extends OptionException {
     public RequiredOptionException(String o) {
         super("Missing required option '" + o + "'"); } }
 public class Option{
     private String n,d;
     private boolean u,r;
     private Option(String n) { this.n = n; }
     public Option describedAs(String d) { this.d = d; return this; }
     public Option isRequired() { this.r = true; return this; }
     public Option isUnary() { this.u = true; return this; }
 }
 private final java.util.Map<String,Option> opts =
         new java.util.TreeMap<String,Option>();
 private final java.util.Map<String,String> args =
         new java.util.HashMap<String, String>();
 public MicroOptions() { super(); }
 public String usageString() {
     int max = 0;
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
     for (Option o : opts.values())
         if (o.r && !args.containsKey(o.n))
             throw new RequiredOptionException(o.n);
 }
 public Option option(String name) {
     checkName(name);
     Option o = new Option(name); opts.put(name, o); return o; }
 private void checkName(String name) {
     if (name == null || name.length() == 0 || name.charAt(0) == '-')
         throw new UnsupportedOptionException("illegal name: " + name);
 }
 public boolean has(String option) {
     checkName(option);
     if (!opts.containsKey(option))
         throw new UnsupportedOptionException(option);
     return args.containsKey(option);
 }
 public String getArg(String option) { return getArg(option, null); }
 public String getArg(String option, String defaultValue) {
     checkName(option);
     if (!opts.containsKey(option))
         throw new UnsupportedOptionException(option);
     if (opts.get(option).u)
         throw new OptionException("Option takes no arguments: " + option);
     return args.containsKey(option) ? args.get(option) : defaultValue;
 }
}