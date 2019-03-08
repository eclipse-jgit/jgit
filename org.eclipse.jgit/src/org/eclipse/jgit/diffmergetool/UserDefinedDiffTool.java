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
 * The user-defined diff tool.
 *
 * @since 5.7
 */
public class UserDefinedDiffTool implements ExternalDiffTool {

	/**
	 * the tool name
	 */
	private final String name;

	/**
	 * the tool path
	 */
	protected String path;

	/**
	 * the tool command
	 */
	private String cmd;

	/**
	 * Creates the diff tool
	 *
	 * @param name
	 *            the name
	 * @param path
	 *            the path
	 * @param cmd
	 *            the command
	 */
	public UserDefinedDiffTool(final String name, final String path,
			final String cmd) {
		this.name = name;
		this.path = path;
		this.cmd = cmd;
	}

	/**
	 * @return the tool name
	 */
	@Override
	public String getName() {
		return name;
	}

	/**
	 * @return the tool path
	 */
	@Override
	public String getPath() {
		return path;
	}

	/**
	 * @return the tool command
	 */
	@Override
	public String getCommand() {
		return cmd;
	}

}
