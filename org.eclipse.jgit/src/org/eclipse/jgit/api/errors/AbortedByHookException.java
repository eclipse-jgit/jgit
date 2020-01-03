/*
 * Copyright (C) 2015 Obeo. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
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
	 * The stderr output of the hook.
	 */
	private final String hookStdErr;

	/**
	 * Constructor for AbortedByHookException
	 *
	 * @param hookStdErr
	 *            The error details from the stderr output of the hook
	 * @param hookName
	 *            The name of the hook that interrupted the command, must not be
	 *            null.
	 * @param returnCode
	 *            The return code of the hook process that has been run.
	 */
	public AbortedByHookException(String hookStdErr, String hookName,
			int returnCode) {
		super(MessageFormat.format(JGitText.get().commandRejectedByHook,
				hookName, hookStdErr));
		this.hookStdErr = hookStdErr;
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

	/**
	 * Get the stderr output of the hook.
	 *
	 * @return A string containing the complete stderr output of the hook.
	 * @since 5.6
	 */
	public String getHookStdErr() {
		return hookStdErr;
	}
}
