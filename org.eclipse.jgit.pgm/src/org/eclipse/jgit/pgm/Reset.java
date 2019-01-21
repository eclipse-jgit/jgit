/*
 * Copyright (C) 2011, Chris Aniszczyk <caniszczyk@gmail.com>
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

package org.eclipse.jgit.pgm;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.pgm.internal.CLIText;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.spi.RestOfArgumentsHandler;

@Command(common = true, usage = "usage_reset")
class Reset extends TextBuiltin {

	@Option(name = "--soft", usage = "usage_resetSoft")
	private boolean soft = false;

	@Option(name = "--mixed", usage = "usage_resetMixed")
	private boolean mixed = false;

	@Option(name = "--hard", usage = "usage_resetHard")
	private boolean hard = false;

	@Argument(required = false, index = 0, metaVar = "metaVar_commitish", usage = "usage_resetReference")
	private String commit;

	@Argument(required = false, index = 1, metaVar = "metaVar_paths")
	@Option(name = "--", metaVar = "metaVar_paths", handler = RestOfArgumentsHandler.class)
	private List<String> paths = new ArrayList<>();

	/** {@inheritDoc} */
	@Override
	protected void run() {
		try (Git git = new Git(db)) {
			ResetCommand command = git.reset();
			command.setRef(commit);
			if (paths.size() > 0) {
				for (String path : paths) {
					command.addPath(path);
				}
			} else {
				ResetType mode = null;
				if (soft) {
					mode = selectMode(mode, ResetType.SOFT);
				}
				if (mixed) {
					mode = selectMode(mode, ResetType.MIXED);
				}
				if (hard) {
					mode = selectMode(mode, ResetType.HARD);
				}
				if (mode == null) {
					throw die(CLIText.get().resetNoMode);
				}
				command.setMode(mode);
			}
			command.call();
		} catch (GitAPIException e) {
			throw die(e.getMessage(), e);
		}
	}

	private static ResetType selectMode(ResetType mode, ResetType want) {
		if (mode != null)
			throw die("reset modes are mutually exclusive, select one"); //$NON-NLS-1$
		return want;
	}
}
