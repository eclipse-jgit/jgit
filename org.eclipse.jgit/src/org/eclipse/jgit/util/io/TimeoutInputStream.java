/*
 * Copyright (C) 2009, 2013 Google Inc.
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
			throw readTimedOut();
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
			throw readTimedOut();
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
			throw readTimedOut();
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

	private InterruptedIOException readTimedOut() {
		return new InterruptedIOException(MessageFormat.format(
				JGitText.get().readTimedOut, Integer.valueOf(timeout)));
	}
}
