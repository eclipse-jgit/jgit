/*
 * Copyright (C) 2018-2020, Andre Bossert <andre.bossert@siemens.com>
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.diffmergetool;

/**
 * The external tool interface.
 *
 * @since 5.7
 */
public interface ExternalDiffTool {

	/**
	 * @return the tool name
	 */
	String getName();

	/**
	 * @return the tool path
	 */
	String getPath();

	/**
	 * @return the tool command
	 */
	String getCommand();

}
