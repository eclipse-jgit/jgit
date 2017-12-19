/*
 * Copyright (C) 2014 Obeo.
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
	public static enum Status {
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
