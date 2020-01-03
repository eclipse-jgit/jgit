/*
 * Copyright (C) 2015 Zend Technologies Ltd. and others and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.pgm.opt;

import org.eclipse.jgit.pgm.internal.CLIText;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.Parameters;
import org.kohsuke.args4j.spi.Setter;
import org.kohsuke.args4j.spi.StringOptionHandler;

/**
 * Special handler for the <code>--untracked-files</code> option of the
 * <code>status</code> command.
 *
 * The following rules apply:
 * <ul>
 * <li>If no mode is given, i.e. just <code>--untracked-files</code> is passed,
 * then it is the same as <code>--untracked-files=all</code></li>
 * <li>If the <code>-u</code> alias is passed then it is the same as
 * <code>--untracked-files</code></li>
 * <li>If the <code>-uno</code> alias is passed then it is the same as
 * <code>--untracked-files=no</code></li>
 * <li>If the <code>-uall</code> alias is passed then it is the same as
 * <code>--untracked-files=all</code></li>
 * </ul>
 *
 * @since 4.0
 */
public class UntrackedFilesHandler extends StringOptionHandler {

	/**
	 * <p>Constructor for UntrackedFilesHandler.</p>
	 *
	 * @param parser
	 *            The parser to which this handler belongs to.
	 * @param option
	 *            The annotation.
	 * @param setter
	 *            Object to be used for setting value.
	 */
	public UntrackedFilesHandler(CmdLineParser parser, OptionDef option,
			Setter<? super String> setter) {
		super(parser, option, setter);
	}

	/** {@inheritDoc} */
	@Override
	public int parseArguments(Parameters params) throws CmdLineException {
		String alias = params.getParameter(-1);
		if ("-u".equals(alias)) { //$NON-NLS-1$
			setter.addValue("all"); //$NON-NLS-1$
			return 0;
		} else if ("-uno".equals(alias)) { //$NON-NLS-1$
			setter.addValue("no"); //$NON-NLS-1$
			return 0;
		} else if ("-uall".equals(alias)) { //$NON-NLS-1$
			setter.addValue("all"); //$NON-NLS-1$
			return 0;
		} else if (params.size() == 0) {
			setter.addValue("all"); //$NON-NLS-1$
			return 0;
		} else if (params.size() == 1) {
			String mode = params.getParameter(0);
			if ("no".equals(mode) || "all".equals(mode)) { //$NON-NLS-1$ //$NON-NLS-2$
				setter.addValue(mode);
			} else {
				throw new CmdLineException(owner,
						CLIText.format(CLIText.get().invalidUntrackedFilesMode),
						mode);
			}
			return 1;
		} else {
			return super.parseArguments(params);
		}
	}

}
