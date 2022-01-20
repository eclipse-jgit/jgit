/*
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.pgm.debug;

import java.io.IOException;
import java.net.URL;

import org.eclipse.jgit.pgm.Command;
import org.eclipse.jgit.pgm.CommandCatalog;
import org.eclipse.jgit.pgm.CommandRef;
import org.eclipse.jgit.pgm.TextBuiltin;
import org.eclipse.jgit.pgm.internal.CLIText;
import org.eclipse.jgit.util.stream.ThrowingPrintWriter;
import org.kohsuke.args4j.Option;

@Command(usage = "usage_displayAListOfAllRegisteredJgitCommands")
class ShowCommands extends TextBuiltin {
	@Option(name = "--pretty", metaVar = "metaVar_commandDetail", usage = "usage_alterTheDetailShown")
	private Format pretty = Format.USAGE;

	/** {@inheritDoc} */
	@Override
	protected void run() throws Exception {
		final CommandRef[] list = CommandCatalog.all();

		int width = 0;
		for (CommandRef c : list)
			width = Math.max(width, c.getName().length());
		width += 2;

		for (CommandRef c : list) {
			errw.print(c.isCommon() ? '*' : ' ');
			errw.print(' ');

			errw.print(c.getName());
			for (int i = c.getName().length(); i < width; i++)
				errw.print(' ');

			pretty.print(errw, c);
			errw.println();
		}
		errw.println();
	}

	enum Format {
		/** */
		USAGE {
			@Override
			void print(ThrowingPrintWriter err, CommandRef c) throws IOException {
				String usage = c.getUsage();
				if (usage != null && usage.length() > 0)
					err.print(CLIText.get().resourceBundle().getString(usage));
			}
		},

		/** */
		CLASSES {
			@Override
			void print(ThrowingPrintWriter err, CommandRef c) throws IOException {
				err.print(c.getImplementationClassName());
			}
		},

		/** */
		URLS {
			@Override
			void print(ThrowingPrintWriter err, CommandRef c) throws IOException {
				final ClassLoader ldr = c.getImplementationClassLoader();

				String cn = c.getImplementationClassName();
				cn = cn.replace('.', '/') + ".class"; //$NON-NLS-1$

				final URL url = ldr.getResource(cn);
				if (url == null) {
					err.print(CLIText.get().notFound);
					return;
				}

				String rn = url.toExternalForm();
				if (rn.endsWith(cn))
					rn = rn.substring(0, rn.length() - cn.length());

				err.print(rn);
			}
		};

		abstract void print(ThrowingPrintWriter err, CommandRef c) throws IOException;
	}
}
