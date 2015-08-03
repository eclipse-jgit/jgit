/*
 * Copyright (C) 2008-2009, Google Inc.
 * Copyright (C) 2009-2010, Robin Rosenberg <robin.rosenberg@dewire.com>
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

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.RebaseCommand;
import org.eclipse.jgit.api.RebaseResult;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.merge.ResolveMerger.MergeFailureReason;
import org.eclipse.jgit.util.io.ThrowingPrintWriter;
import org.kohsuke.args4j.Argument;

@Command(common = false, usage = "usage_Rebase")
class Rebase extends TextBuiltin {
	@Argument(required = true, metaVar = "metaVar_ref", usage = "usage_upstreamRef")
	private String upstream;

	@Override
	protected void run() throws Exception {
		try (Git git = new Git(db)) {
			RebaseCommand rebase = git.rebase();
			rebase.setProgressMonitor(new TextProgressMonitor(outw));
			rebase.setUpstream(upstream);
			RebaseResult result = rebase.call();
			if (result.getStatus().isSuccessful()) {
				success(outw);
			} else {
				error(errw, result.getStatus(), result.getConflicts(),
						result.getFailingPaths());
			}
		}
	}

	@SuppressWarnings("nls")
	static void success(ThrowingPrintWriter writer) throws IOException {
		writer.println("Success!");
	}

	@SuppressWarnings("nls")
	static void error(ThrowingPrintWriter writer, Object reason,
			List<String> conflicts, Map<String, MergeFailureReason> failingPaths)
			throws IOException {
		writer.println("Error! " + reason);
		if (conflicts != null && !conflicts.isEmpty()) {
			writer.println("Conflicts:");
			for (String conflict : conflicts) {
				writer.println("\t" + conflict);
			}
		}
		if (!failingPaths.isEmpty()) {
			writer.println("Failures:");
			for (Map.Entry<String, MergeFailureReason> conflict : failingPaths
					.entrySet()) {
				writer.println("\t" + conflict.getKey() + ": "
						+ conflict.getValue());
			}
		}
	}
}
