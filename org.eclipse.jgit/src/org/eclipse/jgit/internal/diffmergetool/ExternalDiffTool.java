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
 * The external tool interface.
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

	/**
	 * @return availability of the tool: true if tool can be executed and false
	 *         if not
	 */
	boolean isAvailable();

}
