/*
 * Copyright (C) 2018-2020, Andre Bossert <andre.bossert@siemens.com>
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.diffmergetool;

/**
 * The merge tool interface.
 *
 * @since 5.7
 */
public interface ExternalMergeTool extends ExternalDiffTool {

	/**
	 * @return the tool "trust exit code" option
	 */
	public BooleanOption getTrustExitCode();

	/**
	 * @param withBase
	 *            get command with base present (true) or without base present
	 *            (false)
	 * @return the tool command
	 */
	abstract public String getCommand(boolean withBase);

}
