/*
 * Copyright (C) 2018-2022, Andre Bossert <andre.bossert@siemens.com>
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.diffmergetool;

import org.eclipse.jgit.lib.internal.BooleanTriState;

/**
 * The user-defined merge tool.
 */
public class UserDefinedMergeTool extends UserDefinedDiffTool
		implements ExternalMergeTool {

	/**
	 * the merge tool "trust exit code" option
	 */
	private final BooleanTriState trustExitCode;

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
			BooleanTriState trustExitCode) {
		super(name, path, cmd);
		this.trustExitCode = trustExitCode;
	}

	/**
	 * @return the "trust exit code" flag
	 */
	@Override
	public boolean isTrustExitCode() {
		return trustExitCode == BooleanTriState.TRUE;
	}

	/**
	 * @return the "trust exit code" option
	 */
	public BooleanTriState getTrustExitCode() {
		return trustExitCode;
	}

}
