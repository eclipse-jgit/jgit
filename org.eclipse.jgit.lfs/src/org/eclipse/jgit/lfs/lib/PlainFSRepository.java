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
package org.eclipse.jgit.lfs.lib;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.DigestOutputStream;
import java.text.MessageFormat;

import org.eclipse.jgit.internal.storage.file.LockFile;
import org.eclipse.jgit.lfs.errors.CorruptLongObjectException;
import org.eclipse.jgit.lfs.internal.LfsText;
import org.eclipse.jgit.util.FS;

/**
 * Repository storing large objects in the file system
 */
public class PlainFSRepository implements LargeFileRepository {

	/**
	 * Output stream writing content to a {@link LockFile} which is committed on
	 * close(). The stream checks if the hash of the stream content matches the
	 * id.
	 */
	private static class AtomicObjectOutputStream extends OutputStream {

		private LockFile locked;

		private DigestOutputStream out;

		private boolean aborted;

		private AnyLongObjectId id;

		AtomicObjectOutputStream(Path path, AnyLongObjectId id)
				throws IOException {
			locked = new LockFile(path.toFile(), FS.DETECTED);
			locked.lock();
			this.id = id;
			out = new DigestOutputStream(locked.getOutputStream(),
					Constants.newMessageDigest());
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
			try {
				out.close();
				if (!aborted) {
					AnyLongObjectId contentHash = LongObjectId
							.fromRaw(out.getMessageDigest().digest());
					if (contentHash.equals(id)) {
						locked.commit();
					} else {
						abort();
						throw new CorruptLongObjectException(id, contentHash,
								MessageFormat.format(LfsText.get().corruptLongObject, contentHash, id));
					}
				}
			} finally {
				super.close();
			}
		}

		void abort() {
			locked.unlock();
			aborted = true;
		}
	}

	private Path dir;

	private AtomicObjectOutputStream out;

	/**
	 * @param dir
	 *            storage directory
	 * @throws IOException
	 */
	public PlainFSRepository(Path dir) throws IOException {
		this.dir = dir;
		Files.createDirectories(dir);
	}

	@Override
	public boolean exists(AnyLongObjectId id) {
		return Files.exists(getPath(id));
	}

	@Override
	public long getLength(AnyLongObjectId id) throws IOException {
		return Files.size(getPath(id));
	}

	@Override
	public ReadableByteChannel getReadChannel(AnyLongObjectId id)
			throws IOException {
		return FileChannel.open(getPath(id), StandardOpenOption.READ);
	}

	@Override
	public WritableByteChannel getWriteChannel(AnyLongObjectId id)
			throws IOException {
		Path path = getPath(id);
		Files.createDirectories(path.getParent());
		out = new AtomicObjectOutputStream(path, id);
		return Channels.newChannel(out);
	}

	@Override
	public void abortWrite() {
		if (out != null) {
			out.abort();
		}
	}

	/**
	 * @return the path of the storage directory
	 */
	public Path getDir() {
		return dir;
	}

	/**
	 * @param id
	 *            id of a large object
	 * @return path the object's storage path
	 */
	protected Path getPath(AnyLongObjectId id) {
		StringBuilder s = new StringBuilder(
				Constants.LONG_OBJECT_ID_STRING_LENGTH + 6);
		s.append(toHexCharArray(id.getFirstByte())).append('/');
		s.append(toHexCharArray(id.getSecondByte())).append('/');
		s.append(id.name());
		return dir.resolve(s.toString());
	}

	private char[] toHexCharArray(int b) {
		final char[] dst = new char[2];
		formatHexChar(dst, 0, b);
		return dst;
	}

	private static final char[] hexchar = { '0', '1', '2', '3', '4', '5', '6',
			'7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };

	static void formatHexChar(final char[] dst, final int p, int b) {
		int o = p + 1;
		while (o >= p && b != 0) {
			dst[o--] = hexchar[b & 0xf];
			b >>>= 4;
		}
		while (o >= p)
			dst[o--] = '0';
	}
}
