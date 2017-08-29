/*
 * Copyright (C) 2009, Google Inc.
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.eclipse.jgit.pgm.opt;

import java.io.IOException;
import java.text.MessageFormat;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.pgm.internal.CLIText;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevFlag;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.OptionHandler;
import org.kohsuke.args4j.spi.Setter;

/**
 * Custom argument handler {@link RevCommit} from string values.
 * <p>
 * Assumes the parser has been initialized with a Repository.
 */
public class RevCommitHandler extends OptionHandler<RevCommit> {
	private final org.eclipse.jgit.pgm.opt.CmdLineParser clp;

	/**
	 * Create a new handler for the command name.
	 * <p>
	 * This constructor is used only by args4j.
	 *
	 * @param parser
	 * @param option
	 * @param setter
	 */
	public RevCommitHandler(final CmdLineParser parser, final OptionDef option,
			final Setter<? super RevCommit> setter) {
		super(parser, option, setter);
		clp = (org.eclipse.jgit.pgm.opt.CmdLineParser) parser;
	}

	@Override
	public int parseArguments(final Parameters params) throws CmdLineException {
		String name = params.getParameter(0);

		boolean interesting = true;
		if (name.startsWith("^")) { //$NON-NLS-1$
			name = name.substring(1);
			interesting = false;
		}

		final int dot2 = name.indexOf(".."); //$NON-NLS-1$
		if (dot2 != -1) {
			if (!option.isMultiValued())
				throw new CmdLineException(clp,
						MessageFormat.format(
								CLIText.get().onlyOneMetaVarExpectedIn,
								option.metaVar(), name));

			final String left = name.substring(0, dot2);
			final String right = name.substring(dot2 + 2);
			addOne(left, false);
			addOne(right, true);
			return 1;
		}

		addOne(name, interesting);
		return 1;
	}

	private void addOne(final String name, final boolean interesting)
			throws CmdLineException {
		final ObjectId id;
		try {
			id = clp.getRepository().resolve(name);
		} catch (IOException e) {
			throw new CmdLineException(clp, e.getMessage());
		}
		if (id == null)
			throw new CmdLineException(clp, MessageFormat.format(CLIText.get().notACommit, name));

		final RevCommit c;
		try {
			c = clp.getRevWalk().parseCommit(id);
		} catch (MissingObjectException e) {
			throw new CmdLineException(clp, MessageFormat.format(CLIText.get().notACommit, name));
		} catch (IncorrectObjectTypeException e) {
			throw new CmdLineException(clp, MessageFormat.format(CLIText.get().notACommit, name));
		} catch (IOException e) {
			throw new CmdLineException(clp, MessageFormat.format(CLIText.get().cannotReadBecause, name, e.getMessage()));
		}

		if (interesting)
			c.remove(RevFlag.UNINTERESTING);
		else
			c.add(RevFlag.UNINTERESTING);

		setter.addValue(c);
	}

	@Override
	public String getDefaultMetaVariable() {
		return CLIText.get().metaVar_commitish;
	}
}
