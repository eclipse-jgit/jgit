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
 * The merge tool interface.
 */
public interface ExternalMergeTool extends ExternalDiffTool {

	/**
	 * @return the tool "trust exit code" option
	 */
	BooleanTriState getTrustExitCode();

	/**
	 * @param withBase
	 *            get command with base present (true) or without base present
	 *            (false)
	 * @return the tool command
	 */
	String getCommand(boolean withBase);

}
