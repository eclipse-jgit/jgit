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
 * The pre-defined merge tool.
 *
 * @since 5.7
 */
public class PreDefinedMergeTool extends UserDefinedMergeTool {

	/**
	 * the tool parameters without base
	 */
	private final String parametersWithoutBase;

	/**
	 * Creates the pre-defined merge tool
	 *
	 * @param name
	 *            the name
	 * @param path
	 *            the path
	 * @param parametersWithBase
	 *            the tool parameters that are used together with path as
	 *            command and "base is present" ($BASE)
	 * @param parametersWithoutBase
	 *            the tool parameters that are used together with path as
	 *            command and "base is present" ($BASE)
	 * @param trustExitCode
	 *            the "trust exit code" option
	 */
	public PreDefinedMergeTool(String name, String path,
			String parametersWithBase, String parametersWithoutBase,
			BooleanOption trustExitCode) {
		super(name, path, parametersWithBase, trustExitCode);
		this.parametersWithoutBase = parametersWithoutBase;
	}

	/**
	 * Creates the pre-defined merge tool
	 *
	 * @param tool
	 *            the command line merge tool
	 *
	 */
	public PreDefinedMergeTool(CommandLineMergeTool tool) {
		this(tool.name(), tool.getPath(), tool.getParameters(true),
				tool.getParameters(false),
				BooleanOption.toConfigured(tool.isExitCodeTrustable()));
	}

	/**
	 * @param path
	 */
	public void setPath(String path) {
		this.path = path;
	}

	/**
	 * @param trustExitCode
	 *            the "trust exit code" option
	 */
	public void setTrustExitCode(BooleanOption trustExitCode) {
		this.trustExitCode = trustExitCode;
	}

	/**
	 * @return the tool command (with base present)
	 */
	@Override
	public String getCommand() {
		return getCommand(true);
	}

	/**
	 * @param withBase
	 *            get command with base present (true) or without base present
	 *            (false)
	 * @return the tool command
	 */
	@Override
	public String getCommand(boolean withBase) {
		return path + " " //$NON-NLS-1$
				+ (withBase ? super.getCommand() : parametersWithoutBase);
	}

}
