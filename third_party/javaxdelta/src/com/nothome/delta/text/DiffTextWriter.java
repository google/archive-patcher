/* 
 *
 * Copyright (c) 2008 Elias Ross
 * Copyright 2014 Google Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 *
 */

package com.nothome.delta.text;

import java.io.Closeable;
import java.io.IOException;

/**
 * Writes diff commands to a stream.
 */
public interface DiffTextWriter extends Closeable {
    
    /**
     * Add a copy command.
     * @param offset start of sequence
     * @param length length of sequence
     */
    void addCopy(int offset, int length) throws IOException;
    
    /**
     * Add a character to output.
     */
	void addData(char seq) throws IOException;
	
	/**
	 * Writes current state to output stream.
	 */
    void flush() throws IOException;
    
    /**
     * Frees internal resources; closes output stream.
     */
    void close() throws IOException;
}

