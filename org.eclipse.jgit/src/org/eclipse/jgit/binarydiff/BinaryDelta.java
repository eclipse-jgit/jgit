/*
 * Copyright (C) 2022 Yuriy Mitrofanov <mitr15fan15v@gmail.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.binarydiff;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

/**
 * Class for computing binary deltas against a source.
 * The source is read by blocks and a hash is computed per block.
 * Then the target is scanned for matching blocks.
 * @since 6.4
 */
public class BinaryDelta {

    /**
     * Default size of 16.
     * Use a size like 64 or 128 for large files.
     * Default for {@link #setChunkSize}.
     */
    public static final int DEFAULT_CHUNK_SIZE = 1 << 4;

    /**
     * Chunk Size {@link #DEFAULT_CHUNK_SIZE}.
     */
    private int chunk;
    private TargetState target;
    private BinaryDiffWriter output;

    /**
     * Create a new Binary delta.
     *
     * @param output
     *            stream to copy binary diff.
     */
    public BinaryDelta(OutputStream output) {
        this.output = new BinaryDiffWriter(output);
        setChunkSize(DEFAULT_CHUNK_SIZE);
    }

    /**
     * Sets the chunk size used.
     * Larger chunks are faster and use less memory, but create larger patches.
     *
     * @param size
     *            size of chunks.
     */
    public void setChunkSize(int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("Invalid chunk size"); //$NON-NLS-1$
        }
        chunk = size;
    }

    /**
     * Compares the source bytes with target bytes, writing to output.
     *
     * @param source
     *            the source array.
     * @param target
     *            the target array.
     * @throws java.io.IOException
     *            the stream write operation failed.
     */
    public void compute(byte[] source, byte[] target) throws IOException {
        compute(new SeekableSource(source),
                new ByteArrayInputStream(target), output);
    }

    /**
     * Compares the source bytes with target input, writing to output.
     *
     * @param sourceBytes
     *            the source array.
     * @param inputStream
     *            the target stream.
     * @throws java.io.IOException
     *            the stream read-write operation failed.
     */
    public void compute(byte[] sourceBytes, InputStream inputStream)
            throws IOException {
        compute(new SeekableSource(sourceBytes), inputStream, output);
    }

    /**
     * Compares the source with a target, writing to output.
     *
     * @param seekSource
     *            the source.
     * @param targetStream
     *            the target stream.
     * @param output
     *            the output stream, will be closed.
     * @throws java.io.IOException
     *            the stream read-write operation failed.
     */
    public void compute(SeekableSource seekSource,
                        InputStream targetStream, BinaryDiffWriter output)
            throws IOException {
        this.output = output;
        SourceState source = new SourceState(seekSource);
        target = new TargetState(targetStream);
        while (!target.eof()) {
            long index = target.find(source);
            if (index != -1) {
                long offset = index * chunk;
                source.seek(offset);
                int match = target.longestMatch(source);
                if (match >= chunk) {
                    output.addCopy(offset, match);
                } else {
                    target.targetBuffer.position(
                            target.targetBuffer.position() - match);
                    addData();
                }
            } else {
                addData();
            }
        }
        output.close();
    }

    /**
     * Adds a source data byte in to diff stream.
     *
     * @throws java.io.IOException
     *            the stream write operation failed.
     */
    private void addData() throws IOException {
        int i = target.read();
        if (i == -1) {
            return;
        }
        output.addData((byte)i);
    }

    /**
     * Encode and write file size in to diff stream.
     *
     * @param fileSize
     *            size of file.
     * @throws java.io.IOException
     *             if the stream could not be written.
     */
    public void writeFileSize(long fileSize)  throws IOException {
        output.writeFileSize(fileSize);
    }

    /**
     * The source state.
     *
     */
    class SourceState {

        private final Checksum checksum;
        private final SeekableSource source;

        /**
         * Creates a new source state.
         *
         * @param source
         *            source data.
         * @throws java.io.IOException
         *             if the stream could not be read.
         */
        private SourceState(SeekableSource source) throws IOException {
            checksum = new Checksum(source, chunk);
            this.source = source;
            source.seek(0);
        }

        /**
         * Sets the position.
         *
         * @param index
         *            position for seek.
         * @throws java.io.IOException
         *            if the input could not be read.
         */
        private void seek(long index) throws IOException {
            source.seek(index);
        }

    }

    /**
     * The target state
     *
     */
    class TargetState {

        private final ReadableByteChannel byteChannel;

        private final ByteBuffer targetBuffer = ByteBuffer.allocate(
                blockSize());
        private final ByteBuffer sourceBuffer = ByteBuffer.allocate(
                blockSize());

        private long hash;
        private boolean hashReset = true;
        private boolean eof;

        /**
         * Creates a new target state.
         *
         * @param targetInputStream
         *            source input stream.
         */
        private TargetState(InputStream targetInputStream) {
            byteChannel = Channels.newChannel(targetInputStream);
            targetBuffer.limit(0);
        }

        /**
         * Get block size.
         *
         * @return min block size.
         */
        private int blockSize() {
            return Math.min(1024 * 8, chunk * 8);
        }

        /**
         * Find the index of the next N bytes of the stream.
         * @param source
         *            source for read.
         * @return checksum index value.
         * @throws java.io.IOException
         *             if the input could not be read.
         */
        private long find(SourceState source) throws IOException {
            if (eof) {
                return -1;
            }
            sourceBuffer.clear();
            sourceBuffer.limit(0);
            if (hashReset) {
                while (targetBuffer.remaining() < chunk) {
                    targetBuffer.compact();
                    int read = byteChannel.read(targetBuffer);
                    targetBuffer.flip();
                    if (read == -1) {
                        return -1;
                    }
                }
                hash = Checksum.queryChecksum(targetBuffer, chunk);
                hashReset = false;
            }
            return source.checksum.findChecksumIndex(hash);
        }

        /**
         * Returns the eof.
         *
         * @return the boolean.
         */
        private boolean eof() {
            return eof;
        }

        /**
         * Reads a byte.
         *
         * @return the int.
         * @throws java.io.IOException
         *             if the input could not be read.
         */
        private int read() throws IOException {
            if (targetBuffer.remaining() <= chunk) {
                readMore();
                if (!targetBuffer.hasRemaining()) {
                    eof = true;
                    return -1;
                }
            }
            byte b = targetBuffer.get();
            if (targetBuffer.remaining() >= chunk) {
                byte newByte = targetBuffer.get(targetBuffer.position() +
                        chunk -1);
                hash = Checksum.incrementChecksum(hash, b, newByte, chunk);
            }
            return b & 0xFF;
        }

        /**
         * Returns the longest match length at the source location.
         * @param source
         *            source for read.
         * @return length the longest match.
         * @throws java.io.IOException
         *             if the input could not be read.
         */
        private int longestMatch(SourceState source) throws IOException {
            int match = 0;
            hashReset = true;
            while (true) {
                if (!sourceBuffer.hasRemaining()) {
                    sourceBuffer.clear();
                    int read = source.source.read(sourceBuffer);
                    sourceBuffer.flip();
                    if (read == -1) {
                        return match;
                    }
                }
                if (!targetBuffer.hasRemaining()) {
                    readMore();
                    if (!targetBuffer.hasRemaining()) {
                        eof = true;
                        return match;
                    }
                }
                if (sourceBuffer.get() != targetBuffer.get()) {
                    targetBuffer.position(
                            targetBuffer.position() - 1);
                    return match;
                }
                match++;
            }
        }

        /**
         * Reads a byte.
         *
         * @throws java.io.IOException
         *             if the input could not be read.
         */
        private void readMore() throws IOException {
            targetBuffer.compact();
            byteChannel.read(targetBuffer);
            targetBuffer.flip();
        }
    }

}
