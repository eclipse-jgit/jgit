/*
 * Copyright (C) 2015 Zend Technologies Ltd. and others
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
