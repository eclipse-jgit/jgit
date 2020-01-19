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
 * The user-defined diff tool.
 *
 * @since 5.13
 */
public class UserDefinedDiffTool implements ExternalDiffTool {

	/**
	 * the diff tool name
	 */
	private final String name;

	/**
	 * the diff tool path
	 */
	protected String path;

	/**
	 * the diff tool command
	 */
	private final String cmd;

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
	public UserDefinedDiffTool(String name, String path, String cmd) {
		this.name = name;
		this.path = path;
		this.cmd = cmd;
	}

	/**
	 * @return the diff tool name
	 */
	@Override
	public String getName() {
		return name;
	}

	/**
	 * @return the diff tool path
	 */
	@Override
	public String getPath() {
		return path;
	}

	/**
	 * @return the diff tool command
	 */
	@Override
	public String getCommand() {
		return cmd;
	}

}
