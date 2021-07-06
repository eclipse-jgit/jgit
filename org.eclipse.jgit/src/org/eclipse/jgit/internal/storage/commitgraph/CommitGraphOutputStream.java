/*
 * Copyright (C) 2021, Tencent.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.commitgraph;

import static org.eclipse.jgit.internal.storage.commitgraph.CommitGraphConstants.COMMIT_GRAPH_MAGIC;

import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.util.NB;

/**
 * Custom output stream to support
 * {@link org.eclipse.jgit.internal.storage.commitgraph.CommitGraphWriter}.
 * <p>
 * It keeps a digest and checks every
 * {@value CommitGraphOutputStream#BYTES_TO_WRITE_BEFORE_CANCEL_CHECK} bytes for
 * cancellation.
 */
class CommitGraphOutputStream extends OutputStream {

	private static final int BYTES_TO_WRITE_BEFORE_CANCEL_CHECK = 128 * 1024;

	private final ProgressMonitor writeMonitor;

	private final OutputStream out;

	private final MessageDigest md = Constants.newMessageDigest();

	private long count;

	private long checkCancelAt;

	private final byte[] headerBuffer = new byte[16];

	/**
	 * Initialize a commit-graph output stream.
	 *
	 * @param writeMonitor
	 *            monitor to update on output progress.
	 * @param out
	 *            target stream to receive all contents.
	 */
	CommitGraphOutputStream(ProgressMonitor writeMonitor, OutputStream out) {
		this.writeMonitor = writeMonitor;
		this.out = out;
		this.checkCancelAt = BYTES_TO_WRITE_BEFORE_CANCEL_CHECK;
	}

	/** {@inheritDoc} */
	@Override
	public final void write(int b) throws IOException {
		if (checkCancelAt <= count) {
			if (writeMonitor.isCancelled()) {
				throw new IOException(JGitText
						.get().commitGraphWritingCancelled);
			}
			checkCancelAt = count + BYTES_TO_WRITE_BEFORE_CANCEL_CHECK;
		}

		count++;
		out.write(b);
		md.update((byte) b);
	}

	/** {@inheritDoc} */
	@Override
	public final void write(byte[] b, int off, int len) throws IOException {
		while (0 < len) {
			int n = Math.min(len, BYTES_TO_WRITE_BEFORE_CANCEL_CHECK);

			if (checkCancelAt <= count) {
				if (writeMonitor.isCancelled()) {
					throw new IOException(JGitText
							.get().commitGraphWritingCancelled);
				}
				checkCancelAt = count + BYTES_TO_WRITE_BEFORE_CANCEL_CHECK;
			}

			out.write(b, off, n);
			md.update(b, off, n);
			count += n;

			off += n;
			len -= n;
		}
	}

	/** {@inheritDoc} */
	@Override
	public void flush() throws IOException {
		out.flush();
	}

	void writeFileHeader(int version, int hashVersion, int chunksNumber)
			throws IOException {
		NB.encodeInt32(headerBuffer, 0, COMMIT_GRAPH_MAGIC);
		byte[] buff = { (byte) version, (byte) hashVersion, (byte) chunksNumber,
				(byte) 0 };
		System.arraycopy(buff, 0, headerBuffer, 4, 4);
		write(headerBuffer, 0, 8);
	}

	/** @return obtain the current SHA-1 digest. */
	byte[] getDigest() {
		return md.digest();
	}

	void updateMonitor() {
		writeMonitor.update(1);
	}
}
