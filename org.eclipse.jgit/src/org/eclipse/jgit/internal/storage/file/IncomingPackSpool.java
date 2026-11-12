/*
 * Copyright (c) 2026 Qualcomm Technologies, Inc. and/or its subsidiaries.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.file;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.Objects;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.util.FileUtils;

/** Disposable, unindexed input archive; never registered as an object database. */
final class IncomingPackSpool implements AutoCloseable {
	private final File path;

	private final RandomAccessFile file;

	private boolean closed;

	IncomingPackSpool(ObjectDirectory main) throws IOException {
		path = File.createTempFile("routing_incoming_", ".spool", //$NON-NLS-1$ //$NON-NLS-2$
				main.getDirectory());
		try {
			file = new RandomAccessFile(path, "rw"); //$NON-NLS-1$
		} catch (IOException failure) {
			try {
				FileUtils.delete(path, FileUtils.RETRY | FileUtils.SKIP_MISSING);
			} catch (IOException cleanup) {
				failure.addSuppressed(cleanup);
			}
			throw failure;
		}
	}

	File getFile() {
		return path;
	}

	synchronized void append(byte[] data, int offset, int count)
			throws IOException {
		checkOpen();
		file.seek(file.length());
		file.write(data, offset, count);
	}

	synchronized long length() throws IOException {
		checkOpen();
		return file.length();
	}

	synchronized int read(long position, byte[] data, int offset, int count)
			throws IOException {
		checkOpen();
		return file.getChannel().read(ByteBuffer.wrap(data, offset, count), position);
	}

	synchronized InputStream openRange(long offset, long count) throws IOException {
		checkOpen();
		if (offset < 0 || count < 0 || offset > Long.MAX_VALUE - count) {
			throw new IllegalArgumentException();
		}
		if (offset + count > file.length()) {
			throw truncated();
		}
		return new InputStream() {
			private final byte[] single = new byte[1];

			private long position = offset;

			private long remaining = count;

			private boolean rangeClosed;

			@Override
			public int read() throws IOException {
				return read(single, 0, 1) < 0 ? -1 : single[0] & 0xff;
			}

			@Override
			public int read(byte[] data, int pos, int len) throws IOException {
				Objects.checkFromIndexSize(pos, len, data.length);
				synchronized (IncomingPackSpool.this) {
					checkOpen();
					if (rangeClosed) {
						throw new IOException(JGitText.get().inputStreamClosed);
					}
					if (len == 0) {
						return 0;
					}
					if (remaining == 0) {
						return -1;
					}
					int n = IncomingPackSpool.this.read(position, data, pos,
							(int) Math.min(len, remaining));
					if (n <= 0) {
						throw truncated();
					}
					position += n;
					remaining -= n;
					return n;
				}
			}

			@Override
			public void close() {
				rangeClosed = true;
			}
		};
	}

	private void checkOpen() throws IOException {
		if (closed) {
			throw new IOException(JGitText.get().inputStreamClosed);
		}
	}

	private static EOFException truncated() {
		return new EOFException(JGitText.get().packfileIsTruncatedNoParam);
	}

	@Override
	public synchronized void close() throws IOException {
		if (closed) {
			return;
		}
		closed = true;
		IOException failure = null;
		try {
			file.close();
		} catch (IOException cleanup) {
			failure = cleanup;
		}
		try {
			FileUtils.delete(path, FileUtils.RETRY | FileUtils.SKIP_MISSING);
		} catch (IOException cleanup) {
			if (failure == null) {
				failure = cleanup;
			} else {
				failure.addSuppressed(cleanup);
			}
		}
		if (failure != null) {
			throw failure;
		}
	}
}
