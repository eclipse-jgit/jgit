/*
 * Copyright (C) 2011-2012, Google Inc.
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

package org.eclipse.jgit.transport;

import java.io.IOException;

import org.eclipse.jgit.internal.JGitText;

/**
 * Indicates a transport service may not continue execution.
 *
 * @since 2.0
 */
public class ServiceMayNotContinueException extends IOException {
	private static final long serialVersionUID = 1L;

	private boolean output;

	/** Initialize with no message. */
	public ServiceMayNotContinueException() {
		// Do not set a message.
	}

	/**
	 * @param msg
	 *            a message explaining why it cannot continue. This message may
	 *            be shown to an end-user.
	 */
	public ServiceMayNotContinueException(String msg) {
		super(msg);
	}

	/**
	 * @param msg
	 *            a message explaining why it cannot continue. This message may
	 *            be shown to an end-user.
	 * @param cause
	 *            the cause of the exception.
	 */
	public ServiceMayNotContinueException(String msg, Throwable cause) {
		super(msg);
		initCause(cause);
	}

	/**
	 * Initialize with an "internal server error" message and a cause.
	 *
	 * @param cause
	 *            the cause of the exception.
	 */
	public ServiceMayNotContinueException(Throwable cause) {
		this(JGitText.get().internalServerError, cause);
	}

	/** @return true if the message was already output to the client. */
	public boolean isOutput() {
		return output;
	}

	/** Mark this message has being sent to the client. */
	public void setOutput() {
		output = true;
	}
}
