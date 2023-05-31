/*
 * Copyright (C) 2010, Google Inc.
 * Copyright (C) 2006, 2008, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, 2021, Shawn O. Pearce <spearce@spearce.org> and others
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
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.diff.RenameDetector;
import org.eclipse.jgit.errors.LargeObjectException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.GpgConfig;
import org.eclipse.jgit.lib.GpgSignatureVerifier;
import org.eclipse.jgit.lib.GpgSignatureVerifier.SignatureVerification;
import org.eclipse.jgit.lib.GpgSignatureVerifierFactory;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.pgm.internal.CLIText;
import org.eclipse.jgit.pgm.internal.VerificationUtils;
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

	private boolean showNameOnly = false;

	private boolean showNameAndStatusOnly = false;

	@Option(name="--decorate", usage="usage_showRefNamesMatchingCommits")
	private boolean decorate;

	@Option(name = "--no-standard-notes", usage = "usage_noShowStandardNotes")
	private boolean noStandardNotes;

	private List<String> additionalNoteRefs = new ArrayList<>();

	@Option(name = "--show-notes", usage = "usage_showNotes", metaVar = "metaVar_ref")
	void addAdditionalNoteRef(String notesRef) {
		additionalNoteRefs.add(notesRef);
	}

	@Option(name = "--show-signature", usage = "usage_showSignature")
	private boolean showSignature;

	@Option(name = "--date", usage = "usage_date")
	void dateFormat(String date) {
		if (date.toLowerCase(Locale.ROOT).equals(date))
			date = date.toUpperCase(Locale.ROOT);
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


	private GpgSignatureVerifier verifier;

	private GpgConfig config;

	Log() {
		dateFormatter = new GitDateFormatter(Format.DEFAULT);
	}

	@Override
	protected void init(Repository repository, String gitDir) {
		super.init(repository, gitDir);
		diffFmt = new DiffFormatter(new BufferedOutputStream(outs));
	}

	@Override
	protected void run() {
		config = new GpgConfig(db.getConfig());
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

			if (!noStandardNotes || !additionalNoteRefs.isEmpty()) {
				createWalk();
				noteMaps = new LinkedHashMap<>();
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

			if (decorate) {
				allRefsByPeeledObjectId = getRepository()
						.getAllRefsByPeeledObjectId();
			}
			super.run();
		} catch (Exception e) {
			throw die(e.getMessage(), e);
		} finally {
			diffFmt.close();
			if (verifier != null) {
				verifier.clear();
			}
		}
	}

	private void addNoteMap(String notesRef) throws IOException {
		Ref notes = db.exactRef(notesRef);
		if (notes == null)
			return;
		RevCommit notesCommit = argWalk.parseCommit(notes.getObjectId());
		noteMaps.put(notesRef,
				NoteMap.read(argWalk.getObjectReader(), notesCommit));
	}

	@Override
	protected void show(RevCommit c) throws Exception {
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

		if (showSignature) {
			showSignature(c);
		}
		final PersonIdent author = c.getAuthorIdent();
		outw.println(MessageFormat.format(CLIText.get().authorInfo, author.getName(), author.getEmailAddress()));
		outw.println(MessageFormat.format(CLIText.get().dateInfo,
				dateFormatter.formatDate(author)));

		outw.println();
		final String[] lines = c.getFullMessage().split("\n"); //$NON-NLS-1$
		for (String s : lines) {
			outw.print("    "); //$NON-NLS-1$
			outw.print(s);
			outw.println();
		}
		c.disposeBody();

		outw.println();
		if (showNotes(c))
			outw.println();

		if (c.getParentCount() <= 1 && (showNameAndStatusOnly || showPatch
				|| showNameOnly)) {
			showDiff(c);
		}
		outw.flush();
	}

	private void showSignature(RevCommit c) throws IOException {
		if (c.getRawGpgSignature() == null) {
			return;
		}
		if (verifier == null) {
			GpgSignatureVerifierFactory factory = GpgSignatureVerifierFactory
					.getDefault();
			if (factory == null) {
				throw die(CLIText.get().logNoSignatureVerifier, null);
			}
			verifier = factory.getVerifier();
		}
		SignatureVerification verification = verifier.verifySignature(c,
				config);
		if (verification == null) {
			return;
		}
		VerificationUtils.writeVerification(outw, verification,
				verifier.getName(), c.getCommitterIdent());
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
		outw.print("Notes"); //$NON-NLS-1$
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
		final RevTree a = c.getParentCount() > 0 ? c.getParent(0).getTree()
				: null;
		final RevTree b = c.getTree();

		if (showNameAndStatusOnly) {
			Diff.nameStatus(outw, diffFmt.scan(a, b));
		} else if (showNameOnly) {
			Diff.nameOnly(outw, diffFmt.scan(a, b));
		} else {
			outw.flush();
			diffFmt.format(a, b);
			diffFmt.flush();
		}
		outw.println();
	}
}
