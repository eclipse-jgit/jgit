/*
 * Copyright (C) 2007, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
 * Copyright (C) 2009, Google Inc.
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

import java.io.EOFException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import org.eclipse.jgit.errors.ObjectWritingException;

/** Creates loose objects in a {@link ObjectDirectory}. */
class ObjectDirectoryInserter extends ObjectInserter {
	private final ObjectDirectory db;

	private final Config config;

	private Deflater deflate;

	ObjectDirectoryInserter(final ObjectDirectory dest, final Config cfg) {
		db = dest;
		config = cfg;
	}

	@Override
	public ObjectId insert(final int type, long len, final InputStream is)
			throws IOException {
		final MessageDigest md = digest();
		final File tmp = toTemp(md, type, len, is);
		final ObjectId id = ObjectId.fromRaw(md.digest());
		if (db.hasObject(id)) {
			// Object is already in the repository, remove temporary file.
			//
			tmp.delete();
			return id;
		}

		final File dst = db.fileFor(id);
		if (tmp.renameTo(dst))
			return id;

		// Maybe the directory doesn't exist yet as the object
		// directories are always lazily created. Note that we
		// try the rename first as the directory likely does exist.
		//
		dst.getParentFile().mkdir();
		if (tmp.renameTo(dst))
			return id;

		if (db.hasObject(id)) {
			tmp.delete();
			return id;
		}

		// The object failed to be renamed into its proper
		// location and it doesn't exist in the repository
		// either. We really don't know what went wrong, so
		// fail.
		//
		tmp.delete();
		throw new ObjectWritingException("Unable to create new object: " + dst);
	}

	@Override
	public void flush() throws IOException {
		// Do nothing. Objects are immediately visible.
	}

	@Override
	public void release() {
		if (deflate != null) {
			try {
				deflate.end();
			} finally {
				deflate = null;
			}
		}
	}

	private File toTemp(final MessageDigest md, final int type, long len,
			final InputStream is) throws IOException, FileNotFoundException,
			Error {
		boolean delete = true;
		File tmp = File.createTempFile("noz", null, db.getDirectory());
		try {
			DigestOutputStream dOut = new DigestOutputStream(
					compress(new FileOutputStream(tmp)), md);
			try {
				dOut.write(Constants.encodedTypeString(type));
				dOut.write((byte) ' ');
				dOut.write(Constants.encodeASCII(len));
				dOut.write((byte) 0);

				final byte[] buf = buffer();
				while (len > 0) {
					int n = is.read(buf, 0, (int) Math.min(len, buf.length));
					if (n <= 0)
						throw shortInput(len);
					dOut.write(buf, 0, n);
					len -= n;
				}
			} finally {
				dOut.close();
			}

			tmp.setReadOnly();
			delete = false;
			return tmp;
		} finally {
			if (delete)
				tmp.delete();
		}
	}

	private DeflaterOutputStream compress(final OutputStream out) {
		if (deflate == null)
			deflate = new Deflater(config.get(CoreConfig.KEY).getCompression());
		else
			deflate.reset();
		return new DeflaterOutputStream(out, deflate);
	}

	private static EOFException shortInput(long missing) {
		return new EOFException("Input did not match supplied length. "
				+ missing + " bytes are missing.");
	}
}
