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

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.util.NB;

/**
 * Writes a {@code .mtimes} file for a cruft pack.
 *
 * <p>
 * Format (all integers big-endian / network byte order):
 * <ul>
 * <li>Magic: {@code 'M','T','M','E'} (4 bytes)</li>
 * <li>Version: {@code 1} (uint32)</li>
 * <li>Hash function: {@code 1} for SHA-1 (uint32)</li>
 * <li>N × mtime (uint32 seconds since epoch), in pack index order</li>
 * <li>Trailer: pack checksum (20 bytes), then SHA-1 of all preceding bytes
 * (20 bytes)</li>
 * </ul>
 */
public class PackCruftMtimesWriter {

	/** Magic bytes identifying a {@code .mtimes} file. */
	static final byte[] MAGIC = { 'M', 'T', 'M', 'E' };

	/** The only version this writer produces. */
	static final int VERSION_1 = 1;

	/**
	 * Hash function identifier for SHA-1.
	 *
	 * @TODO(ms) move this to Constants when we add SHA-256 support to JGit
	 */
	static final int HASH_ID_SHA1 = 1;

	private final List<? extends ObjectId> objectsInIndexOrder;

	private final Map<ObjectId, Long> mtimes;

	private final ObjectId packId;

	/**
	 * Create a writer for a {@code .mtimes} file.
	 *
	 * @param objectsInIndexOrder
	 *            object IDs in pack index (SHA-1-sorted) order
	 * @param mtimes
	 *            map from object ID to mtime in seconds since epoch; objects
	 *            absent from the map are written with mtime {@code 0}
	 * @param packId
	 *            the SHA-1 checksum of the corresponding pack file, written as
	 *            the first part of the trailer
	 */
	public PackCruftMtimesWriter(List<? extends ObjectId> objectsInIndexOrder,
			Map<ObjectId, Long> mtimes, ObjectId packId) {
		this.objectsInIndexOrder = objectsInIndexOrder;
		this.mtimes = mtimes;
		this.packId = packId;
	}

	/**
	 * Write the {@code .mtimes} file to {@code out}.
	 *
	 * @param out
	 *            destination stream
	 * @throws IOException
	 *             if writing fails
	 */
	public void write(OutputStream out) throws IOException {
		MessageDigest md = Constants.newMessageDigest();
		BufferedOutputStream buf = new BufferedOutputStream(out);
		DigestOutputStream dos = new DigestOutputStream(buf, md);
		byte[] b = new byte[4];

		dos.write(MAGIC);
		NB.encodeInt32(b, 0, VERSION_1);
		dos.write(b);
		NB.encodeInt32(b, 0, HASH_ID_SHA1);
		dos.write(b);

		for (ObjectId id : objectsInIndexOrder) {
			Long mt = mtimes.get(id);
			long secs = mt != null ? mt.longValue() : 0L;
			NB.encodeInt32(b, 0, (int) (secs & 0xFFFFFFFFL));
			dos.write(b);
		}

		byte[] packChecksum = new byte[Constants.OBJECT_ID_LENGTH];
		packId.copyRawTo(packChecksum, 0);
		dos.write(packChecksum);
		buf.write(md.digest());
		buf.flush();
	}
}
