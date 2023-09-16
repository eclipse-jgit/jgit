/*
 * Copyright (C) 2018-2021, Andre Bossert <andre.bossert@siemens.com>
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.diffmergetool;

import org.eclipse.jgit.util.FS.ExecutionResult;
import org.eclipse.jgit.util.SystemReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tool exception for differentiation.
 *
 */
public class ToolException extends Exception {

	private final static Logger LOG = LoggerFactory
			.getLogger(ToolException.class);

	private final ExecutionResult result;

	private final boolean commandExecutionError;

	/**
	 * the serial version UID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * Create tool exception
	 */
	public ToolException() {
		this(null, null, false);
	}

	/**
	 * Create tool exception
	 *
	 * @param message
	 *            the exception message
	 */
	public ToolException(String message) {
		this(message, null, false);
	}

	/**
	 * Create tool exception
	 *
	 * @param message
	 *            the exception message
	 * @param result
	 *            the execution result
	 * @param commandExecutionError
	 *            is command execution error happened ?
	 */
	public ToolException(String message, ExecutionResult result,
			boolean commandExecutionError) {
		super(message);
		this.result = result;
		this.commandExecutionError = commandExecutionError;
	}

	/**
	 * Create tool exception
	 *
	 * @param message
	 *            the exception message
	 * @param cause
	 *            the cause for throw
	 */
	public ToolException(String message, Throwable cause) {
		super(message, cause);
		result = null;
		commandExecutionError = false;
	}

	/**
	 * Create tool exception
	 *
	 * @param cause
	 *            the cause for throw
	 */
	public ToolException(Throwable cause) {
		super(cause);
		result = null;
		commandExecutionError = false;
	}

	/**
	 * Whether result is valid
	 *
	 * @return true if result is valid, false else
	 */
	public boolean isResult() {
		return result != null;
	}

	/**
	 * Get execution result
	 *
	 * @return the execution result
	 */
	public ExecutionResult getResult() {
		return result;
	}

	/**
	 * Whether execution failed with an error
	 *
	 * @return true if command execution error appears, false otherwise
	 */
	public boolean isCommandExecutionError() {
		return commandExecutionError;
	}

	/**
	 * Get buffered stderr as a String
	 *
	 * @return the result Stderr
	 */
	public String getResultStderr() {
		if (result == null) {
			return ""; //$NON-NLS-1$
		}
		try {
			return new String(result.getStderr().toByteArray(),
					SystemReader.getInstance().getDefaultCharset());
		} catch (Exception e) {
			LOG.warn("Failed to retrieve standard error output", e); //$NON-NLS-1$
		}
		return ""; //$NON-NLS-1$
	}

	/**
	 * Get buffered stdout as a String
	 *
	 * @return the result Stdout
	 */
	public String getResultStdout() {
		if (result == null) {
			return ""; //$NON-NLS-1$
		}
		try {
			return new String(result.getStdout().toByteArray(),
					SystemReader.getInstance().getDefaultCharset());
		} catch (Exception e) {
			LOG.warn("Failed to retrieve standard output", e); //$NON-NLS-1$
		}
		return ""; //$NON-NLS-1$
	}

}
