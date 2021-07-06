/*
 * Copyright (C) 2022, Tencent.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.util.io;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ProgressMonitor;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.security.MessageDigest;

/**
 * An OutputStream that keeps a digest and checks every N bytes for
 * cancellation.
 */
public class CancellableDigestOutputStream extends OutputStream {

	private static final int BYTES_TO_WRITE_BEFORE_CANCEL_CHECK = 128 * 1024;

	/** monitor to update on output progress and check cancel **/
	final ProgressMonitor writeMonitor;

	private final OutputStream out;

	private final MessageDigest md = Constants.newMessageDigest();

	private long count;

	private long checkCancelAt;

	/**
	 * Initialize a CancellableDigestOutputStream.
	 *
	 * @param writeMonitor
	 *            monitor to update on output progress and check cancel.
	 * @param out
	 *            target stream to receive all contents.
	 */
	public CancellableDigestOutputStream(ProgressMonitor writeMonitor,
			OutputStream out) {
		this.writeMonitor = writeMonitor;
		this.out = out;
		this.checkCancelAt = BYTES_TO_WRITE_BEFORE_CANCEL_CHECK;
	}

	/**
	 * Get the monitor which is used to update on output progress and check
	 * cancel.
	 *
	 * @return the monitor
	 */
	public ProgressMonitor getWriteMonitor() {
		return writeMonitor;
	}

	/**
	 * Obtain the current SHA-1 digest.
	 *
	 * @return SHA-1 digest
	 */
	public byte[] getDigest() {
		return md.digest();
	}

	/** {@inheritDoc} */
	@Override
	public final void write(int b) throws IOException {
		if (checkCancelAt <= count) {
			if (writeMonitor.isCancelled()) {
				throw new InterruptedIOException();
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
					throw new InterruptedIOException();
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
}
