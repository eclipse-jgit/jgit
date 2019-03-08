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

/**
 * The merge tool interface.
 *
 * @since 5.13
 */
public interface ExternalMergeTool extends ExternalDiffTool {

	/**
	 * @return the tool "trust exit code" option
	 */
	boolean isTrustExitCode();

}
