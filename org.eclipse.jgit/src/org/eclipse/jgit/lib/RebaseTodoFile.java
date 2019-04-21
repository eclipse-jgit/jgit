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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.jgit.lib.RebaseTodoLine.Action;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.RawParseUtils;

/**
 * Offers methods to read and write files formatted like the git-rebase-todo
 * file
 *
 * @since 3.2
 */
public class RebaseTodoFile {
	private Repository repo;

	/**
	 * Constructor for RebaseTodoFile.
	 *
	 * @param repo
	 *            a {@link org.eclipse.jgit.lib.Repository} object.
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
	 *            path to the file relative to the repository's git-dir. E.g.
	 *            "rebase-merge/git-rebase-todo" or "rebase-append/done"
	 * @param includeComments
	 *            <code>true</code> if also comments should be reported
	 * @return the list of steps
	 * @throws java.io.IOException
	 */
	public List<RebaseTodoLine> readRebaseTodo(String path,
			boolean includeComments) throws IOException {
		byte[] buf = IO.readFully(new File(repo.getDirectory(), path));
		int ptr = 0;
		int tokenBegin = 0;
		List<RebaseTodoLine> r = new LinkedList<>();
		while (ptr < buf.length) {
			tokenBegin = ptr;
			ptr = RawParseUtils.nextLF(buf, ptr);
			int lineStart = tokenBegin;
			int lineEnd = ptr - 2;
			if (lineEnd >= 0 && buf[lineEnd] == '\r')
				lineEnd--;
			// Handle comments
			if (buf[tokenBegin] == '#') {
				if (includeComments)
					parseComments(buf, tokenBegin, r, lineEnd);
			} else {
				// skip leading spaces+tabs+cr
				tokenBegin = nextParsableToken(buf, tokenBegin, lineEnd);
				// Handle empty lines (maybe empty after skipping leading
				// whitespace)
				if (tokenBegin == -1) {
					if (includeComments)
						r.add(new RebaseTodoLine(RawParseUtils.decode(buf,
								lineStart, 1 + lineEnd)));
					continue;
				}
				RebaseTodoLine line = parseLine(buf, tokenBegin, lineEnd);
				if (line == null)
					continue;
				r.add(line);
			}
		}
		return r;
	}

	private static void parseComments(byte[] buf, int tokenBegin,
			List<RebaseTodoLine> r, int lineEnd) {
		RebaseTodoLine line = null;
		String commentString = RawParseUtils.decode(buf,
				tokenBegin, lineEnd + 1);
		try {
			int skip = tokenBegin + 1; // skip '#'
			skip = nextParsableToken(buf, skip, lineEnd);
			if (skip != -1) {
				// try to parse the line as non-comment
				line = parseLine(buf, skip, lineEnd);
				// successfully parsed as non-comment line
				// mark this line as a comment explicitly
				line.setAction(Action.COMMENT);
				// use the read line as comment string
				line.setComment(commentString);
			}
		} catch (Exception e) {
			// parsing as non-comment line failed
			line = null;
		} finally {
			if (line == null)
				line = new RebaseTodoLine(commentString);
			r.add(line);
		}
	}

	/**
	 * Skip leading space, tab, CR and LF characters
	 *
	 * @param buf
	 * @param tokenBegin
	 * @param lineEnd
	 * @return the token within the range of the given {@code buf} that doesn't
	 *         need to be skipped, {@code -1} if no such token found within the
	 *         range (i.e. empty line)
	 */
	private static int nextParsableToken(byte[] buf, int tokenBegin, int lineEnd) {
		while (tokenBegin <= lineEnd
				&& (buf[tokenBegin] == ' ' || buf[tokenBegin] == '\t' || buf[tokenBegin] == '\r'))
			tokenBegin++;
		if (tokenBegin > lineEnd)
			return -1;
		return tokenBegin;
	}

	private static RebaseTodoLine parseLine(byte[] buf, int tokenBegin,
			int lineEnd) {
		RebaseTodoLine.Action action = null;
		AbbreviatedObjectId commit = null;

		int nextSpace = RawParseUtils.next(buf, tokenBegin, ' ');
		int tokenCount = 0;
		while (tokenCount < 3 && nextSpace <= lineEnd) {
			switch (tokenCount) {
			case 0:
				String actionToken = new String(buf, tokenBegin,
						nextSpace - tokenBegin - 1, UTF_8);
				tokenBegin = nextSpace;
				action = RebaseTodoLine.Action.parse(actionToken);
				if (action == null)
					return null; // parsing failed
				break;
			case 1:
				nextSpace = RawParseUtils.next(buf, tokenBegin, ' ');
				String commitToken;
				if (nextSpace > lineEnd + 1) {
					commitToken = new String(buf, tokenBegin,
							lineEnd - tokenBegin + 1, UTF_8);
				} else {
					commitToken = new String(buf, tokenBegin,
							nextSpace - tokenBegin - 1, UTF_8);
				}
				tokenBegin = nextSpace;
				commit = AbbreviatedObjectId.fromString(commitToken);
				break;
			case 2:
				return new RebaseTodoLine(action, commit,
						RawParseUtils.decode(buf, tokenBegin, 1 + lineEnd));
			}
			tokenCount++;
		}
		if (tokenCount == 2)
			return new RebaseTodoLine(action, commit, ""); //$NON-NLS-1$
		return null;
	}

	/**
	 * Write a file formatted like a git-rebase-todo file.
	 *
	 * @param path
	 *            path to the file relative to the repository's git-dir. E.g.
	 *            "rebase-merge/git-rebase-todo" or "rebase-append/done"
	 * @param steps
	 *            the steps to be written
	 * @param append
	 *            whether to append to an existing file or to write a new file
	 * @throws java.io.IOException
	 */
	public void writeRebaseTodoFile(String path, List<RebaseTodoLine> steps,
			boolean append) throws IOException {
		try (OutputStream fw = new BufferedOutputStream(new FileOutputStream(
				new File(repo.getDirectory(), path), append))) {
			StringBuilder sb = new StringBuilder();
			for (RebaseTodoLine step : steps) {
				sb.setLength(0);
				if (RebaseTodoLine.Action.COMMENT.equals(step.action))
					sb.append(step.getComment());
				else {
					sb.append(step.getAction().toToken());
					sb.append(" "); //$NON-NLS-1$
					sb.append(step.getCommit().name());
					sb.append(" "); //$NON-NLS-1$
					sb.append(step.getShortMessage().trim());
				}
				sb.append('\n');
				fw.write(Constants.encode(sb.toString()));
			}
		}
	}
}
