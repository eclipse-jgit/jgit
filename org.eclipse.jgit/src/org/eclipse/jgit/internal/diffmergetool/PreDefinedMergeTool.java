/*
 * Copyright (C) 2018-2022, Andre Bossert <andre.bossert@siemens.com>
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.diffmergetool;

import org.eclipse.jgit.lib.internal.BooleanTriState;

/**
 * The pre-defined merge tool.
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
			BooleanTriState trustExitCode) {
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
				tool.isExitCodeTrustable() ? BooleanTriState.TRUE
						: BooleanTriState.FALSE);
	}

	/**
	 * @param trustExitCode
	 *            the "trust exit code" option
	 */
	@Override
	public void setTrustExitCode(BooleanTriState trustExitCode) {
		super.setTrustExitCode(trustExitCode);
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
		return ExternalToolUtils.quotePath(getPath()) + " " //$NON-NLS-1$
				+ (withBase ? super.getCommand() : parametersWithoutBase);
	}

}
