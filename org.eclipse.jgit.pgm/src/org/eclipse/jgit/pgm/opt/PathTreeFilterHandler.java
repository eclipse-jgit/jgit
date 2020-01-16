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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.pgm.internal.CLIText;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.OptionHandler;
import org.kohsuke.args4j.spi.Parameters;
import org.kohsuke.args4j.spi.Setter;

/**
 * Create a {@link org.eclipse.jgit.treewalk.filter.TreeFilter} to patch math
 * names.
 * <p>
 * This handler consumes all arguments to the end of the command line, and is
 * meant to be used on an {@link org.kohsuke.args4j.Option} of name "--".
 */
public class PathTreeFilterHandler extends OptionHandler<TreeFilter> {
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
	public PathTreeFilterHandler(final CmdLineParser parser,
			final OptionDef option, final Setter<? super TreeFilter> setter) {
		super(parser, option, setter);
	}

	/** {@inheritDoc} */
	@Override
	public int parseArguments(Parameters params) throws CmdLineException {
		final List<PathFilter> filters = new ArrayList<>();
		for (int idx = 0;; idx++) {
			final String path;
			try {
				path = params.getParameter(idx);
			} catch (CmdLineException cle) {
				break;
			}
			filters.add(PathFilter.create(path));
		}

		if (filters.isEmpty())
			return 0;
		if (filters.size() == 1) {
			setter.addValue(filters.get(0));
			return 1;
		}
		setter.addValue(PathFilterGroup.create(filters));
		return filters.size();
	}

	/** {@inheritDoc} */
	@Override
	public String getDefaultMetaVariable() {
		return CLIText.get().metaVar_paths;
	}
}
