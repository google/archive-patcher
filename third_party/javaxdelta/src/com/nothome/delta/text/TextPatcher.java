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

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.CharBuffer;

/**
 * Converts a text patch and source file to a resulting target file.
 */
public class TextPatcher {

    private SeekableSource source;
    private CharBuffer buf = CharBuffer.allocate(1024);
    
    /**
     * Constructs a new TextPatcher with a generic source.
     */
    public TextPatcher(SeekableSource source) throws IOException {
        if (source == null)
            throw new NullPointerException("source");
        this.source = source;
    }
    
    /**
     * Constructs a new TextPatcher with a source to patch.
     */
    public TextPatcher(CharSequence source) {
        this.source = new CharBufferSeekableSource(source);
    }
    
    /**
     * Patch from a string, return the result.
     */
    public String patch(CharSequence patch) {
        if (patch == null)
            throw new NullPointerException("patch");
        StringWriter sw = new StringWriter();
        try {
            patch(new StringReader(patch.toString()), sw);
            return sw.toString();
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid patch: " + e, e);
        }
    }
    
    private long l(String s) {
        return Long.parseLong(s, 16);
    }
    
    /**
     * Patches a source to an output file.
     * @param out The output must be closed by the caller
     */
    public void patch(Reader patch, Writer out) throws IOException {
        if (patch == null)
            throw new NullPointerException("patch");
        if (out == null)
            throw new NullPointerException("out");
        BufferedReader br;
        if (patch instanceof BufferedReader)
            br = (BufferedReader) patch;
        else
            br = new BufferedReader(patch);
        String header = br.readLine();
        if (header == null)
            throw new EOFException();
        if (!header.equals(GDiffTextWriter.GDT)) {
            throw new IOException("Unexpected header: " + header);
        }
        String line;
        int lineCount = 0;
        while ((line = br.readLine()) != null) {
            lineCount++;
            if (line.length() == 0)
                throw new IOException("invalid empty line: " + lineCount);
            char c = line.charAt(0);
            if (c == GDiffTextWriter.COPY) {
                int i = line.indexOf(GDiffTextWriter.COMMA);
                if (i == -1)
                    throw new IOException(", not found");
                long offset = l(line.substring(1, i));
                long length = l(line.substring(i + 1));
                source.seek(offset);
                copy(source, out, (int)length);
            } else if (c == GDiffTextWriter.DATA) {
                long dataSize = l(line.substring(1));
                copy(br, out, (int)dataSize);
                br.readLine();
            } else {
                throw new IOException("invalid patch command: " + lineCount);
            }
        }
        out.flush();
    }
    
    private void copy(Readable source, Writer out, int length) throws IOException {
        while (length > 0) {
            if (buf.limit() > length)
                buf.limit(length);
            int count = source.read(buf);
            if (count == -1)
                throw new IOException("EOF in chunk");
            buf.flip();
            out.append(buf);
            length -= count;
        }
    }

}

