/*
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

import java.lang.reflect.Field;
import java.util.ArrayList;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.IllegalAnnotationError;
import org.kohsuke.args4j.NamedOptionDef;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.OptionHandler;
import org.kohsuke.args4j.spi.Setter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.pgm.TextBuiltin;
import org.eclipse.jgit.pgm.internal.CLIText;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;

/**
 * Extended command line parser which handles --foo=value arguments.
 * <p>
 * The args4j package does not natively handle --foo=value and instead prefers
 * to see --foo value on the command line. Many users are used to the GNU style
 * --foo=value long option, so we convert from the GNU style format to the
 * args4j style format prior to invoking args4j for parsing.
 */
public class CmdLineParser extends org.kohsuke.args4j.CmdLineParser {
	static {
		registerHandler(AbstractTreeIterator.class,
				AbstractTreeIteratorHandler.class);
		registerHandler(ObjectId.class, ObjectIdHandler.class);
		registerHandler(RefSpec.class, RefSpecHandler.class);
		registerHandler(RevCommit.class, RevCommitHandler.class);
		registerHandler(RevTree.class, RevTreeHandler.class);
	}

	private final Repository db;

	private RevWalk walk;

	/**
	 * Creates a new command line owner that parses arguments/options and set
	 * them into the given object.
	 *
	 * @param bean
	 *            instance of a class annotated by {@link Option} and
	 *            {@link Argument}. this object will receive values.
	 *
	 * @throws IllegalAnnotationError
	 *             if the option bean class is using args4j annotations
	 *             incorrectly.
	 */
	public CmdLineParser(final Object bean) {
		this(bean, null);
	}

	/**
	 * Creates a new command line owner that parses arguments/options and set
	 * them into the given object.
	 *
	 * @param bean
	 *            instance of a class annotated by {@link Option} and
	 *            {@link Argument}. this object will receive values.
	 * @param repo
	 *            repository this parser can translate options through.
	 * @throws IllegalAnnotationError
	 *             if the option bean class is using args4j annotations
	 *             incorrectly.
	 */
	public CmdLineParser(final Object bean, Repository repo) {
		super(bean);
		if (repo == null && bean instanceof TextBuiltin)
			repo = ((TextBuiltin) bean).getRepository();
		this.db = repo;
	}

	@Override
	public void parseArgument(final String... args) throws CmdLineException {
		final ArrayList<String> tmp = new ArrayList<String>(args.length);
		for (int argi = 0; argi < args.length; argi++) {
			final String str = args[argi];
			if (str.equals("--")) { //$NON-NLS-1$
				while (argi < args.length)
					tmp.add(args[argi++]);
				break;
			}

			if (str.startsWith("--")) { //$NON-NLS-1$
				final int eq = str.indexOf('=');
				if (eq > 0) {
					tmp.add(str.substring(0, eq));
					tmp.add(str.substring(eq + 1));
					continue;
				}
			}

			tmp.add(str);
		}

		super.parseArgument(tmp.toArray(new String[tmp.size()]));
	}

	/**
	 * Get the repository this parser translates values through.
	 *
	 * @return the repository, if specified during construction.
	 */
	public Repository getRepository() {
		if (db == null)
			throw new IllegalStateException(CLIText.get().noGitRepositoryConfigured);
		return db;
	}

	/**
	 * Get the revision walker used to support option parsing.
	 *
	 * @return the revision walk used by this option parser.
	 */
	public RevWalk getRevWalk() {
		if (walk == null)
			walk = new RevWalk(getRepository());
		return walk;
	}

	/**
	 * Get the revision walker used to support option parsing.
	 * <p>
	 * This method does not initialize the RevWalk and may return null.
	 *
	 * @return the revision walk used by this option parser, or null.
	 */
	public RevWalk getRevWalkGently() {
		return walk;
	}

	static class MyOptionDef extends OptionDef {

		public MyOptionDef(OptionDef o) {
			super(o.usage(), o.metaVar(), o.required(),
			      o.hidden(), o.handler(), o.isMultiValued());
		}

		@Override
		public String toString() {
			if (metaVar() == null)
				return "ARG";
			try {
				Field field = CLIText.class.getField(metaVar());
				String ret = field.get(CLIText.get()).toString();
				return ret;
			} catch (Exception e) {
				e.printStackTrace(System.err);
				return metaVar();
			}
		}
	}

	@Override
	protected OptionHandler createOptionHandler(OptionDef o, Setter setter) {
		if (o instanceof NamedOptionDef)
			return super.createOptionHandler(o, setter);
		else
			return super.createOptionHandler(new MyOptionDef(o), setter);

	}
}
