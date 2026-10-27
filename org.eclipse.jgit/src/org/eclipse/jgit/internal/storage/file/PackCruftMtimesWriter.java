/*
 * Copyright (C) 2026, SAP SE and others
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
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.util.NB;

/**
 * Writes a {@code .mtimes} file for a cruft pack.
 *
 * <p>
 * Format (all integers big-endian):
 * <ul>
 * <li>Magic: {@code 'M','T','I','M'} (4 bytes)</li>
 * <li>Version: {@code 1} (uint32)</li>
 * <li>Object count: N (uint32)</li>
 * <li>N × mtime (uint32 seconds since epoch), in SHA-1-sorted index order</li>
 * </ul>
 */
public class PackCruftMtimesWriter {

	/** Magic bytes identifying a {@code .mtimes} file. */
	static final byte[] MAGIC = { 'M', 'T', 'I', 'M' };

	/** The only version this writer produces. */
	static final int VERSION_1 = 1;

	private PackCruftMtimesWriter() {
	}

	/**
	 * Write a {@code .mtimes} file to {@code out}.
	 *
	 * @param out
	 *            destination stream
	 * @param objectsInIndexOrder
	 *            object IDs in SHA-1-sorted (primary pack index) order
	 * @param mtimes
	 *            map from object ID to mtime in seconds since epoch; objects
	 *            absent from the map are written with mtime {@code 0}
	 * @throws IOException
	 *             if writing fails
	 */
	public static void write(OutputStream out,
			List<? extends ObjectId> objectsInIndexOrder,
			Map<ObjectId, Long> mtimes) throws IOException {
		int count = objectsInIndexOrder.size();
		byte[] buf = new byte[4];

		out.write(MAGIC);
		NB.encodeInt32(buf, 0, VERSION_1);
		out.write(buf);
		NB.encodeInt32(buf, 0, count);
		out.write(buf);

		for (ObjectId id : objectsInIndexOrder) {
			Long mt = mtimes.get(id);
			long secs = mt != null ? mt.longValue() : 0L;
			NB.encodeInt32(buf, 0, (int) (secs & 0xFFFFFFFFL));
			out.write(buf);
		}
	}
}
