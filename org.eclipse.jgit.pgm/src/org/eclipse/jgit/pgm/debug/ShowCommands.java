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

package org.eclipse.jgit.pgm.debug;

import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;

import org.eclipse.jgit.util.io.ThrowingPrintWriter;
import org.kohsuke.args4j.Option;
import org.eclipse.jgit.pgm.Command;
import org.eclipse.jgit.pgm.CommandCatalog;
import org.eclipse.jgit.pgm.CommandRef;
import org.eclipse.jgit.pgm.TextBuiltin;
import org.eclipse.jgit.pgm.internal.CLIText;

@Command(usage = "usage_displayAListOfAllRegisteredJgitCommands")
class ShowCommands extends TextBuiltin {
	@Option(name = "--pretty", metaVar = "metaVar_commandDetail", usage = "usage_alterTheDetailShown")
	private Format pretty = Format.USAGE;

	@Override
	protected void run() throws Exception {
		final CommandRef[] list = CommandCatalog.all();

		int width = 0;
		for (final CommandRef c : list)
			width = Math.max(width, c.getName().length());
		width += 2;

		for (final CommandRef c : list) {
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

	static enum Format {
		/** */
		USAGE {
			void print(ThrowingPrintWriter err, final CommandRef c) throws IOException {
				String usage = c.getUsage();
				if (usage != null && usage.length() > 0)
					err.print(CLIText.get().resourceBundle().getString(usage));
			}
		},

		/** */
		CLASSES {
			void print(ThrowingPrintWriter err, final CommandRef c) throws IOException {
				err.print(c.getImplementationClassName());
			}
		},

		/** */
		URLS {
			void print(ThrowingPrintWriter err, final CommandRef c) throws IOException {
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
