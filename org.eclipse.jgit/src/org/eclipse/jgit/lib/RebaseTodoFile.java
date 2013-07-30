/*
 * Copyright (C) 2013, Christian Halstrick <christian.halstrick@sap.com>
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

package org.eclipse.jgit.lib;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.RawParseUtils;

/**
 * Offers methods to read and write files in the like the git-rebase-todo file
 */
public class RebaseTodoFile {
	private Repository repo;

	/**
	 * @param repo
	 */
	public RebaseTodoFile(Repository repo) {
		this.repo = repo;
	}

	/**
	 * Read a file formatted like the git-rebase-todo file. The "done" file is
	 * also formatted like the git-rebase-todo file. These files can be found in
	 * .git/rebase-merge/ or .git/rebase-append/ folders.
	 *
	 * @param path
	 *            path to the file relative to the repositories git-dir. E.g.
	 *            "rebase-merge/git-rebase-todo" or "rebase-append/done"
	 * @param includeComments
	 *            <code>true</code> if also comments should be reported
	 * @return the list of steps
	 * @throws IOException
	 */
	public List<RebaseTodoLine> readRebaseTodo(String path,
			boolean includeComments)
			throws IOException {
		byte[] buf = IO.readFully(new File(repo.getDirectory(), path));
		int ptr = 0;
		int tokenBegin = 0;
		List<RebaseTodoLine> r = new LinkedList<RebaseTodoLine>();
		while (ptr < buf.length) {
			RebaseTodoLine.Action action = null;
			AbbreviatedObjectId commit = null;
			tokenBegin = ptr;
			ptr = RawParseUtils.nextLF(buf, ptr);
			int lineStart = tokenBegin;
			int lineEnd = ptr - 2;
			if (lineEnd >= 0 && buf[lineEnd] == '\r')
				lineEnd--;
			int tokenCount = 0;
			// Handle comments
			if (buf[tokenBegin] == '#') {
				if (includeComments)
					r.add(new RebaseTodoLine(buf, tokenBegin, 1 + lineEnd
							- tokenBegin));
				continue;
			}
			// skip leading spaces+tabs+cr
			while (tokenBegin <= lineEnd
					&& (buf[tokenBegin] == ' ' || buf[tokenBegin] == '\t' || buf[tokenBegin] == '\r'))
				tokenBegin++;
			// Handle empty lines (maybe empty after skipping leading
			// whitespaces)
			if (tokenBegin > lineEnd) {
				if (includeComments)
					r.add(new RebaseTodoLine(buf, lineStart, 1 + lineEnd
							- lineStart));
				continue;
			}
			int nextSpace = RawParseUtils.next(buf, tokenBegin, ' ');
			while (tokenCount < 3 && nextSpace < ptr) {
				switch (tokenCount) {
				case 0:
					String actionToken = new String(buf, tokenBegin, nextSpace
							- tokenBegin - 1);
					tokenBegin = nextSpace;
					action = RebaseTodoLine.Action.parse(actionToken);
					break;
				case 1:
					if (action == null)
						break;
					nextSpace = RawParseUtils.next(buf, tokenBegin, ' ');
					String commitToken = new String(buf, tokenBegin, nextSpace
							- tokenBegin - 1);
					tokenBegin = nextSpace;
					commit = AbbreviatedObjectId.fromString(commitToken);
					break;
				case 2:
					if (action == null)
						break;
					r.add(new RebaseTodoLine(action, commit, buf, tokenBegin, 1
							+ lineEnd - tokenBegin));
					break;
				}
				tokenCount++;
			}
		}
		return r;
	}

	/**
	 * Write a file formatted like a git-rebase-todo file.
	 *
	 * @param path
	 *            path to the file relative to the repositories git-dir. E.g.
	 *            "rebase-merge/git-rebase-todo" or "rebase-append/done"
	 * @param steps
	 *            the steps to be written
	 * @param append
	 *            whether to append to an existing file or to write a new file
	 * @throws IOException
	 */
	public void writeRebaseTodoFile(String path, List<RebaseTodoLine> steps,
			boolean append) throws IOException {
		BufferedWriter fw = new BufferedWriter(new OutputStreamWriter(
				new FileOutputStream(new File(repo.getDirectory(), path),
						append), Constants.CHARACTER_ENCODING));
		try {
			StringBuilder sb = new StringBuilder();
			for (RebaseTodoLine step : steps) {
				sb.setLength(0);
				if (RebaseTodoLine.Action.COMMENT.equals(step.action))
					sb.append(RawParseUtils.decode(step.getComment()));
				else {
					sb.append(step.getAction().toToken());
					sb.append(" "); //$NON-NLS-1$
					sb.append(step.getCommit().name());
					sb.append(" "); //$NON-NLS-1$
					sb.append(RawParseUtils.decode(step.getShortMessage())
							.trim());
				}
				fw.write(sb.toString());
				fw.newLine();
			}
		} finally {
			fw.close();
		}
	}
}
