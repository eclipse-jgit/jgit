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

import java.util.Optional;

/**
 * The user-defined merge tool.
 *
 * @since 5.13
 */
public class UserDefinedMergeTool extends UserDefinedDiffTool
		implements ExternalMergeTool {

	/**
	 * the merge tool "trust exit code" option
	 */
	private final Optional<Boolean> trustExitCode;

	/**
	 * Creates the merge tool
	 *
	 * @param name
	 *            the name
	 * @param path
	 *            the path
	 * @param cmd
	 *            the command
	 * @param trustExitCode
	 *            the "trust exit code" option
	 */
	public UserDefinedMergeTool(String name, String path, String cmd,
			Optional<Boolean> trustExitCode) {
		super(name, path, cmd);
		this.trustExitCode = trustExitCode;
	}

	/**
	 * @return the "trust exit code" flag
	 */
	@Override
	public boolean isTrustExitCode() {
		return trustExitCode.get().booleanValue();
	}

	/**
	 * @return the "trust exit code" option
	 */
	public Optional<Boolean> getTrustExitCode() {
		return trustExitCode;
	}

}
