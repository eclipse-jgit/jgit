/*
 * Copyright (C) 2014 Obeo. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.util;

/**
 * Describes the result of running an external process.
 *
 * @since 3.7
 */
public class ProcessResult {
	/**
	 * Status of a process' execution.
	 */
	public enum Status {
		/**
		 * The script was found and launched properly. It may still have exited
		 * with a non-zero {@link #exitCode}.
		 */
		OK,

		/** The script was not found on disk and thus could not be launched. */
		NOT_PRESENT,

		/**
		 * The script was found but could not be launched since it was not
		 * supported by the current {@link FS}.
		 */
		NOT_SUPPORTED;
	}

	/** The exit code of the process. */
	private final int exitCode;

	/** Status of the process' execution. */
	private final Status status;

	/**
	 * Instantiates a process result with the given status and an exit code of
	 * <code>-1</code>.
	 *
	 * @param status
	 *            Status describing the execution of the external process.
	 */
	public ProcessResult(Status status) {
		this(-1, status);
	}

	/**
	 * <p>Constructor for ProcessResult.</p>
	 *
	 * @param exitCode
	 *            Exit code of the process.
	 * @param status
	 *            Status describing the execution of the external process.
	 */
	public ProcessResult(int exitCode, Status status) {
		this.exitCode = exitCode;
		this.status = status;
	}

	/**
	 * Get exit code of the process.
	 *
	 * @return The exit code of the process.
	 */
	public int getExitCode() {
		return exitCode;
	}

	/**
	 * Get the status of the process' execution.
	 *
	 * @return The status of the process' execution.
	 */
	public Status getStatus() {
		return status;
	}

	/**
	 * Whether the execution occurred and resulted in an error
	 *
	 * @return <code>true</code> if the execution occurred and resulted in a
	 *         return code different from 0, <code>false</code> otherwise.
	 * @since 4.0
	 */
	public boolean isExecutedWithError() {
		return getStatus() == ProcessResult.Status.OK && getExitCode() != 0;
	}
}
