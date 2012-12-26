/*
 * Copyright (C) 2011, Google Inc.
 * Copyright (C) 2009, Christian Halstrick <christian.halstrick@sap.com>
 * Copyright (C) 2009, Johannes E. Schindelin
 * Copyright (C) 2009, Johannes Schindelin <johannes.schindelin@gmx.de>
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

import static java.lang.Integer.valueOf;
import static java.lang.Long.valueOf;
import static org.eclipse.jgit.lib.Constants.OBJECT_ID_STRING_LENGTH;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.eclipse.jgit.blame.BlameGenerator;
import org.eclipse.jgit.blame.BlameResult;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevFlag;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

@Command(common = false, usage = "usage_Blame")
class Blame extends TextBuiltin {
	private RawTextComparator comparator = RawTextComparator.DEFAULT;

	@Option(name = "-w", usage = "usage_ignoreWhitespace")
	void ignoreAllSpace(@SuppressWarnings("unused") boolean on) {
		comparator = RawTextComparator.WS_IGNORE_ALL;
	}

	@Option(name = "--abbrev", metaVar = "metaVar_n", usage = "usage_abbrevCommits")
	private int abbrev;

	@Option(name = "-l", usage = "usage_blameLongRevision")
	private boolean showLongRevision;

	@Option(name = "-t", usage = "usage_blameRawTimestamp")
	private boolean showRawTimestamp;

	@Option(name = "-b", usage = "usage_blameShowBlankBoundary")
	private boolean showBlankBoundary;

	@Option(name = "-s", usage = "usage_blameSuppressAuthor")
	private boolean noAuthor;

	@Option(name = "--show-email", aliases = { "-e" }, usage = "usage_blameShowEmail")
	private boolean showAuthorEmail;

	@Option(name = "--show-name", aliases = { "-f" }, usage = "usage_blameShowSourcePath")
	private boolean showSourcePath;

	@Option(name = "--show-number", aliases = { "-n" }, usage = "usage_blameShowSourceLine")
	private boolean showSourceLine;

	@Option(name = "--root", usage = "usage_blameShowRoot")
	private boolean root;

	@Option(name = "-L", metaVar = "metaVar_blameL", usage = "usage_blameRange")
	private String rangeString;

	@Option(name = "--reverse", metaVar = "metaVar_blameReverse", usage = "usage_blameReverse")
	private List<RevCommit> reverseRange = new ArrayList<RevCommit>(2);

	@Argument(index = 0, required = false, metaVar = "metaVar_revision")
	private String revision;

	@Argument(index = 1, required = false, metaVar = "metaVar_file")
	private String file;

	private ObjectReader reader;

	private final Map<RevCommit, String> abbreviatedCommits = new HashMap<RevCommit, String>();

	private SimpleDateFormat dateFmt;

	private int begin;

	private int end;

	private BlameResult blame;

	@Override
	protected void run() throws Exception {
		if (file == null) {
			if (revision == null)
				throw die(CLIText.get().fileIsRequired);
			file = revision;
			revision = null;
		}

		if (abbrev == 0)
			abbrev = db.getConfig().getInt("core", "abbrev", 7); //$NON-NLS-1$ //$NON-NLS-2$
		if (!showBlankBoundary)
			root = db.getConfig().getBoolean("blame", "blankboundary", false); //$NON-NLS-1$ //$NON-NLS-2$
		if (!root)
			root = db.getConfig().getBoolean("blame", "showroot", false); //$NON-NLS-1$ //$NON-NLS-2$

		if (showRawTimestamp)
			dateFmt = new SimpleDateFormat("ZZZZ"); //$NON-NLS-1$
		else
			dateFmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss ZZZZ"); //$NON-NLS-1$

		BlameGenerator generator = new BlameGenerator(db, file);
		reader = db.newObjectReader();
		try {
			generator.setTextComparator(comparator);

			if (!reverseRange.isEmpty()) {
				RevCommit rangeStart = null;
				List<RevCommit> rangeEnd = new ArrayList<RevCommit>(2);
				for (RevCommit c : reverseRange) {
					if (c.has(RevFlag.UNINTERESTING))
						rangeStart = c;
					else
						rangeEnd.add(c);
				}
				generator.reverse(rangeStart, rangeEnd);
			} else if (revision != null) {
				generator.push(null, db.resolve(revision + "^{commit}")); //$NON-NLS-1$
			} else {
				generator.push(null, db.resolve(Constants.HEAD));
				if (!db.isBare()) {
					DirCache dc = db.readDirCache();
					int entry = dc.findEntry(file);
					if (0 <= entry)
						generator.push(null, dc.getEntry(entry).getObjectId());

					File inTree = new File(db.getWorkTree(), file);
					if (db.getFS().isFile(inTree))
						generator.push(null, new RawText(inTree));
				}
			}

			blame = BlameResult.create(generator);
			begin = 0;
			end = blame.getResultContents().size();
			if (rangeString != null)
				parseLineRangeOption();
			blame.computeRange(begin, end);

			int authorWidth = 8;
			int dateWidth = 8;
			int pathWidth = 1;
			int maxSourceLine = 1;
			for (int line = begin; line < end; line++) {
				authorWidth = Math.max(authorWidth, author(line).length());
				dateWidth = Math.max(dateWidth, date(line).length());
				pathWidth = Math.max(pathWidth, path(line).length());
				maxSourceLine = Math.max(maxSourceLine, blame.getSourceLine(line));
			}

			String pathFmt = MessageFormat.format(" %{0}s", valueOf(pathWidth)); //$NON-NLS-1$
			String numFmt = MessageFormat.format(" %{0}d", //$NON-NLS-1$
					valueOf(1 + (int) Math.log10(maxSourceLine + 1)));
			String lineFmt = MessageFormat.format(" %{0}d) ", //$NON-NLS-1$
					valueOf(1 + (int) Math.log10(end + 1)));
			String authorFmt = MessageFormat.format(" (%-{0}s %{1}s", //$NON-NLS-1$
					valueOf(authorWidth), valueOf(dateWidth));

			for (int line = begin; line < end; line++) {
				outw.print(abbreviate(blame.getSourceCommit(line)));
				if (showSourcePath)
					outw.format(pathFmt, path(line));
				if (showSourceLine)
					outw.format(numFmt, valueOf(blame.getSourceLine(line) + 1));
				if (!noAuthor)
					outw.format(authorFmt, author(line), date(line));
				outw.format(lineFmt, valueOf(line + 1));
				outw.flush();
				blame.getResultContents().writeLine(outs, line);
				outs.flush();
				outw.print('\n');
			}
		} finally {
			generator.release();
			reader.release();
		}
	}

	private void parseLineRangeOption() {
		String beginStr, endStr;
		if (rangeString.startsWith("/")) { //$NON-NLS-1$
			int c = rangeString.indexOf("/,", 1); //$NON-NLS-1$
			if (c < 0) {
				beginStr = rangeString;
				endStr = String.valueOf(end);
			} else {
				beginStr = rangeString.substring(0, c);
				endStr = rangeString.substring(c + 2);
			}

		} else {
			int c = rangeString.indexOf(',');
			if (c < 0) {
				beginStr = rangeString;
				endStr = String.valueOf(end);
			} else if (c == 0) {
				beginStr = "0"; //$NON-NLS-1$
				endStr = rangeString.substring(1);
			} else {
				beginStr = rangeString.substring(0, c);
				endStr = rangeString.substring(c + 1);
			}
		}

		if (beginStr.equals("")) //$NON-NLS-1$
			begin = 0;
		else if (beginStr.startsWith("/")) //$NON-NLS-1$
			begin = findLine(0, beginStr);
		else
			begin = Math.max(0, Integer.parseInt(beginStr) - 1);

		if (endStr.equals("")) //$NON-NLS-1$
			end = blame.getResultContents().size();
		else if (endStr.startsWith("/")) //$NON-NLS-1$
			end = findLine(begin, endStr);
		else if (endStr.startsWith("-")) //$NON-NLS-1$
			end = begin + Integer.parseInt(endStr);
		else if (endStr.startsWith("+")) //$NON-NLS-1$
			end = begin + Integer.parseInt(endStr.substring(1));
		else
			end = Math.max(0, Integer.parseInt(endStr) - 1);
	}

	private int findLine(int b, String regex) {
		String re = regex.substring(1, regex.length() - 1);
		if (!re.startsWith("^")) //$NON-NLS-1$
			re = ".*" + re; //$NON-NLS-1$
		if (!re.endsWith("$")) //$NON-NLS-1$
			re = re + ".*"; //$NON-NLS-1$
		Pattern p = Pattern.compile(re);
		RawText text = blame.getResultContents();
		for (int line = b; line < text.size(); line++) {
			if (p.matcher(text.getString(line)).matches())
				return line;
		}
		return b;
	}

	private String path(int line) {
		String p = blame.getSourcePath(line);
		return p != null ? p : ""; //$NON-NLS-1$
	}

	private String author(int line) {
		PersonIdent author = blame.getSourceAuthor(line);
		if (author == null)
			return ""; //$NON-NLS-1$
		String name = showAuthorEmail ? author.getEmailAddress() : author
				.getName();
		return name != null ? name : ""; //$NON-NLS-1$
	}

	private String date(int line) {
		if (blame.getSourceCommit(line) == null)
			return ""; //$NON-NLS-1$

		PersonIdent author = blame.getSourceAuthor(line);
		if (author == null)
			return ""; //$NON-NLS-1$

		dateFmt.setTimeZone(author.getTimeZone());
		if (!showRawTimestamp)
			return dateFmt.format(author.getWhen());
		return String.format("%d %s", //$NON-NLS-1$
				valueOf(author.getWhen().getTime() / 1000L),
				dateFmt.format(author.getWhen()));
	}

	private String abbreviate(RevCommit commit) throws IOException {
		String r = abbreviatedCommits.get(commit);
		if (r != null)
			return r;

		if (showBlankBoundary && commit.getParentCount() == 0)
			commit = null;

		if (commit == null) {
			int len = showLongRevision ? OBJECT_ID_STRING_LENGTH : (abbrev + 1);
			StringBuilder b = new StringBuilder(len);
			for (int i = 0; i < len; i++)
				b.append(' ');
			r = b.toString();

		} else if (!root && commit.getParentCount() == 0) {
			if (showLongRevision)
				r = "^" + commit.name().substring(0, OBJECT_ID_STRING_LENGTH - 1); //$NON-NLS-1$
			else
				r = "^" + reader.abbreviate(commit, abbrev).name(); //$NON-NLS-1$
		} else {
			if (showLongRevision)
				r = commit.name();
			else
				r = reader.abbreviate(commit, abbrev + 1).name();
		}

		abbreviatedCommits.put(commit, r);
		return r;
	}
}
