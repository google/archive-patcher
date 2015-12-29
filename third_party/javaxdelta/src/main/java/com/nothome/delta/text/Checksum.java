/*
 *
 * Copyright (c) 2001 Torgeir Veimo
 * Copyright (c) 2002 Nicolas PERIDONT
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
 */

package com.nothome.delta.text;

import java.io.IOException;
import java.nio.CharBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Checksum that uses character streams.
 */
public class Checksum {
    
    public static boolean debug = false;
    
    protected Map<Long, Integer> checksums = new HashMap<Long, Integer>();
    
    private static final char[] single_hash = com.nothome.delta.Checksum.getSingleHash();
    
    /**
     * Initialize checksums for source. The checksum for the <code>chunkSize</code> bytes at offset
     * <code>chunkSize</code> * i is inserted into an array at index i.
     */
    public Checksum(Readable source, int chunkSize) throws IOException {
        CharBuffer bb = CharBuffer.allocate(chunkSize * 2);
        int count = 0;
        while (true) {
            source.read(bb);
            bb.flip();
            if (bb.remaining() < chunkSize)
                break;
            while (bb.remaining() >= chunkSize) {
                long queryChecksum = queryChecksum0(bb, chunkSize);
                checksums.put(queryChecksum, count++);
            }
            bb.compact();
        }
    }
    
    /**
     * Marks, gets, then resets the checksum computed from the buffer.
     */
    public static long queryChecksum(CharBuffer bb, int len) {
        bb.mark();
        long sum = queryChecksum0(bb, len);
        bb.reset();
        return sum;
    }
    
    private static byte b(char c) {
        return (byte)c;
    }
    
    private static long queryChecksum0(CharBuffer bb, int len) {
        int high = 0; int low = 0;
        for (int i = 0; i < len; i++) {
            low += single_hash[b(bb.get())+128];
            high += low;
        }
        return ((high & 0xffff) << 16) | (low & 0xffff);
    }
    
    public static long incrementChecksum(long checksum, char out, char in, int chunkSize) {
        char old_c = single_hash[b(out)+128];
        char new_c = single_hash[b(in)+128];
        int low   = ((int)((checksum) & 0xffff) - old_c + new_c) & 0xffff;
        int high  = ((int)((checksum) >> 16) - (old_c * chunkSize) + low) & 0xffff;
        return (high << 16) | (low & 0xffff);
    }
    
    public int findChecksumIndex(long hashf) {
        if (!checksums.containsKey(hashf))
            return -1;
        return checksums.get(hashf);
    }

    /**
     * Returns a debug <code>String</code>.
     */
    @Override
    public String toString()
    {
        return super.toString() +
            " checksums=" + new HashMap<Long, Integer>(this.checksums) +
            "";
    }
    
    
}
