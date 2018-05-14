/*
 * Copyright (C) 2015 Obeo.
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
package org.eclipse.jgit.api.errors;

import java.text.MessageFormat;

import org.eclipse.jgit.internal.JGitText;

/**
 * Exception thrown when a hook returns a process result with a value different
 * from 0. It is up to the caller to decide whether this should block execution
 * or not.
 *
 * @since 4.0
 */
public class AbortedByHookException extends GitAPIException {
	private static final long serialVersionUID = 1L;

	/**
	 * The hook that caused this exception.
	 */
	private final String hookName;

	/**
	 * The process result.
	 */
	private final int returnCode;

	/**
	 * Constructor for AbortedByHookException
	 *
	 * @param message
	 *            The error details.
	 * @param hookName
	 *            The name of the hook that interrupted the command, must not be
	 *            null.
	 * @param returnCode
	 *            The return code of the hook process that has been run.
	 */
	public AbortedByHookException(String message, String hookName,
			int returnCode) {
		super(message);
		this.hookName = hookName;
		this.returnCode = returnCode;
	}

	/**
	 * Get hook name
	 *
	 * @return the type of the hook that interrupted the git command.
	 */
	public String getHookName() {
		return hookName;
	}

	/**
	 * Get return code
	 *
	 * @return the hook process result.
	 */
	public int getReturnCode() {
		return returnCode;
	}

	/** {@inheritDoc} */
	@Override
	public String getMessage() {
		return MessageFormat.format(JGitText.get().commandRejectedByHook,
				hookName, super.getMessage());
	}
}
