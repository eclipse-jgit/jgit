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
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.diff.RenameDetector;
import org.eclipse.jgit.errors.LargeObjectException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.util.GitDateFormatter;
import org.eclipse.jgit.util.GitDateFormatter.Format;
import org.kohsuke.args4j.Option;

@Command(common = true, usage = "usage_viewCommitHistory")
class Log extends RevWalkTextBuiltin {

	private GitDateFormatter dateFormatter = new GitDateFormatter(
			Format.DEFAULT);

	private DiffFormatter diffFmt;

	private Map<AnyObjectId, Set<Ref>> allRefsByPeeledObjectId;

	private Map<String, NoteMap> noteMaps;

	@Option(name="--decorate", usage="usage_showRefNamesMatchingCommits")
	private boolean decorate;

	@Option(name = "--no-standard-notes", usage = "usage_noShowStandardNotes")
	private boolean noStandardNotes;

	private List<String> additionalNoteRefs = new ArrayList<String>();

	@Option(name = "--show-notes", usage = "usage_showNotes", metaVar = "metaVar_ref")
	void addAdditionalNoteRef(String notesRef) {
		additionalNoteRefs.add(notesRef);
	}

	@Option(name = "--date", usage = "usage_date")
	void dateFormat(String date) {
		if (date.toLowerCase().equals(date))
			date = date.toUpperCase();
		dateFormatter = new GitDateFormatter(Format.valueOf(date));
	}

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


	Log() {
		dateFormatter = new GitDateFormatter(Format.DEFAULT);
	}

	@Override
	protected void init(final Repository repository, final String gitDir) {
		super.init(repository, gitDir);
		diffFmt = new DiffFormatter(new BufferedOutputStream(outs));
	}

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

			if (!noStandardNotes || !additionalNoteRefs.isEmpty()) {
				createWalk();
				noteMaps = new LinkedHashMap<String, NoteMap>();
				if (!noStandardNotes) {
					addNoteMap(Constants.R_NOTES_COMMITS);
				}
				if (!additionalNoteRefs.isEmpty()) {
					for (String notesRef : additionalNoteRefs) {
						if (!notesRef.startsWith(Constants.R_NOTES)) {
							notesRef = Constants.R_NOTES + notesRef;
						}
						addNoteMap(notesRef);
					}
				}
			}

			if (decorate)
				allRefsByPeeledObjectId = getRepository()
						.getAllRefsByPeeledObjectId();

			super.run();
		} finally {
			diffFmt.release();
		}
	}

	private void addNoteMap(String notesRef) throws IOException {
		Ref notes = db.getRef(notesRef);
		if (notes == null)
			return;
		RevCommit notesCommit = argWalk.parseCommit(notes.getObjectId());
		noteMaps.put(notesRef,
				NoteMap.read(argWalk.getObjectReader(), notesCommit));
	}

	@Override
	protected void show(final RevCommit c) throws Exception {
		outw.print(CLIText.get().commitLabel);
		outw.print(" "); //$NON-NLS-1$
		c.getId().copyTo(outbuffer, outw);
		if (decorate) {
			Collection<Ref> list = allRefsByPeeledObjectId.get(c);
			if (list != null) {
				outw.print(" ("); //$NON-NLS-1$
				for (Iterator<Ref> i = list.iterator(); i.hasNext(); ) {
					outw.print(i.next().getName());
					if (i.hasNext())
						outw.print(" "); //$NON-NLS-1$
				}
				outw.print(")"); //$NON-NLS-1$
			}
		}
		outw.println();

		final PersonIdent author = c.getAuthorIdent();
		outw.println(MessageFormat.format(CLIText.get().authorInfo, author.getName(), author.getEmailAddress()));
		outw.println(MessageFormat.format(CLIText.get().dateInfo,
				dateFormatter.formatDate(author)));

		outw.println();
		final String[] lines = c.getFullMessage().split("\n"); //$NON-NLS-1$
		for (final String s : lines) {
			outw.print("    "); //$NON-NLS-1$
			outw.print(s);
			outw.println();
		}

		outw.println();
		if (showNotes(c))
			outw.println();

		if (c.getParentCount() == 1 && (showNameAndStatusOnly || showPatch))
			showDiff(c);
		outw.flush();
	}

	/**
	 * @param c
	 * @return <code>true</code> if at least one note was printed,
	 *         <code>false</code> otherwise
	 * @throws IOException
	 */
	private boolean showNotes(RevCommit c) throws IOException {
		if (noteMaps == null)
			return false;

		boolean printEmptyLine = false;
		boolean atLeastOnePrinted = false;
		for (Map.Entry<String, NoteMap> e : noteMaps.entrySet()) {
			String label = null;
			String notesRef = e.getKey();
			if (! notesRef.equals(Constants.R_NOTES_COMMITS)) {
				if (notesRef.startsWith(Constants.R_NOTES))
					label = notesRef.substring(Constants.R_NOTES.length());
				else
					label = notesRef;
			}
			boolean printedNote = showNotes(c, e.getValue(), label,
					printEmptyLine);
			atLeastOnePrinted |= printedNote;
			printEmptyLine = printedNote;
		}
		return atLeastOnePrinted;
	}

	/**
	 * @param c
	 * @param map
	 * @param label
	 * @param emptyLine
	 * @return <code>true</code> if note was printed, <code>false</code>
	 *         otherwise
	 * @throws IOException
	 */
	private boolean showNotes(RevCommit c, NoteMap map, String label,
			boolean emptyLine)
			throws IOException {
		ObjectId blobId = map.get(c);
		if (blobId == null)
			return false;
		if (emptyLine)
			outw.println();
		outw.print("Notes");
		if (label != null) {
			outw.print(" ("); //$NON-NLS-1$
			outw.print(label);
			outw.print(")"); //$NON-NLS-1$
		}
		outw.println(":"); //$NON-NLS-1$
		try {
			RawText rawText = new RawText(argWalk.getObjectReader()
					.open(blobId).getCachedBytes(Integer.MAX_VALUE));
			for (int i = 0; i < rawText.size(); i++) {
				outw.print("    "); //$NON-NLS-1$
				outw.println(rawText.getString(i));
			}
		} catch (LargeObjectException e) {
			outw.println(MessageFormat.format(
					CLIText.get().noteObjectTooLargeToPrint, blobId.name()));
		}
		return true;
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
