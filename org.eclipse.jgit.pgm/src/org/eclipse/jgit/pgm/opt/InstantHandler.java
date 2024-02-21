/*
 * Copyright (C) 2022, Harald Weiner and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.pgm.opt;

import java.time.Instant;

import org.eclipse.jgit.pgm.internal.CLIText;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.OptionHandler;
import org.kohsuke.args4j.spi.Parameters;
import org.kohsuke.args4j.spi.Setter;

/**
 * Custom argument handler {@link java.time.Instant} from string values.
 * <p>
 * Assumes the parser has been initialized with a Repository.
 *
 * @since 6.5
 */
public class InstantHandler extends OptionHandler<Instant> {
	/**
	 * Create a new handler for the command name.
	 * <p>
	 * This constructor is used only by args4j.
	 *
	 * @param parser
	 *            a {@link org.kohsuke.args4j.CmdLineParser} object.
	 * @param option
	 *            a {@link org.kohsuke.args4j.OptionDef} object.
	 * @param setter
	 *            a {@link org.kohsuke.args4j.spi.Setter} object.
	 */
	public InstantHandler(CmdLineParser parser, OptionDef option,
			Setter<? super Instant> setter) {
		super(parser, option, setter);
	}

	@Override
	public int parseArguments(Parameters params) throws CmdLineException {
		Instant instant = Instant.parse(params.getParameter(0));
		setter.addValue(instant);
		return 1;
	}

	@Override
	public String getDefaultMetaVariable() {
		return CLIText.get().metaVar_instant;
	}
}
