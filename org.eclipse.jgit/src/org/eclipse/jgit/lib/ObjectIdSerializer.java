/*
 * Copyright (C) 2009, The Android Open Source Project
 * Copyright (C) 2009, Shawn O. Pearce <spearce@spearce.org>
 * Copyright (C) 2018, David Pursehouse <david.pursehouse@gmail.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.lib;

import static org.eclipse.jgit.lib.Constants.OBJECT_ID_LENGTH;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.util.IO;

/**
 * Helper to serialize {@link ObjectId} instances. {@link ObjectId} is already
 * serializable, but this class provides methods to handle null and non-null
 * instances.
 *
 * @since 4.11
 */
public class ObjectIdSerializer {
	/*
	 * Marker to indicate a null ObjectId instance.
	 */
	private static final byte NULL_MARKER = 0;

	/*
	 * Marker to indicate a non-null ObjectId instance.
	 */
	private static final byte NON_NULL_MARKER = 1;

	/**
	 * Write a possibly null {@link ObjectId} to the stream, using markers to
	 * differentiate null and non-null instances.
	 *
	 * <p>
	 * If the id is non-null, writes a {@link #NON_NULL_MARKER} followed by the
	 * id's words. If it is null, writes a {@link #NULL_MARKER} and nothing
	 * else.
	 *
	 * @param out
	 *            the output stream
	 * @param id
	 *            the object id to serialize; may be null
	 * @throws IOException
	 *             the stream writing failed
	 */
	public static void write(OutputStream out, @Nullable AnyObjectId id)
			throws IOException {
		if (id != null) {
			out.write(NON_NULL_MARKER);
			writeWithoutMarker(out, id);
		} else {
			out.write(NULL_MARKER);
		}
	}

	/**
	 * Write a non-null {@link ObjectId} to the stream.
	 *
	 * @param out
	 *            the output stream
	 * @param id
	 *            the object id to serialize; never null
	 * @throws IOException
	 *             the stream writing failed
	 * @since 4.11
	 */
	public static void writeWithoutMarker(OutputStream out, @NonNull AnyObjectId id)
			throws IOException {
		id.copyRawTo(out);
	}

	/**
	 * Read a possibly null {@link ObjectId} from the stream.
	 *
	 * Reads the first byte of the stream, which is expected to be either
	 * {@link #NON_NULL_MARKER} or {@link #NULL_MARKER}.
	 *
	 * @param in
	 *            the input stream
	 * @return the object id, or null
	 * @throws IOException
	 *             there was an error reading the stream
	 */
	@Nullable
	public static ObjectId read(InputStream in) throws IOException {
		byte marker = (byte) in.read();
		switch (marker) {
		case NULL_MARKER:
			return null;
		case NON_NULL_MARKER:
			return readWithoutMarker(in);
		default:
			throw new IOException("Invalid flag before ObjectId: " + marker); //$NON-NLS-1$
		}
	}

	/**
	 * Read a non-null {@link ObjectId} from the stream.
	 *
	 * @param in
	 *            the input stream
	 * @return the object id; never null
	 * @throws IOException
	 *             there was an error reading the stream
	 * @since 4.11
	 */
	@NonNull
	public static ObjectId readWithoutMarker(InputStream in) throws IOException {
		final byte[] b = new byte[OBJECT_ID_LENGTH];
		IO.readFully(in, b, 0, OBJECT_ID_LENGTH);
		return ObjectId.fromRaw(b);
	}

	private ObjectIdSerializer() {
	}
}
