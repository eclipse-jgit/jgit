/*
 * Copyright (C) 2015, Matthias Sohn <matthias.sohn@sap.com>
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
package org.eclipse.jgit.lfs.internal;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.text.MessageFormat;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.internal.storage.file.LockFile;
import org.eclipse.jgit.lfs.errors.CorruptLongObjectException;
import org.eclipse.jgit.lfs.lib.AnyLongObjectId;
import org.eclipse.jgit.lfs.lib.Constants;
import org.eclipse.jgit.lfs.lib.LongObjectId;

/**
 * Output stream writing content to a {@link LockFile} which is committed on
 * close(). The stream checks if the hash of the stream content matches the
 * id.
 */
public class AtomicObjectOutputStream extends OutputStream {

	private LockFile locked;

	private DigestOutputStream out;

	private boolean aborted;

	private AnyLongObjectId id;

	/**
	 * @param path
	 * @param id
	 * @throws IOException
	 */
	public AtomicObjectOutputStream(Path path, AnyLongObjectId id)
			throws IOException {
		locked = new LockFile(path.toFile());
		locked.lock();
		this.id = id;
		out = new DigestOutputStream(locked.getOutputStream(),
				Constants.newMessageDigest());
	}

	/**
	 * @param path
	 * @throws IOException
	 */
	public AtomicObjectOutputStream(Path path) throws IOException {
		this(path, null);
	}

	/**
	 * @return content hash of the object which was streamed through this
	 *         stream. May return {@code null} if called before closing this stream.
	 */
	public @Nullable AnyLongObjectId getId() {
		return id;
	}

	@Override
	public void write(int b) throws IOException {
		out.write(b);
	}

	@Override
	public void write(byte[] b) throws IOException {
		out.write(b);
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		out.write(b, off, len);
	}

	@Override
	public void close() throws IOException {
		out.close();
		if (!aborted) {
			if (id != null) {
				verifyHash();
			} else {
				id = LongObjectId.fromRaw(out.getMessageDigest().digest());
			}
			locked.commit();
		}
	}

	private void verifyHash() {
		AnyLongObjectId contentHash = LongObjectId
				.fromRaw(out.getMessageDigest().digest());
		if (!contentHash.equals(id)) {
			abort();
			throw new CorruptLongObjectException(id, contentHash,
					MessageFormat.format(LfsText.get().corruptLongObject,
							contentHash, id));
		}
	}

	/**
	 * Aborts the stream. Temporary file will be deleted
	 */
	public void abort() {
		locked.unlock();
		aborted = true;
	}
}
