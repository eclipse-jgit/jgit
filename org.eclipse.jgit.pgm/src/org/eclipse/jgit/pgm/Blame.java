/*
 * Copyright (C) 2010, Benjamin Muskalla <bmuskalla@eclipsesource.com>
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

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.blame.BlameEntry;
import org.eclipse.jgit.blame.Range;
import org.eclipse.jgit.errors.AmbiguousObjectException;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.RawParseUtils;
import org.kohsuke.args4j.Argument;

// TODO usage
@Command(common = true, usage = "usage_DisplayTheVersionOfJgit")
class Blame extends TextBuiltin {

	@Argument(metaVar = "metaVar_path", usage = "usage_path", required = true)
	String filePath;

	@Override
	protected void run() throws Exception {
		Git git = new Git(db);
		Iterable<BlameEntry> blame = git.blame().setPath(filePath).call();
		String[] lines = getRevisionContent();
		for (BlameEntry blameEntry : blame) {
			Range range = blameEntry.originalRange;
			int start = range.getStart();
			int end = range.getStart() + range.getLength();
			for (int i = start; i < end; i++)
				printLine(lines, blameEntry, i);
		}

	}

	private void printLine(String[] lines, BlameEntry blameEntry, int i) {
		RevCommit commit = blameEntry.suspect.getCommit();

		int maxLineLength = String.valueOf(lines.length).length();
		String outputFormat = "%s [%-15s] %" + maxLineLength + "d) %s";
		String format = String.format(outputFormat,
				commit.getName().substring(0, 7), commit.getAuthorIdent()
						.getName(), Integer.valueOf(i + 1), lines[i]);
		System.out.println(format);
	}

	private String[] getRevisionContent() throws AmbiguousObjectException,
			IOException, MissingObjectException, IncorrectObjectTypeException,
			CorruptObjectException {
		ObjectId headId = db.resolve(Constants.HEAD);
		RevTree headCommit = new RevWalk(db).parseTree(headId);
		TreeWalk walk = TreeWalk.forPath(db, filePath, headCommit);
		ObjectReader objectReader = walk.getObjectReader();
		ObjectLoader objectLoader = objectReader.open(walk.getObjectId(0));
		byte[] bytes = objectLoader.getBytes();
		String decode = RawParseUtils.decode(bytes);
		String[] lines = decode.split("\n", -1);
		return lines;
	}

}
