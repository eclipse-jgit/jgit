/*
 * Copyright (C) 2018, Salesforce. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.pgm.opt;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.Parameters;
import org.kohsuke.args4j.spi.Setter;
import org.kohsuke.args4j.spi.StringOptionHandler;

/**
 * Special handler for the <code>--gpg-sign</code> option of the
 * <code>commit</code> command.
 *
 * The following rules apply:
 * <ul>
 * <li>If no key is given, i.e. just <code>--gpg-sign</code> is passed, then it
 * is the same as <code>--gpg-sign=default</code></li>
 * </ul>
 *
 * @since 5.3
 */
public class GpgSignHandler extends StringOptionHandler {

	/**
	 * The value "default" which will be used when just the option is specified
	 * without any argument
	 */
	public static final String DEFAULT = "default"; //$NON-NLS-1$

	/**
	 * <p>
	 * Constructor for GpgSignHandler.
	 * </p>
	 *
	 * @param parser
	 *            The parser to which this handler belongs.
	 * @param option
	 *            The annotation.
	 * @param setter
	 *            Object to be used for setting value.
	 */
	public GpgSignHandler(CmdLineParser parser, OptionDef option,
			Setter<? super String> setter) {
		super(parser, option, setter);
	}

	/** {@inheritDoc} */
	@Override
	public int parseArguments(Parameters params) throws CmdLineException {
		String alias = params.getParameter(-1);
		if ("--gpg-sign".equals(alias) || "-S".equals(alias)) { //$NON-NLS-1$ //$NON-NLS-2$
			try {
				String key = params.getParameter(0);
				if (key == null || key.startsWith("-")) { //$NON-NLS-1$
					// ignore invalid values and assume default
					setter.addValue(DEFAULT);
					return 0;
				}

				// use what we have
				setter.addValue(key);
				return 1;
			} catch (CmdLineException e) {
				// no additional value, assume default
				setter.addValue(DEFAULT);
				return 0;
			}
		}
		return 0;
	}

}
