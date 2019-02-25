/*
 * Copyright (C) 2018-2019, Andre Bossert <andre.bossert@siemens.com>
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

package org.eclipse.jgit.diffmergetool;

import org.eclipse.jgit.util.FS.ExecutionResult;

/**
 * Tool exception for differentiation.
 *
 * @since 5.4
 *
 */
public class ToolException extends Exception {

	private final ExecutionResult result;

	/**
	 * the serial version UID
	 */
	private static final long serialVersionUID = 6618861799028752563L;

	/**
	 *
	 */
	public ToolException() {
		super();
		result = null;
	}

	/**
	 * @param message
	 *            the exception message
	 */
	public ToolException(String message) {
		super(message);
		result = null;
	}

	/**
	 * @param message
	 *            the exception message
	 * @param result
	 *            the execution result
	 */
	public ToolException(String message, ExecutionResult result) {
		super(message);
		this.result = result;
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
	}

	/**
	 * @param cause
	 *            the cause for throw
	 */
	public ToolException(Throwable cause) {
		super(cause);
		result = null;
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
	 * @return the result Stderr
	 */
	public String getResultStderr() {
		try {
			return new String(result.getStderr().toByteArray());
		} catch (Exception e) {
			// nop
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
			// nop
		}
		return ""; //$NON-NLS-1$
	}

}
