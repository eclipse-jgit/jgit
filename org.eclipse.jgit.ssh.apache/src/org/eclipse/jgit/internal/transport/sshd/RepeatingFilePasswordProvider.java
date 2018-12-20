/*
 * Copyright (C) 2018, Thomas Wolf <thomas.wolf@paranor.ch>
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
package org.eclipse.jgit.internal.transport.sshd;

import java.io.IOException;
import java.security.GeneralSecurityException;

import org.apache.sshd.common.config.keys.FilePasswordProvider;

/**
 * A {@link FilePasswordProvider} augmented to support repeatedly asking for
 * passwords.
 *
 */
public interface RepeatingFilePasswordProvider extends FilePasswordProvider {

	/**
	 * Define the maximum number of attempts to get a password that should be
	 * attempted for one identity resource through this provider.
	 *
	 * @param numberOfPasswordPrompts
	 *            number of times to ask for a password;
	 *            {@link IllegalArgumentException} may be thrown if <= 0
	 */
	void setAttempts(int numberOfPasswordPrompts);

	/**
	 * Gets the maximum number of attempts to get a password that should be
	 * attempted for one identity resource through this provider.
	 *
	 * @return the maximum number of attempts to try, always >= 1.
	 */
	default int getAttempts() {
		return 1;
	}

	// The following part of this interface is from the upstream resolution of
	// SSHD-850. See https://github.com/apache/mina-sshd/commit/f19bd2e34 .
	// TODO: remove this once we move to sshd > 2.1.0

	/**
	 * Result value of
	 * {@link RepeatingFilePasswordProvider#handleDecodeAttemptResult(String, String, Exception)}.
	 */
	public enum ResourceDecodeResult {
		/** Re-throw the decoding exception. */
		TERMINATE,
		/** Retry the decoding process - including password prompt. */
		RETRY,
		/** Skip attempt and see if we can proceed without the key. */
		IGNORE;
	}

	/**
	 * Invoked to inform the password provider about the decoding result.
	 * <b>Note:</b> any exception thrown from this method (including if called
	 * to inform about success) will be propagated instead of the original (if
	 * any was reported)
	 *
	 * @param resourceKey
	 *            The resource key representing the <U>private</U> file
	 * @param password
	 *            The password that was attempted
	 * @param err
	 *            The attempt result - {@code null} for success
	 * @return How to proceed in case of error - <u>ignored</u> if invoked in
	 *         order to report success. <b>Note:</b> {@code null} is same as
	 *         {@link ResourceDecodeResult#TERMINATE}.
	 * @throws IOException
	 * @throws GeneralSecurityException
	 */
	ResourceDecodeResult handleDecodeAttemptResult(String resourceKey,
			String password, Exception err)
			throws IOException, GeneralSecurityException;
}
