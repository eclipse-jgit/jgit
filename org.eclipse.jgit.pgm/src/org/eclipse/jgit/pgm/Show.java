/*
 * Copyright (C) 2010, Google Inc.
 * Copyright (C) 2006-2008, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
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

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.diff.RenameDetector;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.pgm.internal.CLIText;
import org.eclipse.jgit.pgm.opt.PathTreeFilterHandler;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

@Command(common = true, usage = "usage_show")
class Show extends TextBuiltin {
	private final TimeZone myTZ = TimeZone.getDefault();

	private final DateFormat fmt;

	private DiffFormatter diffFmt;

	@Argument(index = 0, metaVar = "metaVar_object")
	private String objectName;

	@Option(name = "--", metaVar = "metaVar_path", handler = PathTreeFilterHandler.class)
	protected TreeFilter pathFilter = TreeFilter.ALL;

	// BEGIN -- Options shared with Diff
	@Option(name = "-p", usage = "usage_showPatch")
	boolean showPatch;

	@Option(name = "-M", usage = "usage_detectRenames")
	private Boolean detectRenames;

	@Option(name = "--no-renames", usage = "usage_noRenames")
	void noRenames(@SuppressWarnings("unused") boolean on) {
		detectRenames = Boolean.FALSE;
	}

	@Option(name = "-l", usage = "usage_renameLimit")
	private Integer renameLimit;

	@Option(name = "--name-status", usage = "usage_nameStatus")
	private boolean showNameAndStatusOnly;

	@Option(name = "--ignore-space-at-eol")
	void ignoreSpaceAtEol(@SuppressWarnings("unused") boolean on) {
		diffFmt.setDiffComparator(RawTextComparator.WS_IGNORE_TRAILING);
	}

	@Option(name = "--ignore-leading-space")
	void ignoreLeadingSpace(@SuppressWarnings("unused") boolean on) {
		diffFmt.setDiffComparator(RawTextComparator.WS_IGNORE_LEADING);
	}

	@Option(name = "-b", aliases = { "--ignore-space-change" })
	void ignoreSpaceChange(@SuppressWarnings("unused") boolean on) {
		diffFmt.setDiffComparator(RawTextComparator.WS_IGNORE_CHANGE);
	}

	@Option(name = "-w", aliases = { "--ignore-all-space" })
	void ignoreAllSpace(@SuppressWarnings("unused") boolean on) {
		diffFmt.setDiffComparator(RawTextComparator.WS_IGNORE_ALL);
	}

	@Option(name = "-U", aliases = { "--unified" }, metaVar = "metaVar_linesOfContext")
	void unified(int lines) {
		diffFmt.setContext(lines);
	}

	@Option(name = "--abbrev", metaVar = "metaVar_n")
	void abbrev(int lines) {
		diffFmt.setAbbreviationLength(lines);
	}

	@Option(name = "--full-index")
	void abbrev(@SuppressWarnings("unused") boolean on) {
		diffFmt.setAbbreviationLength(Constants.OBJECT_ID_STRING_LENGTH);
	}

	@Option(name = "--src-prefix", usage = "usage_srcPrefix")
	void sourcePrefix(String path) {
		diffFmt.setOldPrefix(path);
	}

	@Option(name = "--dst-prefix", usage = "usage_dstPrefix")
	void dstPrefix(String path) {
		diffFmt.setNewPrefix(path);
	}

	@Option(name = "--no-prefix", usage = "usage_noPrefix")
	void noPrefix(@SuppressWarnings("unused") boolean on) {
		diffFmt.setOldPrefix(""); //$NON-NLS-1$
		diffFmt.setNewPrefix(""); //$NON-NLS-1$
	}

	// END -- Options shared with Diff

	Show() {
		fmt = new SimpleDateFormat("EEE MMM dd HH:mm:ss yyyy ZZZZZ", Locale.US); //$NON-NLS-1$
	}

	/** {@inheritDoc} */
	@Override
	protected void init(final Repository repository, final String gitDir) {
		super.init(repository, gitDir);
		diffFmt = new DiffFormatter(new BufferedOutputStream(outs));
	}

	/** {@inheritDoc} */
	@SuppressWarnings("boxing")
	@Override
	protected void run() throws Exception {
		diffFmt.setRepository(db);
		try {
			diffFmt.setPathFilter(pathFilter);
			if (detectRenames != null)
				diffFmt.setDetectRenames(detectRenames.booleanValue());
			if (renameLimit != null && diffFmt.isDetectRenames()) {
				RenameDetector rd = diffFmt.getRenameDetector();
				rd.setRenameLimit(renameLimit.intValue());
			}

			ObjectId objectId;
			if (objectName == null)
				objectId = db.resolve(Constants.HEAD);
			else
				objectId = db.resolve(objectName);

			try (RevWalk rw = new RevWalk(db)) {
				RevObject obj = rw.parseAny(objectId);
				while (obj instanceof RevTag) {
					show((RevTag) obj);
					obj = ((RevTag) obj).getObject();
					rw.parseBody(obj);
				}

				switch (obj.getType()) {
				case Constants.OBJ_COMMIT:
					show(rw, (RevCommit) obj);
					break;

				case Constants.OBJ_TREE:
					outw.print("tree "); //$NON-NLS-1$
					outw.print(objectName);
					outw.println();
					outw.println();
					show((RevTree) obj);
					break;

				case Constants.OBJ_BLOB:
					db.open(obj, obj.getType()).copyTo(System.out);
					outw.flush();
					break;

				default:
					throw die(MessageFormat.format(
							CLIText.get().cannotReadBecause, obj.name(),
							obj.getType()));
				}
			}
		} finally {
			diffFmt.close();
		}
	}

	private void show(RevTag tag) throws IOException {
		outw.print(CLIText.get().tagLabel);
		outw.print(" "); //$NON-NLS-1$
		outw.print(tag.getTagName());
		outw.println();

		final PersonIdent tagger = tag.getTaggerIdent();
		if (tagger != null) {
			outw.println(MessageFormat.format(CLIText.get().taggerInfo,
					tagger.getName(), tagger.getEmailAddress()));

			final TimeZone taggerTZ = tagger.getTimeZone();
			fmt.setTimeZone(taggerTZ != null ? taggerTZ : myTZ);
			outw.println(MessageFormat.format(CLIText.get().dateInfo,
					fmt.format(tagger.getWhen())));
		}

		outw.println();
		final String[] lines = tag.getFullMessage().split("\n"); //$NON-NLS-1$
		for (final String s : lines) {
			outw.print("    "); //$NON-NLS-1$
			outw.print(s);
			outw.println();
		}

		outw.println();
	}

	private void show(RevTree obj) throws MissingObjectException,
			IncorrectObjectTypeException, CorruptObjectException, IOException {
		try (final TreeWalk walk = new TreeWalk(db)) {
			walk.reset();
			walk.addTree(obj);

			while (walk.next()) {
				outw.print(walk.getPathString());
				final FileMode mode = walk.getFileMode(0);
				if (mode == FileMode.TREE)
					outw.print("/"); //$NON-NLS-1$
				outw.println();
			}
		}
	}

	private void show(RevWalk rw, final RevCommit c) throws Exception {
		char[] outbuffer = new char[Constants.OBJECT_ID_LENGTH * 2];

		outw.print(CLIText.get().commitLabel);
		outw.print(" "); //$NON-NLS-1$
		c.getId().copyTo(outbuffer, outw);
		outw.println();

		final PersonIdent author = c.getAuthorIdent();
		outw.println(MessageFormat.format(CLIText.get().authorInfo,
				author.getName(), author.getEmailAddress()));

		final TimeZone authorTZ = author.getTimeZone();
		fmt.setTimeZone(authorTZ != null ? authorTZ : myTZ);
		outw.println(MessageFormat.format(CLIText.get().dateInfo,
				fmt.format(author.getWhen())));

		outw.println();
		final String[] lines = c.getFullMessage().split("\n"); //$NON-NLS-1$
		for (final String s : lines) {
			outw.print("    "); //$NON-NLS-1$
			outw.print(s);
			outw.println();
		}

		outw.println();
		if (c.getParentCount() == 1) {
			rw.parseHeaders(c.getParent(0));
			showDiff(c);
		}
		outw.flush();
	}

	private void showDiff(RevCommit c) throws IOException {
		final RevTree a = c.getParent(0).getTree();
		final RevTree b = c.getTree();

		if (showNameAndStatusOnly)
			Diff.nameStatus(outw, diffFmt.scan(a, b));
		else {
			outw.flush();
			diffFmt.format(a, b);
			diffFmt.flush();
		}
		outw.println();
	}
}
