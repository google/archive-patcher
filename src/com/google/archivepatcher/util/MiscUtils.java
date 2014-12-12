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

package com.google.archivepatcher.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.zip.DeflaterOutputStream;

/**
 * Miscellaneous utilities.
 */
public class MiscUtils {

    /**
     * Load a named class and return an instance of it using the class'
     * default no-arg public constructor.
     * @param className the name of the class to load and instantiate
     * @param requiredInterface optionally, an interface that must be
     * implemented by the class in order for loading to succeed
     * @return an instance of the specified class, that implements the
     * specified interface
     * @throws RuntimeException if class loading fails or the required
     * interface is not implemented
     */
    @SuppressWarnings("unchecked") // enforced by loadrequiredClass.
    public static final <T> T createRequiredInstance(final String className,
            final Class<T> requiredInterface) {
        final Class<?> clazz = MiscUtils.loadRequiredClass(
            className, requiredInterface);
        try {
            return (T) clazz.newInstance();
        } catch (Exception e) {
            throw new RuntimeException(
                "failed to initialize class: " + className, e);
        }
    }

    /**
     * Load a named class that is required, throwing an exception upon failure.
     * 
     * @param className the name of the class to load
     * @param requiredInterface optionally, an interface that must be
     * implemented by the class in order for loading to succeed
     * @return the class object representing the named class
     * @throws RuntimeException if class loading fails or the required interface
     * is not implemented
     */
    public static final Class<?> loadRequiredClass(final String className,
            final Class<?> requiredInterface) {
        try {
            Class<?> result = Class.forName(className);
            if (requiredInterface == null) return result;
            if (requiredInterface.isAssignableFrom(result)) return result;
            throw new RuntimeException("class doesn't implement " +
                requiredInterface.getName());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("failed to load class: " + className, e);
        }
    }

    /**
     * Obtains a {@link File} and guarantees that it exists, is a file, and can
     * be read.
     * @param path the path
     * @return the file object
     * @throws IOException if the path doesn't denote a readable file.
     */
    public final static File getReadableFile(String path) throws IOException {
        final File result = new File(path);
        if (!result.exists()) {
            throw new IOException("No such file: " + path);
        }
        if (!result.isFile()) {
            throw new IOException("Not a file: " + path);
        }
        if (!result.canRead()) {
            throw new IOException("Access denied: " + path);
        }
        return result;
    }

    /**
     * Read all the lines out of the specified file and return them as a list
     * of strings. Empty lines are skipped.
     * @param file the file to read from
     * @param commentChar optionally, a character that, when present as the
     * first non-whitespace character in a line, indicates that the line is a
     * comment and should not be included in the returned list
     * @return the list of lines within the file; possibly empty but never null.
     * @throws IOException if unable to complete the read operation
     */
    public final static List<String> readLines(final File file,
        final Character commentChar) throws IOException {
        FileReader fileReader = null;
        BufferedReader bufferedReader = null;
        try {
            fileReader = new FileReader(file);
            bufferedReader = new BufferedReader(fileReader);
            final List<String> result = new LinkedList<String>();
            String currentLine = null;
            while ((currentLine = bufferedReader.readLine()) != null) {
                String trimmed = currentLine.trim();
                if (!trimmed.isEmpty()) {
                    if (commentChar == null ||
                        trimmed.charAt(0) != commentChar) {
                        result.add(currentLine);
                    }
                }
            }
            return result;
        } finally {
            try {
                bufferedReader.close();
            } catch (Exception ignored) {
                // Ignored
            }
            try {
                fileReader.close();
            } catch (Exception ignored) {
                // Ignored
            }
        }
    }

    /**
     * Convenience method to either create a new instance of the specified
     * class name, or to return null. In either case the return type is as
     * specified.
     * 
     * @param className the name of a class to load, or null if no class should
     * be loaded
     * @param requiredInterface the interface required to be implemented by the
     * the class, and the return type for this method
     * @return either an instance of the class or null if no class name was
     * specified.
     */
    public static final <T> T maybeCreateInstance(final String className,
            final Class<T> requiredInterface) {
        if (className == null) return null;
        return createRequiredInstance(className, requiredInterface);
    }

    /**
     * Deflate the specified input file and write to the specified output file.
     * @param inputFile the input file
     * @param outputFile the output file
     * @throws IOException if there is an error while reading or writing
     */
    public final static void deflate(final File inputFile,
        final File outputFile) throws IOException {
        FileInputStream fileIn = null;
        FileOutputStream fileOut = null;
        DeflaterOutputStream deflateOut = null;
        try {
            fileIn = new FileInputStream(inputFile);
            fileOut = new FileOutputStream(outputFile);
            deflateOut = new DeflaterOutputStream(fileOut);
            final byte[] buffer = new byte[65535];
            int numRead = 0;
            while ((numRead = fileIn.read(buffer)) >= 0) {
                if (numRead > 0) {
                    deflateOut.write(buffer, 0, numRead);
                }
            }
        } finally {
            try {
                fileIn.close();
            } catch (Exception ignored) {
                // ignore
            }
            try {
                deflateOut.close();
            } catch (Exception ignored) {
                // ignore
            }
            try {
                fileOut.close();
            } catch (Exception ignored) {
                // ignore
            }
        }
    }
}
