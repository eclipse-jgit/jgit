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
 */
public class UserDefinedDiffTool implements ExternalDiffTool {

	private boolean available;

	/**
	 * the diff tool name
	 */
	private final String name;

	/**
	 * the diff tool path
	 */
	private String path;

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
	 * The path of the diff tool.
	 *
	 * <p>
	 * The path to a pre-defined external diff tool can be overridden by
	 * specifying {@code difftool.<tool>.path} in a configuration file.
	 * </p>
	 * <p>
	 * For a user defined diff tool (that does not override a pre-defined diff
	 * tool), the path is ignored when invoking the tool.
	 * </p>
	 *
	 * @return the diff tool path
	 *
	 * @see <a href=
	 *      "https://git-scm.com/docs/git-difftool">https://git-scm.com/docs/git-difftool</a>
	 */
	@Override
	public String getPath() {
		return path;
	}

	/**
	 * The command of the diff tool.
	 *
	 * <p>
	 * A pre-defined external diff tool can be overridden using the tools name
	 * in a configuration file. The overwritten tool is then a user defined tool
	 * and the command of the diff tool is specified with
	 * {@code difftool.<tool>.cmd}. This command must work without prepending
	 * the value of {@link #getPath()} and can sometimes include tool
	 * parameters.
	 * </p>
	 *
	 * @return the diff tool command
	 *
	 * @see <a href=
	 *      "https://git-scm.com/docs/git-difftool">https://git-scm.com/docs/git-difftool</a>
	 */
	@Override
	public String getCommand() {
		return cmd;
	}

	/**
	 * @return availability of the tool: true if tool can be executed and false
	 *         if not
	 */
	@Override
	public boolean isAvailable() {
		return available;
	}

	/**
	 * @param available
	 *            true if tool can be found and false if not
	 */
	public void setAvailable(boolean available) {
		this.available = available;
	}

	/**
	 * Overrides the path for the given tool. Equivalent to setting
	 * {@code difftool.<tool>.path}.
	 *
	 * @param path
	 *            the new diff tool path
	 *
	 * @see <a href=
	 *      "https://git-scm.com/docs/git-difftool">https://git-scm.com/docs/git-difftool</a>
	 */
	public void setPath(String path) {
		this.path = path;
	}
}
