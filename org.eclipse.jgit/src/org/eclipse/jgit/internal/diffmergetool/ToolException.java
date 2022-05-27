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
	 *
	 */
	public ToolException() {
		this(null, null, false);
	}

	/**
	 * @param message
	 *            the exception message
	 */
	public ToolException(String message) {
		this(message, null, false);
	}

	/**
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
	 * @param cause
	 *            the cause for throw
	 */
	public ToolException(Throwable cause) {
		super(cause);
		result = null;
		commandExecutionError = false;
	}

	/**
	 * @return true if result is valid, false else
	 */
	public boolean isResult() {
		return result != null;
	}

	/**
	 * @return the execution result
	 */
	public ExecutionResult getResult() {
		return result;
	}

	/**
	 * @return true if command execution error appears, false otherwise
	 */
	public boolean isCommandExecutionError() {
		return commandExecutionError;
	}

	/**
	 * @return the result Stderr
	 */
	public String getResultStderr() {
		try {
			return new String(result.getStderr().toByteArray());
		} catch (Exception e) {
			LOG.warn(e.getMessage());
		}
		return ""; //$NON-NLS-1$
	}

	/**
	 * @return the result Stdout
	 */
	public String getResultStdout() {
		try {
			return new String(result.getStdout().toByteArray());
		} catch (Exception e) {
			LOG.warn(e.getMessage());
		}
		return ""; //$NON-NLS-1$
	}

}
