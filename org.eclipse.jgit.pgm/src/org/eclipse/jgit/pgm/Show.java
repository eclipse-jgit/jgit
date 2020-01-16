/*
 * Copyright (C) 2010, Google Inc.
 * Copyright (C) 2006-2008, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
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
import org.eclipse.jgit.errors.RevisionSyntaxException;
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
	protected void init(Repository repository, String gitDir) {
		super.init(repository, gitDir);
		diffFmt = new DiffFormatter(new BufferedOutputStream(outs));
	}

	/** {@inheritDoc} */
	@SuppressWarnings("boxing")
	@Override
	protected void run() {
		diffFmt.setRepository(db);
		try {
			diffFmt.setPathFilter(pathFilter);
			if (detectRenames != null) {
				diffFmt.setDetectRenames(detectRenames.booleanValue());
			}
			if (renameLimit != null && diffFmt.isDetectRenames()) {
				RenameDetector rd = diffFmt.getRenameDetector();
				rd.setRenameLimit(renameLimit.intValue());
			}

			ObjectId objectId;
			if (objectName == null) {
				objectId = db.resolve(Constants.HEAD);
			} else {
				objectId = db.resolve(objectName);
			}

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
		} catch (RevisionSyntaxException | IOException e) {
			throw die(e.getMessage(), e);
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
		for (String s : lines) {
			outw.print("    "); //$NON-NLS-1$
			outw.print(s);
			outw.println();
		}

		outw.println();
	}

	private void show(RevTree obj) throws MissingObjectException,
			IncorrectObjectTypeException, CorruptObjectException, IOException {
		try (TreeWalk walk = new TreeWalk(db)) {
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

	private void show(RevWalk rw, RevCommit c) throws IOException {
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
		for (String s : lines) {
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
