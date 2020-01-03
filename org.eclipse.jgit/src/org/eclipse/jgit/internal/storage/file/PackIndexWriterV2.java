/*
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.file;

import java.io.IOException;
import java.io.OutputStream;

import org.eclipse.jgit.transport.PackedObjectInfo;
import org.eclipse.jgit.util.NB;

/**
 * Creates the version 2 pack table of contents files.
 *
 * @see PackIndexWriter
 * @see PackIndexV2
 */
class PackIndexWriterV2 extends PackIndexWriter {
	private static final int MAX_OFFSET_32 = 0x7fffffff;
	private static final int IS_OFFSET_64 = 0x80000000;

	PackIndexWriterV2(final OutputStream dst) {
		super(dst);
	}

	/** {@inheritDoc} */
	@Override
	protected void writeImpl() throws IOException {
		writeTOC(2);
		writeFanOutTable();
		writeObjectNames();
		writeCRCs();
		writeOffset32();
		writeOffset64();
		writeChecksumFooter();
	}

	private void writeObjectNames() throws IOException {
		for (PackedObjectInfo oe : entries)
			oe.copyRawTo(out);
	}

	private void writeCRCs() throws IOException {
		for (PackedObjectInfo oe : entries) {
			NB.encodeInt32(tmp, 0, oe.getCRC());
			out.write(tmp, 0, 4);
		}
	}

	private void writeOffset32() throws IOException {
		int o64 = 0;
		for (PackedObjectInfo oe : entries) {
			final long o = oe.getOffset();
			if (o <= MAX_OFFSET_32)
				NB.encodeInt32(tmp, 0, (int) o);
			else
				NB.encodeInt32(tmp, 0, IS_OFFSET_64 | o64++);
			out.write(tmp, 0, 4);
		}
	}

	private void writeOffset64() throws IOException {
		for (PackedObjectInfo oe : entries) {
			final long o = oe.getOffset();
			if (MAX_OFFSET_32 < o) {
				NB.encodeInt64(tmp, 0, o);
				out.write(tmp, 0, 8);
			}
		}
	}
}
