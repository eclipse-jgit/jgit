/*
 * Copyright (C) 2011, Garmin International
 * Copyright (C) 2011, Jesse Greenwald <jesse.greenwald@gmail.com>
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
import java.util.List;

import org.eclipse.jgit.api.SubtreeSplitCommand;
import org.eclipse.jgit.api.SubtreeSplitResult;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.subtree.SubtreeContext;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.Option;

@Command
class Subtree extends TextBuiltin {

	@Option(name = "--rewrite")
	private boolean rewrite;

	@Option(name = "--rewrite-all")
	private boolean rewriteAll;

	@Argument(index = 0)
	private String subtreeCmd;

	@Argument(index = 1)
	private List<String> args;

	@Override
	protected void run() throws Exception {

		if ("split".equals(subtreeCmd)) {
			SubtreeSplitCommand split = new SubtreeSplitCommand(db);
			if (args != null) {
				for (String path : args) {
					split.addSplitPath(path);
				}
			}

			ObjectId head = db.resolve("HEAD");
			if (rewriteAll) {
				split.setRewriteAll();
			} else if (rewrite) {
				split.addCommitToRewrite(head);
			}
			split.setStart(head);

			SubtreeSplitResult result = split.call();
			if (rewrite || rewriteAll) {
				out.println(result.getRewrittenCommits().get(head).name());
			}
			for (SubtreeContext c : result.getSubtreeContexts()) {
				out.println(c.getSplitCommit(head).name() + " " + c.getId());
			}
		} else {
			throw new CmdLineException(MessageFormat.format(
					CLIText.get().notAJgitCommand, subtreeCmd));
		}

	}
}
