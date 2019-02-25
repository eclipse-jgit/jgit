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
 * The pre-defined diff tool.
 *
 * @since 5.7
 */
@SuppressWarnings("nls")
public class PreDefinedDiffTool extends UserDefinedDiffTool {

	/**
	 * Creates the pre-defined diff tool
	 *
	 * @param name
	 *            the name
	 * @param path
	 *            the path
	 * @param parameters
	 *            the tool parameters that are used together with path as
	 *            command
	 */
	public PreDefinedDiffTool(final String name, final String path,
			final String parameters) {
		super(name, path, parameters);
	}

	/**
	 * @param path
	 */
	public void setPath(String path) {
		// handling of spaces in path
		if (path.contains(" ")) { //$NON-NLS-1$
			// add quotes before if needed
			if (!path.startsWith("\"")) { //$NON-NLS-1$
				path = "\"" + path; //$NON-NLS-1$
			}
			// add quotes after if needed
			if (!path.endsWith("\"")) { //$NON-NLS-1$
				path = path + "\""; //$NON-NLS-1$
			}
		}
		this.path = path;
	}

	/**
	 * @return the diff tool command
	 */
	@Override
	public String getCommand() {
		return path + " " + super.getCommand();
	}

}
