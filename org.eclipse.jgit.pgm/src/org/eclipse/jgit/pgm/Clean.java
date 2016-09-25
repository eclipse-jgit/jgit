/*
 * Copyright (C) 2016, Ned Twigg <ned.twigg@diffplug.com>
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

import java.text.MessageFormat;
import java.util.Set;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.pgm.internal.CLIText;
import org.kohsuke.args4j.Option;

@Command(common = true, usage = "usage_Clean")
class Clean extends TextBuiltin {
	@Option(name = "-d", usage = "usage_removeUntrackedDirectories")
	private boolean dirs = false;

	@Option(name = "--force", aliases = {
			"-f" }, usage = "usage_forceClean")
	private boolean force = false;

	@Option(name = "--dryRun", aliases = { "-n" })
	private boolean dryRun = false;

	@Override
	protected void run() throws Exception {
		try (Git git = new Git(db)) {
			boolean requireForce = git.getRepository().getConfig()
					.getBoolean("clean", "requireForce", true); //$NON-NLS-1$ //$NON-NLS-2$
			if (requireForce && !(force || dryRun)) {
				throw die(CLIText.fatalError(CLIText.get().cleanRequireForce));
			}
			// Note that CleanCommand's setForce(true) will delete
			// .git folders. In the cgit cli, this behavior
			// requires setting "-f" twice, not sure how to do
			// this with args4j, so this feature is unimplemented
			// for now.
			Set<String> removedFiles = git.clean().setCleanDirectories(dirs)
					.setDryRun(dryRun).call();
			for (String removedFile : removedFiles) {
				outw.println(MessageFormat.format(CLIText.get().removing,
						removedFile));
			}
		}
	}
}
