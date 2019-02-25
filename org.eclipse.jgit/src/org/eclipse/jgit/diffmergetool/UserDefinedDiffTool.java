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

/**
 * The user-defined diff tool.
 *
 */
public class UserDefinedDiffTool implements ITool {

	/**
	 * the diff tool name
	 */
	protected final String name;

	/**
	 * the diff tool path
	 */
	protected String path;

	/**
	 * the diff tool command
	 */
	protected String cmd;

	/**
	 * the diff tool "trust exit code" option
	 */
	protected boolean trustExitCode;

	/**
	 * Creates the diff tool
	 *
	 * @param name
	 *            the name
	 * @param path
	 *            the path
	 * @param cmd
	 *            the command
	 *
	 * @param trustExitCode
	 *            the "trust exit code" flag
	 */
	public UserDefinedDiffTool(final String name, final String path,
			final String cmd, final boolean trustExitCode) {
		this.name = name;
		this.path = path;
		this.cmd = cmd;
		this.trustExitCode = trustExitCode;
	}

	/**
	 * @return the diff tool name
	 */
	@Override
	public String getName() {
		return name;
	}

	/**
	 * @return the diff tool path
	 */
	@Override
	public String getPath() {
		return path;
	}

	/**
	 * @return the diff tool command
	 */
	@Override
	public String getCommand() {
		return cmd;
	}

	/**
	 * @return the diff tool "trust exit code" flag
	 */
	@Override
	public boolean isTrustExitCode() {
		return trustExitCode;
	}

}
