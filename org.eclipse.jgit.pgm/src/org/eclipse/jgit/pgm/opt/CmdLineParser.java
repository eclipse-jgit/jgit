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

import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ResourceBundle;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.pgm.Die;
import org.eclipse.jgit.pgm.TextBuiltin;
import org.eclipse.jgit.pgm.internal.CLIText;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.IllegalAnnotationError;
import org.kohsuke.args4j.NamedOptionDef;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.OptionHandlerRegistry;
import org.kohsuke.args4j.spi.OptionHandler;
import org.kohsuke.args4j.spi.RestOfArgumentsHandler;
import org.kohsuke.args4j.spi.Setter;

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
		OptionHandlerRegistry registry = OptionHandlerRegistry.getRegistry();
		registry.registerHandler(AbstractTreeIterator.class,
				AbstractTreeIteratorHandler.class);
		registry.registerHandler(ObjectId.class, ObjectIdHandler.class);
		registry.registerHandler(RefSpec.class, RefSpecHandler.class);
		registry.registerHandler(RevCommit.class, RevCommitHandler.class);
		registry.registerHandler(RevTree.class, RevTreeHandler.class);
		registry.registerHandler(List.class, OptionWithValuesListHandler.class);
	}

	private final Repository db;

	private RevWalk walk;

	private boolean seenHelp;

	private TextBuiltin cmd;

	/**
	 * Creates a new command line owner that parses arguments/options and set
	 * them into the given object.
	 *
	 * @param bean
	 *            instance of a class annotated by
	 *            {@link org.kohsuke.args4j.Option} and
	 *            {@link org.kohsuke.args4j.Argument}. this object will receive
	 *            values.
	 * @throws IllegalAnnotationError
	 *             if the option bean class is using args4j annotations
	 *             incorrectly.
	 */
	public CmdLineParser(Object bean) {
		this(bean, null);
	}

	/**
	 * Creates a new command line owner that parses arguments/options and set
	 * them into the given object.
	 *
	 * @param bean
	 *            instance of a class annotated by
	 *            {@link org.kohsuke.args4j.Option} and
	 *            {@link org.kohsuke.args4j.Argument}. this object will receive
	 *            values.
	 * @param repo
	 *            repository this parser can translate options through.
	 * @throws IllegalAnnotationError
	 *             if the option bean class is using args4j annotations
	 *             incorrectly.
	 */
	public CmdLineParser(Object bean, Repository repo) {
		super(bean);
		if (bean instanceof TextBuiltin) {
			cmd = (TextBuiltin) bean;
		}
		if (repo == null && cmd != null) {
			repo = cmd.getRepository();
		}
		this.db = repo;
	}

	/** {@inheritDoc} */
	@Override
	public void parseArgument(String... args) throws CmdLineException {
		final ArrayList<String> tmp = new ArrayList<>(args.length);
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

			if (containsHelp(args)) {
				// suppress exceptions on required parameters if help is present
				seenHelp = true;
				// stop argument parsing here
				break;
			}
		}
		List<OptionHandler> backup = null;
		if (seenHelp) {
			backup = unsetRequiredOptions();
		}

		try {
			super.parseArgument(tmp.toArray(new String[0]));
		} catch (Die e) {
			if (!seenHelp) {
				throw e;
			}
			printToErrorWriter(CLIText.fatalError(e.getMessage()));
		} finally {
			// reset "required" options to defaults for correct command printout
			if (backup != null && !backup.isEmpty()) {
				restoreRequiredOptions(backup);
			}
			seenHelp = false;
		}
	}

	private void printToErrorWriter(String error) {
		if (cmd == null) {
			System.err.println(error);
		} else {
			try {
				cmd.getErrorWriter().println(error);
			} catch (IOException e1) {
				System.err.println(error);
			}
		}
	}

	private List<OptionHandler> unsetRequiredOptions() {
		List<OptionHandler> options = getOptions();
		List<OptionHandler> backup = new ArrayList<>(options);
		for (Iterator<OptionHandler> iterator = options.iterator(); iterator
				.hasNext();) {
			OptionHandler handler = iterator.next();
			if (handler.option instanceof NamedOptionDef
					&& handler.option.required()) {
				iterator.remove();
			}
		}
		return backup;
	}

	private void restoreRequiredOptions(List<OptionHandler> backup) {
		List<OptionHandler> options = getOptions();
		options.clear();
		options.addAll(backup);
	}

	/**
	 * Check if array contains help option
	 *
	 * @param args
	 *            non null
	 * @return true if the given array contains help option
	 * @since 4.2
	 */
	protected boolean containsHelp(String... args) {
		return TextBuiltin.containsHelp(args);
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

	class MyOptionDef extends OptionDef {

		public MyOptionDef(OptionDef o) {
			super(o.usage(), o.metaVar(), o.required(), o.help(), o.hidden(),
					o.handler(), o.isMultiValued());
		}

		@Override
		public String toString() {
			if (metaVar() == null)
				return "ARG"; //$NON-NLS-1$
			try {
				Field field = CLIText.class.getField(metaVar());
				String ret = field.get(CLIText.get()).toString();
				return ret;
			} catch (Exception e) {
				e.printStackTrace(System.err);
				return metaVar();
			}
		}

		@Override
		public boolean required() {
			return seenHelp ? false : super.required();
		}
	}

	/** {@inheritDoc} */
	@Override
	protected OptionHandler createOptionHandler(OptionDef o, Setter setter) {
		if (o instanceof NamedOptionDef)
			return super.createOptionHandler(o, setter);
		else
			return super.createOptionHandler(new MyOptionDef(o), setter);

	}

	/** {@inheritDoc} */
	@Override
	public void printSingleLineUsage(Writer w, ResourceBundle rb) {
		List<OptionHandler> options = getOptions();
		if (options.isEmpty()) {
			super.printSingleLineUsage(w, rb);
			return;
		}
		List<OptionHandler> backup = new ArrayList<>(options);
		boolean changed = sortRestOfArgumentsHandlerToTheEnd(options);
		try {
			super.printSingleLineUsage(w, rb);
		} finally {
			if (changed) {
				options.clear();
				options.addAll(backup);
			}
		}
	}

	private boolean sortRestOfArgumentsHandlerToTheEnd(
			List<OptionHandler> options) {
		for (int i = 0; i < options.size(); i++) {
			OptionHandler handler = options.get(i);
			if (handler instanceof RestOfArgumentsHandler
					|| handler instanceof PathTreeFilterHandler) {
				options.remove(i);
				options.add(handler);
				return true;
			}
		}
		return false;
	}
}
