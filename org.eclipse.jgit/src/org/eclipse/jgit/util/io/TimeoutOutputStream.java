/*
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

package org.eclipse.jgit.util.io;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.text.MessageFormat;

import org.eclipse.jgit.internal.JGitText;

/** OutputStream with a configurable timeout. */
public class TimeoutOutputStream extends OutputStream {
	private final OutputStream dst;

	private final InterruptTimer myTimer;

	private int timeout;

	/**
	 * Wrap an output stream with a timeout on all write operations.
	 *
	 * @param destination
	 *            base input stream (to write to). The stream must be
	 *            interruptible (most socket streams are).
	 * @param timer
	 *            timer to manage the timeouts during writes.
	 */
	public TimeoutOutputStream(final OutputStream destination,
			final InterruptTimer timer) {
		dst = destination;
		myTimer = timer;
	}

	/** @return number of milliseconds before aborting a write. */
	public int getTimeout() {
		return timeout;
	}

	/**
	 * @param millis
	 *            number of milliseconds before aborting a write. Must be > 0.
	 */
	public void setTimeout(final int millis) {
		if (millis < 0)
			throw new IllegalArgumentException(MessageFormat.format(
					JGitText.get().invalidTimeout, Integer.valueOf(millis)));
		timeout = millis;
	}

	@Override
	public void write(int b) throws IOException {
		try {
			beginWrite();
			dst.write(b);
		} catch (InterruptedIOException e) {
			throw writeTimedOut();
		} finally {
			endWrite();
		}
	}

	@Override
	public void write(byte[] buf) throws IOException {
		write(buf, 0, buf.length);
	}

	@Override
	public void write(byte[] buf, int off, int len) throws IOException {
		try {
			beginWrite();
			dst.write(buf, off, len);
		} catch (InterruptedIOException e) {
			throw writeTimedOut();
		} finally {
			endWrite();
		}
	}

	@Override
	public void flush() throws IOException {
		try {
			beginWrite();
			dst.flush();
		} catch (InterruptedIOException e) {
			throw writeTimedOut();
		} finally {
			endWrite();
		}
	}

	@Override
	public void close() throws IOException {
		try {
			beginWrite();
			dst.close();
		} catch (InterruptedIOException e) {
			throw writeTimedOut();
		} finally {
			endWrite();
		}
	}

	private void beginWrite() {
		myTimer.begin(timeout);
	}

	private void endWrite() {
		myTimer.end();
	}

	private static InterruptedIOException writeTimedOut() {
		return new InterruptedIOException(JGitText.get().writeTimedOut);
	}
}
