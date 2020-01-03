/*
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.pgm.opt;

import org.eclipse.jgit.pgm.internal.CLIText;
import org.eclipse.jgit.transport.RefSpec;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.OptionHandler;
import org.kohsuke.args4j.spi.Parameters;
import org.kohsuke.args4j.spi.Setter;

/**
 * Custom argument handler {@link org.eclipse.jgit.transport.RefSpec} from
 * string values.
 * <p>
 * Assumes the parser has been initialized with a Repository.
 */
public class RefSpecHandler extends OptionHandler<RefSpec> {
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
	public RefSpecHandler(final CmdLineParser parser, final OptionDef option,
			final Setter<? super RefSpec> setter) {
		super(parser, option, setter);
	}

	/** {@inheritDoc} */
	@Override
	public int parseArguments(Parameters params) throws CmdLineException {
		setter.addValue(new RefSpec(params.getParameter(0)));
		return 1;
	}

	/** {@inheritDoc} */
	@Override
	public String getDefaultMetaVariable() {
		return CLIText.get().metaVar_refspec;
	}
}
