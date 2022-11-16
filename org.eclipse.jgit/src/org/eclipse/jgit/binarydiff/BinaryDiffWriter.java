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

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Outputs a diff for binary data.
 *
 * @since 6.4
 */
public class BinaryDiffWriter {

    /**
     * Max length of a chunk.
     */
    public static final int CHUNK_SIZE = Byte.MAX_VALUE;

    private final ByteArrayOutputStream byteBuff = new ByteArrayOutputStream();
    private final DataOutputStream output;

    /**
     * Create a new BinaryDiffWriter.
     *
     * @param outputStream
     *            stream to copy binary diff.
     */
    public BinaryDiffWriter(DataOutputStream outputStream) {
        this.output = outputStream;
    }

    /**
     * Create a new BinaryDiffWriter.
     *
     * @param outputStream
     *            stream to copy binary diff.
     */
    public BinaryDiffWriter(OutputStream outputStream) {
        this(new DataOutputStream(outputStream));
    }

    /**
     * Encodes and write file size in to diff stream.
     *
     * @param fileSize
     *            size of file.
     * @throws java.io.IOException
     *            the stream write operation failed.
     */
    public void writeFileSize(long fileSize) throws IOException {
        encode(fileSize);
    }

    /**
     * Store length/offset information in 7bit encoding.
     * A set 8th bit means: continued in next byte
     * So 127 is stored as 0x7f, but 128 is stored as 0x80 0x01
     * (where 0x80 means 0, highest bit is marker).
     *
     * @param val Value to add in 7bit encoding.
     * @throws java.io.IOException
     *            the stream write operation failed.
     */
    private void encode(long val) throws IOException {
        while ( val > 0x7f) {
            output.write((byte)(val & 0x7f | 0x80));
            val >>>= 7;
        }
        output.write((byte)val);
    }

    /**
     * Calculates and write opcodes.
     *
     * @param offset
     *            offset in source.
     * @param length
     *            length of data.
     * @throws java.io.IOException
     *            the stream write operation failed.
     */
    private void writeOpcodes(long offset, long length) throws IOException {
        int code = 0x80;
        int codeIdx = 0;

        List<Integer> opcodes = new ArrayList<>();
        opcodes.add(0);

        // offset and length are written using a compact encoding
        // where the state of 7 lower bits specify the meaning of
        // the bytes that follow
        if ((offset & 0xff) > 0) {
            opcodes.add((int)(offset & 0xff));
            code |= 0x01;
        }

        if ((offset & 0xff00) > 0) {
            opcodes.add((int)(offset & 0xff00) >>> 8);
            code |= 0x02;
        }

        if ((offset & 0xff0000) > 0) {
            opcodes.add((int)(offset & 0xff0000) >>> 16);
            code |= 0x04;
        }

        if ((offset & 0xff000000) > 0) {
            opcodes.add((int)(offset & 0xff000000) >>> 24);
            code |= 0x08;
        }

        if ((length & 0xff) > 0) {
            opcodes.add((int)(length & 0xff));
            code |= 0x10;
        }

        if ((length & 0xff00) > 0) {
            opcodes.add((int)(length & 0xff00) >>> 8);
            code |= 0x20;
        }

        if ((length & 0xff0000) > 0) {
            opcodes.add((int)(length & 0xff0000) >>> 16);
            code |= 0x40;
        }

        opcodes.set(codeIdx, code);

        for (Integer opcode : opcodes) {
            output.write(opcode);
        }
    }

    /**
     * Adds data and write a copy command.
     *
     * @param offset
     *            offset in source.
     * @param length
     *            length of data.
     * @throws java.io.IOException
     *            the stream write operation failed.
     */
    public void addCopy(long offset, int length) throws IOException {
        writeBuffer();
        writeOpcodes(offset, length);
    }

    /**
     * Adds a data byte.
     *
     * @param dataByte byte for write.
     * @throws java.io.IOException
     *            the stream write operation failed.
     */
    public void addData(byte dataByte) throws IOException {
        byteBuff.write(dataByte);
        if (byteBuff.size() >= CHUNK_SIZE) {
            writeBuffer();
        }
    }

    /**
     * Writes a buffer size and data.
     *
     * @throws java.io.IOException
     *            the stream write operation failed.
     */
    private void writeBuffer() throws IOException {
        if (byteBuff.size() > 0) {
            output.write(byteBuff.size());
        }
        byteBuff.writeTo(output);
        byteBuff.reset();
    }

    /**
     * Flushes accumulated data bytes, if any.
     *
     * @throws java.io.IOException
     *            the stream write operation failed.
     */
    public void flush() throws IOException {
        writeBuffer();
    	output.flush(); 
    }

    /**
     * Writes closes the underlying stream.
     *
     * @throws java.io.IOException
     *            the stream write operation failed.
     */
    public void close() throws IOException {
        this.flush();
        output.close();
    }

}

