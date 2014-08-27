 /*
  *
  * Copyright (c) 2001 Torgeir Veimo
  * Copyright (c) 2002 Nicolas PERIDONT
  * Bug Fixes: Daniel Morrione dan@morrione.net
  * Copyright (c) 2006 Heiko Klein
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
  * Change Log:
  * iiimmddyyn  nnnnn  Description
  * ----------  -----  -------------------------------------------------------
  * gls100603a         Fixes from Torgeir Veimo and Dan Morrione
  * gls110603a         Stream not being closed thus preventing a file from
  *                       being subsequently deleted.
  * gls031504a         Error being written to stderr rather than throwing exception
  */

package com.nothome.delta.text;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.CharBuffer;

/**
 * Class for computing deltas against a source.
 * The source file is read by blocks and a hash is computed per block.
 * Then the target is scanned for matching blocks.
 * <p/>
 * Essentially a duplicate of com.nothome.delta.Delta for character streams.
 */
public class Delta {
    
    /**
     * Debug flag.
     */
    final static boolean debug = false;
    
    /**
     * Default size of 16.
     * For "Lorem ipsum" files, the ideal size is about 14. Any smaller and
     * the patch size becomes actually be larger.
     * <p>
     * Use a size like 64 or 128 for large files.
     */
    public static final int DEFAULT_CHUNK_SIZE = 1<<4;
    
    /**
     * Chunk Size.
     */
    private int S;
    
    private SourceState source;
    private TargetState target;
    private DiffTextWriter output;
    
    public Delta() {
        setChunkSize(DEFAULT_CHUNK_SIZE);
    }
    
    /**
     * Sets the chunk size used.
     * Larger chunks are faster and use less memory, but create larger patches
     * as well.
     * 
     * @param size
     */
    public void setChunkSize(int size) {
        if (size <= 0)
            throw new IllegalArgumentException("Invalid size");
        S = size;
    }
    
    /**
     * Compares the source bytes with target bytes, writing to output.
     */
    public void compute(CharSequence source, CharSequence target, Writer output)
    throws IOException {
        compute(new CharBufferSeekableSource(source), 
                new StringReader(target.toString()),
                new GDiffTextWriter(output));
    }
    
    /**
     * Compares the source bytes with target bytes, returning differences.
     */
    public String compute(CharSequence source, CharSequence target)
    throws IOException {
        StringWriter sw = new StringWriter();
        compute(source, target, sw);
        return sw.toString();
    }
    
    /**
     * Compares the source with a target, writing to output.
     * 
     * @param targetIS second file to compare with
     * @param output diff output
     * 
     * @throws IOException if diff generation fails
     */
    public void compute(SeekableSource seekSource, Reader targetIS, DiffTextWriter output)
    throws IOException {
        
        if (debug) {
            debug("using match length S = " + S);
        }
        
        source = new SourceState(seekSource);
        target = new TargetState(targetIS);
        this.output = output;
        if (debug)
            debug("checksums " + source.checksum);
        
        while (!target.eof()) {
            debug("!target.eof()");
            int index = target.find(source);
            if (index != -1) {
                if (debug)
                    debug("found hash " + index);
                int offset = index * S;
                source.seek(offset);
                int match = target.longestMatch(source);
                if (match >= S) {
                    if (debug)
                        debug("output.addCopy("+offset+","+match+")");
                    output.addCopy(offset, match);
                } else {
                    // move the position back according to how much we can't copy
                    target.tbuf.position(target.tbuf.position() - match);
                    addData();
                }
            } else {
                    addData();
            }
        }
        output.close();
    }
    
    private void addData() throws IOException {
        int i = target.read();
        if (debug)
            debug("addData " + (char)i);
        if (i == -1)
            return;
        output.addData((char)i);
    }
    
    class SourceState {

        private Checksum checksum;
        private SeekableSource source;
        
        public SourceState(SeekableSource source) throws IOException {
            checksum = new Checksum(source, S);
            this.source = source;
            source.seek(0);
        }

        public void seek(long index) throws IOException {
            source.seek(index);
        }

        /**
         * Returns a debug <code>String</code>.
         */
        @Override
        public String toString()
        {
            return "Source"+
                " checksum=" + this.checksum +
                " source=" + this.source +
                "";
        }
        
    }
        
    class TargetState {
        
        private Readable c;
        private CharBuffer tbuf = CharBuffer.allocate(blocksize());
        private CharBuffer sbuf = CharBuffer.allocate(blocksize());
        private long hash;
        private boolean hashReset = true;
        private boolean eof;
        
        TargetState(Reader targetIS) throws IOException {
            c = targetIS;
            tbuf.limit(0);
        }
        
        private int blocksize() {
            return Math.max(1024 * 8, S * 4);
        }

        /**
         * Returns the index of the next N bytes of the stream.
         */
        public int find(SourceState source) throws IOException {
            if (eof)
                return -1;
            sbuf.clear();
            sbuf.limit(0);
            if (hashReset) {
                debug("hashReset");
                while (tbuf.remaining() < S) {
                    tbuf.compact();
                    int read = c.read(tbuf);
                    tbuf.flip();
                    if (read == -1) {
                        debug("target ending");
                        return -1;
                    }
                }
                hash = Checksum.queryChecksum(tbuf, S);
                hashReset = false;
            }
            if (debug)
                debug("hash " + hash + " " + dump());
            return source.checksum.findChecksumIndex(hash);
        }

        public boolean eof() {
            return eof;
        }

        /**
         * Reads a char.
         * @throws IOException
         */
        public int read() throws IOException {
            if (tbuf.remaining() <= S) {
                readMore();
            }
            if (!tbuf.hasRemaining()) {
                eof = true;
                return -1;
            }
            char b = tbuf.get();
            if (tbuf.remaining() >= S) {
                char nchar = tbuf.get( tbuf.position() + S -1 );
                hash = Checksum.incrementChecksum(hash, b, nchar, S);
            } else {
                debug("out of char");
            }
            return b;
        }

        /**
         * Returns the longest match length at the source location.
         */
        public int longestMatch(SourceState source) throws IOException {
            debug("longestMatch");
            int match = 0;
            hashReset = true;
            while (true) {
                if (!sbuf.hasRemaining()) {
                    sbuf.clear();
                    int read = source.source.read(sbuf);
                    sbuf.flip();
                    if (read == -1)
                        return match;
                }
                if (!tbuf.hasRemaining()) {
                    readMore();
                    if (!tbuf.hasRemaining()) {
                        debug("target ending");
                        eof = true;
                        return match;
                    }
                }
                if (sbuf.get() != tbuf.get()) {
                    tbuf.position(tbuf.position() - 1);
                    return match;
                }
                match++;
            }
        }

        private void readMore() throws IOException {
            if (debug)
                debug("readMore " + tbuf);
            tbuf.compact();
            c.read(tbuf);
            tbuf.flip();
        }

        void hash() {
            hash = Checksum.queryChecksum(tbuf, S);
        }

        /**
         * Returns a debug <code>String</code>.
         */
        @Override
        public String toString()
        {
            return "Target[" +
                " targetBuff=" + dump() + // this.tbuf +
                " sourceBuff=" + this.sbuf +
                " hashf=" + this.hash +
                " eof=" + this.eof +
                "]";
        }
        
        private String dump() { return dump(tbuf); }
        
        private String dump(CharBuffer bb) {
            bb.mark();
            StringBuilder sb = new StringBuilder();
            while (bb.hasRemaining())
                sb.append((char)bb.get());
            bb.reset();
            return sb.toString();
        }
        
    }
    
    private void debug(String s) {
        if (debug)
            System.err.println(s);
    }

    static Reader forFile(File name) throws FileNotFoundException {
        FileInputStream f1 = new FileInputStream(name);
        InputStreamReader isr = new InputStreamReader(f1);
        return new BufferedReader(isr);
    }
    
    static CharSequence toString(Reader r) throws IOException {
        StringBuilder sb = new StringBuilder();
        while (true) {
            int read = r.read();
            if (read == -1)
                break;
            sb.append((char)read);
        }
        return sb;
    }
    
    /**
     * Creates a patch with file names.
     */
    public static void main(String s[]) throws IOException {
        if (s.length != 2) {
            System.err.println("Usage: java ...Delta file1 file2 [> somefile]");
            return;
        }
        Reader r1 = forFile(new File(s[0]));
        File f2 = new File(s[1]);
        Reader r2 = forFile(f2);
        CharSequence sb = toString(r1);
        Delta d = new Delta();
        OutputStreamWriter osw = new OutputStreamWriter(System.out);
        d.compute(new CharBufferSeekableSource(sb), r2, new GDiffTextWriter(osw));
        osw.close();
    }
    
}
