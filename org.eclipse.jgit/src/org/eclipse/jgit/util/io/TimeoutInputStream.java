/*
 * Copyright (C) 2009, 2013 Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.util.io;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.text.MessageFormat;

import org.eclipse.jgit.internal.JGitText;

/**
 * InputStream with a configurable timeout.
 */
public class TimeoutInputStream extends FilterInputStream {
	private final InterruptTimer myTimer;

	private int timeout;

	/**
	 * Wrap an input stream with a timeout on all read operations.
	 *
	 * @param src
	 *            base input stream (to read from). The stream must be
	 *            interruptible (most socket streams are).
	 * @param timer
	 *            timer to manage the timeouts during reads.
	 */
	public TimeoutInputStream(final InputStream src,
			final InterruptTimer timer) {
		super(src);
		myTimer = timer;
	}

	/**
	 * Get number of milliseconds before aborting a read.
	 *
	 * @return number of milliseconds before aborting a read.
	 */
	public int getTimeout() {
		return timeout;
	}

	/**
	 * Set number of milliseconds before aborting a read.
	 *
	 * @param millis
	 *            number of milliseconds before aborting a read. Must be &gt; 0.
	 */
	public void setTimeout(int millis) {
		if (millis < 0)
			throw new IllegalArgumentException(MessageFormat.format(
					JGitText.get().invalidTimeout, Integer.valueOf(millis)));
		timeout = millis;
	}

	/** {@inheritDoc} */
	@Override
	public int read() throws IOException {
		try {
			beginRead();
			return super.read();
		} catch (InterruptedIOException e) {
			throw readTimedOut(e);
		} finally {
			endRead();
		}
	}

	/** {@inheritDoc} */
	@Override
	public int read(byte[] buf) throws IOException {
		return read(buf, 0, buf.length);
	}

	/** {@inheritDoc} */
	@Override
	public int read(byte[] buf, int off, int cnt) throws IOException {
		try {
			beginRead();
			return super.read(buf, off, cnt);
		} catch (InterruptedIOException e) {
			throw readTimedOut(e);
		} finally {
			endRead();
		}
	}

	/** {@inheritDoc} */
	@Override
	public long skip(long cnt) throws IOException {
		try {
			beginRead();
			return super.skip(cnt);
		} catch (InterruptedIOException e) {
			throw readTimedOut(e);
		} finally {
			endRead();
		}
	}

	private void beginRead() {
		myTimer.begin(timeout);
	}

	private void endRead() {
		myTimer.end();
	}

	private InterruptedIOException readTimedOut(InterruptedIOException e) {
		InterruptedIOException interrupted = new InterruptedIOException(
				MessageFormat.format(JGitText.get().readTimedOut,
						Integer.valueOf(timeout)));
		interrupted.initCause(e);
		return interrupted;
	}
}
