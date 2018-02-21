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

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.util.IO;

/**
 * Helper to serialize {@link ObjectId} instances. {@link ObjectId} is already
 * serializable, but this class provides a more optimal implementation. It
 * writes out a flag (0 or 1) followed by the object's words, or nothing if it
 * was a null id.
 *
 * @since 4.11
 */
public class ObjectIdSerializer {
	/**
	 * @param out
	 *            the output stream
	 * @param id
	 *            the object id to serialize
	 * @throws IOException
	 *             the stream writing failed
	 */
	public static void write(OutputStream out, @Nullable AnyObjectId id)
			throws IOException {
		if (id != null) {
			out.write((byte) 1);
			id.copyRawTo(out);
		} else {
			out.write((byte) 0);
		}
	}

	/**
	 * @param in
	 *            the input stream
	 * @return the object id
	 * @throws IOException
	 *             there was an error reading the stream
	 */
	@Nullable
	public static ObjectId read(InputStream in) throws IOException {
		switch (in.read()) {
		case 0:
			return null;
		case 1:
			final byte[] b = new byte[OBJECT_ID_LENGTH];
			IO.readFully(in, b, 0, OBJECT_ID_LENGTH);
			return ObjectId.fromRaw(b);
		default:
			throw new IOException("Invalid flag before ObjectId"); //$NON-NLS-1$
		}
	}

	private ObjectIdSerializer() {
	}
}
