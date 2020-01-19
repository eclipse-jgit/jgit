/*
 * Copyright (C) 2018-2019, Tim Neumann <Tim.Neumann@advantest.com>
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.diffmergetool;

/**
 * A handler for when the diff/merge tool manager wants to prompt the user
 * whether to continue
 *
 * @since 5.10
 */
public interface PromptContinueHandler {
	/**
	 * Prompt the user whether to continue with the next file by opening a given
	 * tool.
	 *
	 * @param toolName
	 *            The name of the tool to open
	 * @return Whether the user wants to continue
	 */
	boolean prompt(String toolName);
}
