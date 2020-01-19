/*
 * Copyright (C) 2018-2019, Tim Neumann <Tim.Neumann@advantest.com>
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.diffmergetool;

import java.util.List;

/**
 * A handler for when the diff/merge tool manager wants to inform the user that
 * no tool has been configured and one of the default tools will be used.
 *
 * @since 5.7
 */
public interface InformNoToolHandler {
	/**
	 * Inform the user, that no tool is configured and that one of the given
	 * tools is used.
	 *
	 * @param toolNames
	 *            The tools which are tried
	 */
	void inform(List<String> toolNames);
}
