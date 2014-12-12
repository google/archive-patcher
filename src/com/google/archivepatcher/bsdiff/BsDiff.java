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

package com.google.archivepatcher.bsdiff;

import java.io.IOException;
import java.io.OutputStream;

/**
 * A Java implementation of the "bsdiff" algorithm based on the BSD-2 licensed
 * source code available here: https://github.com/mendsley/bsdiff.
 * <p>
 * A pristine copy of the code is checked into this project under
 * third_party/bsdiff.
 * <p>
 * Since Java only supports "int" for array indexing, the maximum size of files
 * that this implementation can handle is 2^31, or 2 gibibytes.
 * <p>
 * This class is completely standalone and depends only upon JRE functionality
 * that has been available since Java 1.0.
 */
public class BsDiff {

    /**
     * Simple helper class to hold an integer value.
     */
    private static class IntHolder {
        /**
         * The value held in this object.
         */
        public int value;
    }

    /**
     * Provides functional equivalent to C/C++ memcmp.
     * @param data1 first byte array
     * @param start1 index in the first array at which to start comparing
     * @param data2 second byte array
     * @param start2 index in the second array at which to start comparing
     * @param length number of bytes to compare
     * @return as memcmp would: zero if the range is equal, negative if the
     * first difference has a lower value in the first array, greater if the
     * first difference has a lower value in the second array.
     */
    private static int memcmp(final byte[] data1, final int start1,
        final byte[] data2, final int start2, final int length) {
        int bytesLeft = length;
        int indexOld = start1;
        int indexNew = start2;
        while (bytesLeft-- > 0) {
            if (data1[indexOld] != data2[indexNew]) {
                return BsDiff.u(data1[indexOld]) - BsDiff.u(data2[indexNew]);
            }
            indexOld++;
            indexNew++;
        }
        return 0;
    }

    /**
     * Convenience shortcut to get an unsigned integer from a byte.
     * @param b the byte
     * @return a value in the range [0,255]
     */
    private final static int u(byte b) {
        return b & 0x000000ff;
    }

    // Source: https://github.com/mendsley/bsdiff/blob/master/bsdiff.c
    private static void split(final int[] groupArray, final int[] inverseArray,
        final int start, final int length, final int inverseOffset) {
        if(length<16) {
            // Length is too short to bother recursing.
            int step = 0;
            for(int outer=start; outer < start+length; outer+=step) {
                step = 1;
                int x=inverseArray[groupArray[outer]+inverseOffset];
                for(int inner=1;outer+inner<start+length;inner++) {
                    if(inverseArray[groupArray[outer+inner]+inverseOffset]<x) {
                        x=inverseArray[groupArray[outer+inner]+inverseOffset];
                        step = 0;
                    }
                    if(inverseArray[groupArray[outer+inner]+inverseOffset]==x) {
                        final int temp=groupArray[outer+step];
                        groupArray[outer+step]=groupArray[outer+inner];
                        groupArray[outer+inner]=temp;
                        step++;
                    }
                }
                for(int innerIndex=0;innerIndex<step;innerIndex++) {
                    inverseArray[groupArray[outer+innerIndex]]=outer+step-1;
                }
                if(step==1) {
                    groupArray[outer]=-1;
                }
            }
            return;
        }

        // Else, length >= 16
        int x = inverseArray[groupArray[start+length/2]+inverseOffset];
        int jj = 0;
        int kk = 0;
        for(int index=start; index < start+length; index++) {
            if(inverseArray[groupArray[index]+inverseOffset]<x) {
                jj++;
            }
            if(inverseArray[groupArray[index]+inverseOffset]==x) {
                kk++;
            }
        }

        jj+=start;
        kk+=jj;

        { // scoping block
            int j = 0;
            int k = 0;
            {
                int i=start;
                while(i<jj) {
                    if(inverseArray[groupArray[i]+inverseOffset]<x) {
                        i++;
                    } else if(inverseArray[groupArray[i]+inverseOffset]==x) {
                        final int temp=groupArray[i];
                        groupArray[i]=groupArray[jj+j];
                        groupArray[jj+j]=temp;
                        j++;
                    } else { // >x
                        final int temp=groupArray[i];
                        groupArray[i]=groupArray[kk+k];
                        groupArray[kk+k]=temp;
                        k++;
                    };
                };
            }
    
            while(jj+j<kk) {
                if(inverseArray[groupArray[jj+j]+inverseOffset]==x) {
                    j++;
                } else { // != x
                    final int temp=groupArray[jj+j];
                    groupArray[jj+j]=groupArray[kk+k];
                    groupArray[kk+k]=temp;
                    k++;
                };
            };
        }

        if(jj>start) {
            split(groupArray,inverseArray,start,jj-start,inverseOffset);
        }

        for(int i=0; i < kk-jj; i++) {
            inverseArray[groupArray[jj+i]] = kk-1;
        }

        if(jj == kk - 1) {
            groupArray[jj] = -1;
        }

        if(start+length > kk) {
            split(groupArray,inverseArray,kk,start+length-kk,inverseOffset);
        }
    }

    /**
     * Perform a "quick suffix sort".
     * @param groupArray the suffix array to fill with data
     * @param data the data to sort
     */
    // Original name: qsufsort
    private static void quickSuffixSort(
        int[] groupArray, final byte[] data) {
        // Generate a histogram of the counts of each byte in the old data:
        // 1. Initialize buckets 0-255 to zero
        // 2. Read each byte and count the number of occurrences of each byte
        // 3. For each bucket, add the previous bucket's value.
        // 4. For each bucket past the first, set the value to the previous
        //    bucket's value
        final int[] buckets = new int[256];
        for(int i=0; i<256; i++) buckets[i] = 0;
        for(int i=0; i<data.length; i++) buckets[u(data[i])]++;
        for(int i=1; i<256; i++) buckets[i] += buckets[i-1];
        for(int i=255; i>0; i--) buckets[i] = buckets[i-1];
        buckets[0]=0;

        for(int i=0; i<data.length; i++) {
            groupArray[++buckets[u(data[i])]] = i;
        }

        groupArray[0] = data.length;
        final int[] inverseArray = new int[data.length+1];
        for(int i=0; i<data.length; i++) {
            inverseArray[i] = buckets[u(data[i])];
        }

        inverseArray[data.length] = 0;
        for(int i=1; i<256; i++) {
            if(buckets[i]==buckets[i-1]+1) {
                groupArray[buckets[i]] = -1;
            }
        }
        groupArray[0] = -1;

        for(int h=1; groupArray[0] != -(data.length+1); h+=h) {
            int length=0;
            int i = 0;
            for(; i<data.length+1 ;) {
                if(groupArray[i]<0) {
                    length -= groupArray[i];
                    i -= groupArray[i];
                } else {
                    if(length > 0) groupArray[i-length] = -length;
                    length = inverseArray[groupArray[i]] + 1 - i;
                    split(groupArray, inverseArray, i, length, h);
                    i += length;
                    length = 0;
                }
            }
            if(length > 0) {
                groupArray[i-length] = -length;
            }
        }

        for(int i=0; i<data.length+1; i++) {
            groupArray[inverseArray[i]]=i;
        }
    }

    /**
     * Search the specified arrays for a contiguous sequence of identical bytes,
     * starting at the specified "start" offsets and scanning as far ahead as
     * possible till one or the other of the arrays ends or a non-matching byte
     * is found. Returns the length of the matching sequence of bytes, which
     * may be zero.
     * @param oldData the old data to scan
     * @param oldStart the position in the old data at which to start the scan
     * @param newData the new data to scan
     * @param newStart the position in the new data at which to start the scan
     * @return the number of matching bytes in the two arrays starting at the
     * specified indices; zero if the first byte fails to match
     */
    // Original name: matchlen
    private static int lengthOfMatch(final byte[] oldData, final int oldStart,
        final byte[] newData, final int newStart) {
        final int max = Math.min(oldData.length - oldStart,
            newData.length - newStart);
        for(int offset=0; offset<max; offset++) {
            if(oldData[oldStart+offset] != newData[newStart+offset]) {
                return offset;
            }
        }
        return max;
    }

    /**
     * Perform a binary search in two ranges of data, attempting to locate a
     * run of bytes that match in both old and new.
     * @param groupArray
     * @param oldData the old data to scan
     * @param oldStart the position in the old data at which to start scanning
     * @param newData the new data to scan
     * @param newStart the position in the new data at which to start scanning
     * @param oldDataRangeStartA
     * @param oldDataRangeStartB
     * @param positionHolder a holder for a position, which will be set to the
     * position at which a matching range begins
     * @return the length of the matching range that starts at the value set
     * in positionHolder
     */
    // Original name: search
    private static int searchForMatch(final int[] groupArray,
        final byte[] oldData, final int oldStart,
        final byte[] newData, final int newStart,
        final int oldDataRangeStartA, final int oldDataRangeStartB,
        final IntHolder positionHolder)  {
        if(oldDataRangeStartB - oldDataRangeStartA < 2) {
            // We have located the start of a matching range (no further search
            // required) or the size of the range has shrunk to one byte (no
            // further search possible).
            final int lengthOfMatchA=lengthOfMatch(oldData,
                groupArray[oldDataRangeStartA], newData, newStart);
            final int lengthOfMatchB=lengthOfMatch(oldData,
                groupArray[oldDataRangeStartB], newData, newStart);
    
            if(lengthOfMatchA > lengthOfMatchB) {
                positionHolder.value=groupArray[oldDataRangeStartA];
                return lengthOfMatchA;
            }
            positionHolder.value=groupArray[oldDataRangeStartB];
            return lengthOfMatchB;
        }

        // Cut range in half and search again
        final int rangeLength = oldDataRangeStartB - oldDataRangeStartA;
        final int pivot = oldDataRangeStartA + (rangeLength / 2);
        final int compareLength = Math.min(
            oldData.length - groupArray[pivot], newData.length);
        if(memcmp(oldData, groupArray[pivot], newData, 0, compareLength) < 0) {
            return searchForMatch(groupArray,oldData,oldStart,
                newData, newStart, pivot, oldDataRangeStartB, positionHolder);
        }
        return searchForMatch(groupArray,oldData,oldStart,
            newData, newStart, oldDataRangeStartA, pivot, positionHolder);
    }

    /**
     * Write an offset (64-bit signed integer).
     * The least significant bit is written to index [start], the most
     * significant bit is written to index [start+7].
     * @param value the value to write
     * @param destination the buffer to write to
     * @param start the index at which to start writing
     */
    // Original name: offtout (derived presumably from the data type "off_t")
    private static void writeOffset(final long value,
        final byte[] destination, final int start) {
        long y = value < 0 ? -value : value;

        destination[0+start] = (byte) (y & 0xff); y>>>=8;
        destination[1+start] = (byte) (y & 0xff); y>>>=8;
        destination[2+start] = (byte) (y & 0xff); y>>>=8;
        destination[3+start] = (byte) (y & 0xff); y>>>=8;
        destination[4+start] = (byte) (y & 0xff); y>>>=8;
        destination[5+start] = (byte) (y & 0xff); y>>>=8;
        destination[6+start] = (byte) (y & 0xff); y>>>=8;
        destination[7+start] = (byte) (y & 0xff);

        if(value<0) destination[7+start] |= 0x80;
    }

    /**
     * Write data to the configured output stream.
     * @param stream the stream to write to
     * @param buffer the buffer that contains the bytes to be written
     * @param start the index in the buffer from which to start reading
     * @param length the number of bytes to write
     * @return the number of bytes written, always equal to length
     * @throws IOException if unable to complete the write operation
     */
    // Original name: writedata
    // This method is not useful in the Java implementation but is kept for
    // similarity to the C++ implementation.
    private static int write(final OutputStream stream,
        final byte[] buffer, final int start, final int length)
            throws IOException {
        stream.write(buffer, start, length);
        return length;
    }

    /**
     * Helper class that holds the arguments/configuration for generating a
     * patch.
     */
    // Original name: bsdiff_request
    private static class BsDiffRequest {
        private byte[] oldData;
        private byte[] newData;
        private OutputStream outputStream;
        private int[] groupArray;
        private byte[] buffer;
    }

    // Original name: bsdiff_internal
    private static void generatePatch(final BsDiffRequest config)
        throws IOException {
        int overlap,Ss,lens;
        byte[] buffer;
        final int[] groupArray = config.groupArray;

        // Do the suffix search.
        {
            quickSuffixSort(groupArray,config.oldData);
        }

        buffer = config.buffer;

        /* Compute the differences, writing ctrl as we go */
        int newPosition=0;
        int len=0;
        IntHolder positionHolder = new IntHolder();
        int lastNewPosition=0;
        int lastOldPosition=0;
        int lastOffset=0;
        while(newPosition<config.newData.length) {
            int oldScore=0;

            for(int scsc=newPosition+=len;newPosition<config.newData.length;newPosition++) {
                len=searchForMatch(groupArray,
                    config.oldData, 0,
                    config.newData, newPosition,
                    0, config.oldData.length, positionHolder);

                for(;scsc<newPosition+len;scsc++)
                if((scsc+lastOffset<config.oldData.length) &&
                    (config.oldData[scsc+lastOffset] == config.newData[scsc]))
                    oldScore++;

                if(((len==oldScore) && (len!=0)) || 
                    (len>oldScore+8)) break;

                if((newPosition+lastOffset<config.oldData.length) &&
                    (config.oldData[newPosition+lastOffset] == config.newData[newPosition]))
                    oldScore--;
            };

            if((len!=oldScore) || (newPosition==config.newData.length)) {
                int s=0;
                int Sf=0;
                int lenf=0;
                for(int i=0;(lastNewPosition+i<newPosition)&&(lastOldPosition+i<config.oldData.length);) {
                    if(config.oldData[lastOldPosition+i]==config.newData[lastNewPosition+i]) {
                        s++;
                    }
                    i++;
                    if(s*2-i>Sf*2-lenf) {
                        Sf=s; lenf=i;
                    }
                };

                int lenb=0;
                if(newPosition<config.newData.length) {
                    s=0;
                    int Sb = 0;
                    for(int i=1;(newPosition>=lastNewPosition+i)&&(positionHolder.value>=i);i++) {
                        if(config.oldData[positionHolder.value-i]==config.newData[newPosition-i]) {
                            s++;
                        }
                        if(s*2-i>Sb*2-lenb) {
                            Sb=s; lenb=i;
                        }
                    };
                };

                if(lastNewPosition+lenf>newPosition-lenb) {
                    overlap=(lastNewPosition+lenf)-(newPosition-lenb);
                    s=0;Ss=0;lens=0;
                    for(int i=0;i<overlap;i++) {
                        if(config.newData[lastNewPosition+lenf-overlap+i]==
                           config.oldData[lastOldPosition+lenf-overlap+i]) {
                            s++;
                        }
                        if(config.newData[newPosition-lenb+i]==
                           config.oldData[positionHolder.value-lenb+i]) {
                            s--;
                        }
                        if(s>Ss) {
                            Ss=s; lens=i+1;
                        };
                    };

                    lenf+=lens-overlap;
                    lenb-=lens;
                };

                byte[] control_buffer = new byte[8 * 3];
                writeOffset(lenf,control_buffer,0);
                writeOffset((newPosition-lenb)-(lastNewPosition+lenf),control_buffer,8);
                writeOffset((positionHolder.value-lenb)-(lastOldPosition+lenf),control_buffer,16);

                /* Write control data */
                write(config.outputStream, control_buffer, 0, control_buffer.length);

                /* Write diff data */
                for(int i=0;i<lenf;i++) {
                    buffer[i]=(byte) (u(config.newData[lastNewPosition+i])-u(config.oldData[lastOldPosition+i]));
                }
                write(config.outputStream, buffer, 0, lenf);

                /* Write extra data */
                for(int i=0;i<(newPosition-lenb)-(lastNewPosition+lenf);i++) {
                    buffer[i]=config.newData[lastNewPosition+lenf+i];
                }
                write(config.outputStream, buffer, 0, (newPosition-lenb)-(lastNewPosition+lenf));

                lastNewPosition=newPosition-lenb;
                lastOldPosition=positionHolder.value-lenb;
                lastOffset=positionHolder.value-newPosition;
            }
        }
    }

    /**
     * Generate a diff between the old data and the new, writing to the
     * specified stream.
     * @param oldData the old data
     * @param newData the new data
     * @param outputStream where output should be written
     * @throws IOException
     */
    // Original name: bsdiff
    public static void generatePatch(final byte[] oldData,
        final byte[] newData, final OutputStream outputStream)
            throws IOException {
        final BsDiffRequest req = new BsDiffRequest();
        req.groupArray = new int[oldData.length+1];
        req.buffer = new byte[newData.length+1];
        req.oldData = oldData;
        req.newData = newData;
        req.outputStream = outputStream;
        generatePatch(req);
    }
}