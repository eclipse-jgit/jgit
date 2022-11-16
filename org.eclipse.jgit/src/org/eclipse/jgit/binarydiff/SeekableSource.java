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

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Wraps a byte buffer as a source.
 *
 * @since 6.4
 */
public class SeekableSource {

    private ByteBuffer sourceBuffer;
    private ByteBuffer currentBuffer;

    /**
     * Creates a new seekable source, wrapping the specified byte array.
     *
     * @param source
     *            the content array.
     */
    public SeekableSource(byte[] source) {
        this(ByteBuffer.wrap(source));
    }

    /**
     * Creates a new seekable source, wrapping the specified byte buffer.
     *
     * @param sourceBuffer
     *            the content source byte buffer.
     */
    public SeekableSource(ByteBuffer sourceBuffer) {
        if (sourceBuffer == null) {
            throw new NullPointerException("Source buffer should not be null"); //$NON-NLS-1$
        }
        this.sourceBuffer = sourceBuffer;
        sourceBuffer.rewind();
        try {
            seek(0);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Sets the position for the next {@link #read(ByteBuffer)}.
     *
     * @param position
     *            seek to position.
     * @throws java.io.IOException
     *            if the input could not be read.
     */
    public void seek(long position) throws IOException {
        currentBuffer = sourceBuffer.slice();
        if (position > currentBuffer.limit()) {
            throw new IOException("The position" + position +
                    " > " + currentBuffer.limit() + " cannot seek"); //$NON-NLS-1$
        }
        currentBuffer.position((int)position);
    }

    /**
     * Reads up to {@link ByteBuffer#remaining()} bytes from the source,
     *
     * @param destination
     *            byte buffer for read
     * @return returning the number of bytes read, or -1 if no bytes were read
     * and EOF was reached.
     */
    public int read(ByteBuffer destination) {
        if (!currentBuffer.hasRemaining()) {
            return -1;
        }
        int byteNumber = 0;
        while (currentBuffer.hasRemaining() && destination.hasRemaining()) {
            destination.put(currentBuffer.get());
            byteNumber++;
        }
        return byteNumber;
    }

    /**
     * Closes this stream, and closes the underlying.
     */
    public void close() {
        sourceBuffer = null;
        currentBuffer = null;
    }

}
