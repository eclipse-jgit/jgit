/*
 * Copyright (C) 2010, 2012 Christian Halstrick <christian.halstrick@sap.com> and
 * other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v1.0 which accompanies this
 * distribution, is reproduced below, and is available at
 * http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * - Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the names of its
 * contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package org.eclipse.jgit.pgm;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.ConcurrentRefUpdateException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.api.errors.NoMessageException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.pgm.internal.CLIText;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.util.RawParseUtils;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

@Command(common = true, usage = "usage_recordChangesToRepository")
class Commit extends TextBuiltin {
	// I don't support setting the committer, because also the native git
	// command doesn't allow this.

	@Option(name = "--author", metaVar = "metaVar_author", usage = "usage_CommitAuthor")
	private String author;

	@Option(name = "--message", aliases = { "-m" }, metaVar = "metaVar_message", usage = "usage_CommitMessage", required = true)
	private String message;

	@Option(name = "--only", aliases = { "-o" }, usage = "usage_CommitOnly")
	private boolean only;

	@Option(name = "--all", aliases = { "-a" }, usage = "usage_CommitAll")
	private boolean all;

	@Option(name = "--amend", usage = "usage_CommitAmend")
	private boolean amend;

	@Argument(metaVar = "metaVar_commitPaths", usage = "usage_CommitPaths")
	private List<String> paths = new ArrayList<>();

	@Override
	protected void run() throws NoHeadException, NoMessageException,
			ConcurrentRefUpdateException, JGitInternalException, Exception {
		try (Git git = new Git(db)) {
			CommitCommand commitCmd = git.commit();
			if (author != null)
				commitCmd.setAuthor(RawParseUtils.parsePersonIdent(author));
			if (message != null)
				commitCmd.setMessage(message);
			if (only && paths.isEmpty())
				throw die(CLIText.get().pathsRequired);
			if (only && all)
				throw die(CLIText.get().onlyOneOfIncludeOnlyAllInteractiveCanBeUsed);
			if (!paths.isEmpty())
				for (String p : paths)
					commitCmd.setOnly(p);
			commitCmd.setAmend(amend);
			commitCmd.setAll(all);
			Ref head = db.exactRef(Constants.HEAD);
			if (head == null) {
				throw die(CLIText.get().onBranchToBeBorn);
			}
			RevCommit commit;
			try {
				commit = commitCmd.call();
			} catch (JGitInternalException e) {
				throw die(e.getMessage());
			}

			String branchName;
			if (!head.isSymbolic())
				branchName = CLIText.get().branchDetachedHEAD;
			else {
				branchName = head.getTarget().getName();
				if (branchName.startsWith(Constants.R_HEADS))
					branchName = branchName.substring(Constants.R_HEADS.length());
			}
			outw.println("[" + branchName + " " + commit.name() + "] " //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					+ commit.getShortMessage());
		}
	}
}
