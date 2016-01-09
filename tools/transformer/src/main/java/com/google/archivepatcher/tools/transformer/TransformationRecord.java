// Copyright 2016 Google Inc. All rights reserved.
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

package com.google.archivepatcher.tools.transformer;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import com.google.archivepatcher.compat.Implementation;
import com.google.archivepatcher.compression.JreDeflateParameters;
import com.google.archivepatcher.meta.CompressionMethod;
import com.google.archivepatcher.util.MiscUtils;

// TODO: The file paths are unnecessary because there is a 1:1 correspondence
// between entries in the record and entries in the archive, and they are all
// in the same order. It's great information to have for verification, but it
// is ultimately unnecessary.

// TODO: In most files there are only one or two deflate configurations used.
// Some space could be saved by defining all the used deflate configurations at
// the start and then just referencing them via a single byte ID. This is a
// small savings as the current code uses only 3 bytes in each entry to record
// the data anyways.

// TODO: The original compressed size is, in principle, unnecessary since it
// can be empirically derived by running the compression engine with the
// specified config. However, it is convenient for sanity checking and for
// processing the archive in a streaming fashion, since the size must be known
// before the local entry can be written unless a data descriptor has been used.

// TODO: The end of central directory relocation is also unnecessary because its
// offset is implied by the final byte of the last entry in the local section.
// However, it is again nice to keep for sanity and supports archives that are
// non-contiguous.

/**
 * Metadata describing a transform performed by {@link UncompressTransformer}.
 */
public class TransformationRecord {

    /**
     * Header for transformation records.
     */
    public static String MAGIC = "ARCTRX01";

    /**
     * Integer ID for the copy operation.
     */
    public static final int COPY = 0;

    /**
     * Integer ID for the uncompress operation.
     */
    public static final int UNCOMPRESS = 1;

    /**
     * Integer ID for the relocate-central-directory operation.
     */
    public static final int RELOCATE_CENTRAL_DIRECTORY = 2;

    /**
     * All records within this object implement this interface.
     */
    public static interface Operation {
        /**
         * Returns the ID of this operation type, used for reading and writing
         * the operations from/to a file.
         * @return the ID
         */
        public byte getId();

        /**
         * Write the operation to the specified output.
         * @param out output to write the operation to
         * @throws IOException if anything goes wrong
         */
        public void write(DataOutput out) throws IOException;

        /**
         * Read the operation from the specified input.
         * @param in the input to read from
         * @throws IOException if anything goes wrong
         */
        public void read(DataInput in) throws IOException;
    }

    /**
     * A record of a copy operation that left the original data intact, but may
     * have moved it.
     */
    public static final class Copy implements Operation {
        /**
         * The path of the resource that was copied.
         */
        private String path;

        /**
         * The original location of the start of the resource's local section
         * entry.
         */
        private long originalOffset;

        /**
         * The updated location of the start of the resource's local section
         * entry.
         */
        private long newOffset;

        /**
         * Default constructor.
         */
        private Copy() {
            this(null, -1, -1);
        }

        /**
         * Create a new record of this type.
         * @param path the path of the resource that was copied.
         * @param originalOffset the original location of the start of the
         * resource's local section entry
         * @param newOffset the updated location of the start of the resource's
         * local section entry
         */
        public Copy(String path, long originalOffset, long newOffset) {
            this.path = path;
            this.originalOffset = originalOffset;
            this.newOffset = newOffset;
        }

        /**
         * Returns the original location of the start of the resource's local
         * section entry. 
         * @return as described
         */
        public long getOriginalOffset() {
            return originalOffset;
        }

        /**
         * Returns the updated location of the start of the resource's local
         * section entry.
         * @return as described
         */
        public long getNewOffset() {
            return newOffset;
        }

        /**
         * Returns the path of the resource that was copied.
         * @return as described
         */
        public String getPath() {
            return path;
        }

        @Implementation
        public byte getId() {
            return COPY;
        }

        @Implementation
        public void write(DataOutput out) throws IOException {
            out.writeUTF(path);
            out.writeLong(originalOffset);
            out.writeLong(newOffset);
        }

        @Implementation
        public void read(DataInput in) throws IOException {
            path = in.readUTF();
            originalOffset = in.readLong();
            newOffset = in.readLong();
        }

        @Override
        public String toString() {
            return "Copy [path=" + path +
                ", originalOffset=" + originalOffset +
                ", newOffset=" + newOffset + "]";
        }
    }

    /**
     * A record of an uncompress operation that uncompressed the original data
     * and may have moved it as well. The original compression method is
     * always assumed to have been {@link CompressionMethod#DEFLATED}.
     */
    public static final class Uncompress implements Operation {
        /**
         * The path of the resource that was uncompressed.
         */
        private String path;

        /**
         * The original location of the start of the resource's local section
         * entry.
         */
        private long originalOffset;

        /**
         * The updated location of the start of the resource's local section
         * entry.
         */
        private long newOffset;

        /**
         * The original compressed size of the resource.
         */
        private long originalCompressedSize;

        /**
         * The deflate parameters that were used for the resource.
         */
        private JreDeflateParameters deflateParameters;

        /**
         * Default constructor.
         */
        private Uncompress() {
            this(null, -1, -1, -1, null);
        }

        /**
         * Create a new record of this type.
         * @param path the path of the resource that was copied.
         * @param originalOffset the original location of the start of the
         * resource's local section entry
         * @param newOffset the updated location of the start of the resource's
         * local section entry
         * @param originalCompressedSize the original compressed size of the
         * resource
         * @param deflateParameters the deflate parameters that were used for
         * the resource
         */
        public Uncompress(String path, long originalOffset, long newOffset,
            long originalCompressedSize,
            JreDeflateParameters deflateParameters) {
            this.path = path;
            this.originalOffset = originalOffset;
            this.newOffset = newOffset;
            this.originalCompressedSize = originalCompressedSize;
            this.deflateParameters = deflateParameters;
        }

        /**
         * Returns the original location of the start of the resource's local
         * section entry. 
         * @return as described
         */
        public long getOriginalOffset() {
            return originalOffset;
        }

        /**
         * Returns the updated location of the start of the resource's local
         * section entry.
         * @return as described
         */
        public long getNewOffset() {
            return newOffset;
        }

        /**
         * Returns the path of the resource that was uncompressed.
         * @return as described
         */
        public String getPath() {
            return path;
        }

        /**
         * Returns the original compressed size of the resource.
         * @return as described
         */
        public long getOriginalCompressedSize() {
            return originalCompressedSize;
        }

        /**
         * Returns the deflate parameters that were used for the resource.
         * @return as described
         */
        public JreDeflateParameters getDeflateParameters() {
            return deflateParameters;
        }

        @Implementation
        public byte getId() {
            return UNCOMPRESS;
        }

        @Implementation
        public void write(DataOutput out) throws IOException {
            out.writeUTF(path);
            out.writeLong(originalOffset);
            out.writeLong(newOffset);
            out.writeLong(originalCompressedSize);
            out.writeByte(deflateParameters.level);
            out.writeByte(deflateParameters.strategy);
            out.writeBoolean(deflateParameters.nowrap);
        }

        @Implementation
        public void read(DataInput in) throws IOException {
            path = in.readUTF();
            originalOffset = in.readLong();
            newOffset = in.readLong();
            originalCompressedSize = in.readLong();
            final int level = in.readByte();
            final int strategy = in.readByte();
            final boolean nowrap = in.readBoolean();
            deflateParameters = new JreDeflateParameters(
                level, strategy, nowrap);
        }

        @Override
        public String toString() {
            return "Uncompress [path=" + path +
                ", originalOffset=" + originalOffset +
                ", newOffset=" + newOffset
                + ", originalCompressedSize=" + originalCompressedSize +
                ", deflateParameters=" + deflateParameters + "]";
        }
    }

    /**
     * A record of the relocation of the central directory.
     */
    public static final class RelocateCentralDirectory implements Operation {
        /**
         * The original offset of the start of the central directory.
         */
        private long originalOffset;

        /**
         * The updated offset of the start of the central directory.
         */
        private long newOffset;

        /**
         * Default constructor.
         */
        private RelocateCentralDirectory() {
            this(-1, -1);
        }

        /**
         * Create a new record of this type.
         * @param originalOffset the original offset of the start of the central
         * directory
         * @param newOffset the updated offset of the start of the central
         * directory
         */
        public RelocateCentralDirectory(long originalOffset, long newOffset) {
            this.originalOffset = originalOffset;
            this.newOffset = newOffset;
        }

        /**
         * Returns the original offset of the start of the central directory.
         * @return as described
         */
        public long getOriginalOffset() {
            return originalOffset;
        }

        /**
         * Returns the updated offset of the start of the central directory.
         * @return as described
         */
        public long getNewOffset() {
            return newOffset;
        }

        @Implementation
        public byte getId() {
            return RELOCATE_CENTRAL_DIRECTORY;
        }

        @Implementation
        public void write(DataOutput out) throws IOException {
            out.writeLong(originalOffset);
            out.writeLong(newOffset);
        }

        @Implementation
        public void read(DataInput in) throws IOException {
            originalOffset = in.readLong();
            newOffset = in.readLong();
        }

        @Override
        public String toString() {
            return "RelocateCentralDirectory " +
                "[originalOffset=" + originalOffset +
                ", newOffset=" + newOffset + "]";
        }
    }

    /**
     * All operations in this record.
     */
    private final List<Operation> operations =
        new LinkedList<TransformationRecord.Operation>();

    /**
     * The sha256 of the original file.
     */
    private byte[] originalSHA256;

    /**
     * The sha256 of the transformed file.
     */
    private byte[] newSHA256;

    /**
     * Record a copy operation.
     * @param path the path of the resource that was copied.
     * @param originalOffset the original location of the start of the
     * resource's local section entry
     * @param newOffset the updated location of the start of the resource's
     * local section entry.
     */
    public void recordCopy(String path, long originalOffset, long newOffset) {
        operations.add(new Copy(path, originalOffset, newOffset));
    }

    /**
     * Record an uncompress operation.
     * @param path the path of the resource that was copied.
     * @param originalOffset the original location of the start of the
     * resource's local section entry
     * @param newOffset the updated location of the start of the resource's
     * local section entry
     * @param originalCompressedSize the original compressed size of the
     * resource
     * @param deflateParameters the deflate parameters that were used for the
     * resource
     */
    public void recordUncompress(String path,
        long originalCompressedSize,
        long originalOffset, long newOffset,
        JreDeflateParameters deflateParameters) {
        operations.add(new Uncompress(
            path, originalOffset, newOffset, originalCompressedSize,
            deflateParameters));
    }

    /**
     * Record a central directory relocation operation.
     * @param originalOffset the original offset of the start of the central
     * directory
     * @param newOffset the updated offset of the start of the central directory
     */
    public void recordRelocatCentralDirectory(final long originalOffset,
        final long newOffset) {
        operations.add(new RelocateCentralDirectory(originalOffset, newOffset));
    }

    /**
     * Records the sha256 of the original archive.
     * @param sha256 the sha256
     */
    public void recordOriginalSHA256(final byte[] sha256) {
        if (sha256.length != 32) {
            throw new IllegalArgumentException("not a sha256 hash");
        }
        originalSHA256 = Arrays.copyOf(sha256, sha256.length);
    }

    /**
     * Records the sha256 of the new archive.
     * @param sha256 the sha256
     */
    public void recordNewSHA256(final byte[] sha256) {
        if (sha256.length != 32) {
            throw new IllegalArgumentException("not a sha256 hash");
        }
        newSHA256 = Arrays.copyOf(sha256, sha256.length);
    }

    /**
     * Returns a live, unmodifiable view of the operations in this record.
     * @return as described
     */
    public List<Operation> getOperations() {
        return Collections.unmodifiableList(operations);
    }

    /**
     * Returns a copy of the SHA256 of the original file.
     * @return as described
     */
    public byte[] getOriginalSHA256() {
        return Arrays.copyOf(originalSHA256, originalSHA256.length);
    }

    /**
     * Returns a copy of the SHA256 of the transformed file.
     * @return as described
     */
    public byte[] getNewSHA256() {
        return Arrays.copyOf(newSHA256, newSHA256.length);
    }

    /**
     * Writes the record to the specified output.
     * @param out output to write the record to
     * @throws IOException if anything goes wrong
     */
    public void write(DataOutput out) throws IOException {
        if (originalSHA256 == null) {
            throw new IllegalStateException("original sha256 not yet set");
        }
        if (newSHA256 == null) {
            throw new IllegalStateException("new sha256 not yet set");
        }
        out.write(MAGIC.getBytes("UTF-8"));
        out.write(originalSHA256);
        out.write(newSHA256);
        out.writeInt(operations.size());
        for (Operation operation : operations) {
            out.writeByte(operation.getId());
            operation.write(out);
        }
    }

    /**
     * Discards any existing operations and reads the record from the specified
     * input.
     * @param in the input to read the record from
     * @throws IOException if anything goes wrong
     */
    public void read(DataInput in) throws IOException {
        byte[] expectedMagic = MAGIC.getBytes("UTF-8");
        byte[] actualMagic = new byte[expectedMagic.length];
        in.readFully(actualMagic);
        if (!Arrays.equals(expectedMagic, actualMagic)) {
            throw new IOException("bad magic on transformation record");
        }
        originalSHA256 = new byte[32];
        in.readFully(originalSHA256);
        newSHA256 = new byte[32];
        in.readFully(newSHA256);
        final int numOperations = in.readInt();
        operations.clear();
        for (int x=0; x<numOperations; x++) {
            final byte operationId = in.readByte();
            Operation operation = null;
            switch(operationId) {
                case COPY:
                    operation = new Copy();
                    break;
                case UNCOMPRESS:
                    operation = new Uncompress();
                    break;
                case RELOCATE_CENTRAL_DIRECTORY:
                    operation = new RelocateCentralDirectory();
                    break;
                default:
                    throw new RuntimeException("unknown id: " + operationId);
            }
            operation.read(in);
            operations.add(operation);
        }
    }

    @Override
    public String toString() {
        StringBuilder buffer = new StringBuilder("TransformationRecord:");
        buffer.append("\n  SHA256 of original archive: ")
            .append(MiscUtils.hexString(originalSHA256))
            .append("\n  SHA256 of transformed archive: ")
            .append(MiscUtils.hexString(newSHA256));
        for (Operation operation : operations) {
            buffer.append("\n  ").append(operation);
        }
        return buffer.toString();
    }
}
