/*
 * Copyright (C) 2009, The Android Open Source Project
 * Copyright (C) 2009, Shawn O. Pearce <spearce@spearce.org>
 * Copyright (C) 2018, David Pursehouse <david.pursehouse@gmail.com>
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
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
	 * @since 5.0
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
	 * @since 5.0
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
