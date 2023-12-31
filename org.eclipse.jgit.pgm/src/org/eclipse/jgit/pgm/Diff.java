/*
 * Copyright (C) 2009, Christian Halstrick <christian.halstrick@sap.com>
 * Copyright (C) 2009, Johannes E. Schindelin
 * Copyright (C) 2009, Johannes Schindelin <johannes.schindelin@gmx.de> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.pgm;

import static org.eclipse.jgit.lib.Constants.HEAD;
import static org.eclipse.jgit.lib.Constants.OBJECT_ID_STRING_LENGTH;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.eclipse.jgit.diff.DiffAlgorithm;
import org.eclipse.jgit.diff.DiffAlgorithm.SupportedAlgorithm;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.diff.RenameDetector;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.pgm.internal.CLIText;
import org.eclipse.jgit.pgm.opt.PathTreeFilterHandler;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.eclipse.jgit.util.io.ThrowingPrintWriter;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

@Command(common = true, usage = "usage_ShowDiffs")
class Diff extends TextBuiltin {
	private DiffFormatter diffFmt;

	private boolean showNameOnly = false;

	private boolean showNameAndStatusOnly = false;

	@Argument(index = 0, metaVar = "metaVar_treeish")
	private AbstractTreeIterator oldTree;

	@Argument(index = 1, metaVar = "metaVar_treeish")
	private AbstractTreeIterator newTree;

	@Option(name = "--cached", aliases = { "--staged" }, usage = "usage_cached")
	private boolean cached;

	@Option(name = "--", metaVar = "metaVar_paths", handler = PathTreeFilterHandler.class)
	private TreeFilter pathFilter = TreeFilter.ALL;

	// BEGIN -- Options shared with Log
	@Option(name = "-p", usage = "usage_showPatch")
	boolean showPatch;

	@Option(name = "-M", usage = "usage_detectRenames")
	private Boolean detectRenames;

	@Option(name = "--no-renames", usage = "usage_noRenames")
	void noRenames(@SuppressWarnings("unused") boolean on) {
		detectRenames = Boolean.FALSE;
	}

	@Option(name = "--algorithm", metaVar = "metaVar_diffAlg", usage = "usage_diffAlgorithm")
	void setAlgorithm(SupportedAlgorithm s) {
		diffFmt.setDiffAlgorithm(DiffAlgorithm.getAlgorithm(s));
	}

	@Option(name = "-l", usage = "usage_renameLimit")
	private Integer renameLimit;

	@Option(name = "--name-status", usage = "usage_nameStatus")
	void nameAndStatusOnly(boolean on) {
		if (showNameOnly) {
			throw new IllegalArgumentException(
					CLIText.get().cannotUseNameStatusOnlyAndNameOnly);
		}
		showNameAndStatusOnly = on;
	}

	@Option(name = "--name-only", usage = "usage_nameOnly")
	void nameOnly(boolean on) {
		if (showNameAndStatusOnly) {
			throw new IllegalArgumentException(
					CLIText.get().cannotUseNameStatusOnlyAndNameOnly);
		}
		showNameOnly = on;
	}

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
		diffFmt.setAbbreviationLength(OBJECT_ID_STRING_LENGTH);
	}

	@Option(name = "--src-prefix", metaVar = "metaVar_prefix", usage = "usage_srcPrefix")
	void sourcePrefix(String path) {
		diffFmt.setOldPrefix(path);
	}

	@Option(name = "--dst-prefix", metaVar = "metaVar_prefix", usage = "usage_dstPrefix")
	void dstPrefix(String path) {
		diffFmt.setNewPrefix(path);
	}

	@Option(name = "--no-prefix", usage = "usage_noPrefix")
	void noPrefix(@SuppressWarnings("unused") boolean on) {
		diffFmt.setOldPrefix(""); //$NON-NLS-1$
		diffFmt.setNewPrefix(""); //$NON-NLS-1$
	}

	// END -- Options shared with Log

	@Override
	protected void init(Repository repository, String gitDir) {
		super.init(repository, gitDir);
		diffFmt = new DiffFormatter(new BufferedOutputStream(outs));
	}

	@Override
	protected void run() {
		diffFmt.setRepository(db);
		try {
			if (cached) {
				if (oldTree == null) {
					ObjectId head = db.resolve(HEAD + "^{tree}"); //$NON-NLS-1$
					if (head == null) {
						die(MessageFormat.format(CLIText.get().notATree, HEAD));
					}
					CanonicalTreeParser p = new CanonicalTreeParser();
					try (ObjectReader reader = db.newObjectReader()) {
						p.reset(reader, head);
					}
					oldTree = p;
				}
				newTree = new DirCacheIterator(db.readDirCache());
			} else if (oldTree == null) {
				oldTree = new DirCacheIterator(db.readDirCache());
				newTree = new FileTreeIterator(db);
			} else if (newTree == null) {
				newTree = new FileTreeIterator(db);
			}

			TextProgressMonitor pm = new TextProgressMonitor(errw);
			pm.setDelayStart(2, TimeUnit.SECONDS);
			diffFmt.setProgressMonitor(pm);
			diffFmt.setPathFilter(pathFilter);
			if (detectRenames != null) {
				diffFmt.setDetectRenames(detectRenames.booleanValue());
			}
			if (renameLimit != null && diffFmt.isDetectRenames()) {
				RenameDetector rd = diffFmt.getRenameDetector();
				rd.setRenameLimit(renameLimit.intValue());
			}

			if (showNameAndStatusOnly) {
				nameStatus(outw, diffFmt.scan(oldTree, newTree));
				outw.flush();
			} else if(showNameOnly) {
				nameOnly(outw, diffFmt.scan(oldTree, newTree));
				outw.flush();
			} else {
				diffFmt.format(oldTree, newTree);
				diffFmt.flush();
			}
		} catch (RevisionSyntaxException | IOException e) {
			throw die(e.getMessage(), e);
		} finally {
			diffFmt.close();
		}
	}

	static void nameStatus(ThrowingPrintWriter out, List<DiffEntry> files)
			throws IOException {
		for (DiffEntry ent : files) {
			switch (ent.getChangeType()) {
			case ADD:
				out.println("A\t" + ent.getNewPath()); //$NON-NLS-1$
				break;
			case DELETE:
				out.println("D\t" + ent.getOldPath()); //$NON-NLS-1$
				break;
			case MODIFY:
				out.println("M\t" + ent.getNewPath()); //$NON-NLS-1$
				break;
			case COPY:
				out.format("C%1$03d\t%2$s\t%3$s", //$NON-NLS-1$
						Integer.valueOf(ent.getScore()), ent.getOldPath(),
						ent.getNewPath());
				out.println();
				break;
			case RENAME:
				out.format("R%1$03d\t%2$s\t%3$s", //$NON-NLS-1$
						Integer.valueOf(ent.getScore()), ent.getOldPath(),
						ent.getNewPath());
				out.println();
				break;
			}
		}
	}

	static void nameOnly(ThrowingPrintWriter out, List<DiffEntry> files)
			throws IOException {
		for (DiffEntry ent : files) {
			switch (ent.getChangeType()) {
				case ADD:
					out.println(ent.getNewPath());
					break;
				case DELETE:
					out.println(ent.getOldPath());
					break;
				case MODIFY:
					out.println(ent.getNewPath());
					break;
				case COPY:
					out.println(ent.getNewPath());
					break;
				case RENAME:
					out.println(ent.getNewPath());
					break;
			}
		}
	}
}
