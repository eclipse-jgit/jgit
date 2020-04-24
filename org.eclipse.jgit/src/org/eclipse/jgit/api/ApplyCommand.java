/*
 * Copyright (C) 2011, 2020 IBM Corporation and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.api;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.PatchApplyException;
import org.eclipse.jgit.api.errors.PatchFormatException;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.patch.HunkHeader;
import org.eclipse.jgit.patch.Patch;
import org.eclipse.jgit.util.FileUtils;

/**
 * Apply a patch to files and/or to the index.
 *
 * @see <a href="http://www.kernel.org/pub/software/scm/git/docs/git-apply.html"
 *      >Git documentation about apply</a>
 * @since 2.0
 */
public class ApplyCommand extends GitCommand<ApplyResult> {

	private InputStream in;

	/**
	 * Constructs the command if the patch is to be applied to the index.
	 *
	 * @param repo
	 */
	ApplyCommand(Repository repo) {
		super(repo);
	}

	/**
	 * Set patch
	 *
	 * @param in
	 *            the patch to apply
	 * @return this instance
	 */
	public ApplyCommand setPatch(InputStream in) {
		checkCallable();
		this.in = in;
		return this;
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Executes the {@code ApplyCommand} command with all the options and
	 * parameters collected by the setter methods (e.g.
	 * {@link #setPatch(InputStream)} of this class. Each instance of this class
	 * should only be used for one invocation of the command. Don't call this
	 * method twice on an instance.
	 */
	@Override
	public ApplyResult call() throws GitAPIException, PatchFormatException,
			PatchApplyException {
		checkCallable();
		ApplyResult r = new ApplyResult();
		try {
			final Patch p = new Patch();
			try {
				p.parse(in);
			} finally {
				in.close();
			}
			if (!p.getErrors().isEmpty())
				throw new PatchFormatException(p.getErrors());
			for (FileHeader fh : p.getFiles()) {
				ChangeType type = fh.getChangeType();
				File f = null;
				switch (type) {
				case ADD:
					f = getFile(fh.getNewPath(), true);
					apply(f, fh);
					break;
				case MODIFY:
					f = getFile(fh.getOldPath(), false);
					apply(f, fh);
					break;
				case DELETE:
					f = getFile(fh.getOldPath(), false);
					if (!f.delete())
						throw new PatchApplyException(MessageFormat.format(
								JGitText.get().cannotDeleteFile, f));
					break;
				case RENAME:
					f = getFile(fh.getOldPath(), false);
					File dest = getFile(fh.getNewPath(), false);
					try {
						FileUtils.mkdirs(dest.getParentFile(), true);
						FileUtils.rename(f, dest,
								StandardCopyOption.ATOMIC_MOVE);
					} catch (IOException e) {
						throw new PatchApplyException(MessageFormat.format(
								JGitText.get().renameFileFailed, f, dest), e);
					}
					apply(dest, fh);
					break;
				case COPY:
					f = getFile(fh.getOldPath(), false);
					File target = getFile(fh.getNewPath(), false);
					FileUtils.mkdirs(target.getParentFile(), true);
					Files.copy(f.toPath(), target.toPath());
					apply(target, fh);
				}
				r.addUpdatedFile(f);
			}
		} catch (IOException e) {
			throw new PatchApplyException(MessageFormat.format(
					JGitText.get().patchApplyException, e.getMessage()), e);
		}
		setCallable(false);
		return r;
	}

	private File getFile(String path, boolean create)
			throws PatchApplyException {
		File f = new File(getRepository().getWorkTree(), path);
		if (create)
			try {
				File parent = f.getParentFile();
				FileUtils.mkdirs(parent, true);
				FileUtils.createNewFile(f);
			} catch (IOException e) {
				throw new PatchApplyException(MessageFormat.format(
						JGitText.get().createNewFileFailed, f), e);
			}
		return f;
	}

	/**
	 * @param f
	 * @param fh
	 * @throws IOException
	 * @throws PatchApplyException
	 */
	private void apply(File f, FileHeader fh)
			throws IOException, PatchApplyException {
		RawText rt = new RawText(f);
		List<String> oldLines = new ArrayList<>(rt.size());
		for (int i = 0; i < rt.size(); i++)
			oldLines.add(rt.getString(i));
		List<String> newLines = new ArrayList<>(oldLines);
		int afterLastHunk = 0;
		int lineNumberShift = 0;
		int lastHunkNewLine = -1;
		for (HunkHeader hh : fh.getHunks()) {

			// We assume hunks to be ordered
			if (hh.getNewStartLine() <= lastHunkNewLine) {
				throw new PatchApplyException(MessageFormat
						.format(JGitText.get().patchApplyException, hh));
			}
			lastHunkNewLine = hh.getNewStartLine();

			byte[] b = new byte[hh.getEndOffset() - hh.getStartOffset()];
			System.arraycopy(hh.getBuffer(), hh.getStartOffset(), b, 0,
					b.length);
			RawText hrt = new RawText(b);

			List<String> hunkLines = new ArrayList<>(hrt.size());
			for (int i = 0; i < hrt.size(); i++) {
				hunkLines.add(hrt.getString(i));
			}

			if (hh.getNewStartLine() == 0) {
				// Must be the single hunk for clearing all content
				if (fh.getHunks().size() == 1
						&& canApplyAt(hunkLines, newLines, 0)) {
					newLines.clear();
					break;
				}
				throw new PatchApplyException(MessageFormat
						.format(JGitText.get().patchApplyException, hh));
			}
			// Hunk lines as reported by the hunk may be off, so don't rely on
			// them.
			int applyAt = hh.getNewStartLine() - 1 + lineNumberShift;
			// But they definitely should not go backwards.
			if (applyAt < afterLastHunk && lineNumberShift < 0) {
				applyAt = hh.getNewStartLine() - 1;
				lineNumberShift = 0;
			}
			if (applyAt < afterLastHunk) {
				throw new PatchApplyException(MessageFormat
						.format(JGitText.get().patchApplyException, hh));
			}
			boolean applies = false;
			int oldLinesInHunk = hh.getLinesContext()
					+ hh.getOldImage().getLinesDeleted();
			if (oldLinesInHunk <= 1) {
				// Don't shift hunks without context lines. Just try the
				// position corrected by the current lineNumberShift, and if
				// that fails, the position recorded in the hunk header.
				applies = canApplyAt(hunkLines, newLines, applyAt);
				if (!applies && lineNumberShift != 0) {
					applyAt = hh.getNewStartLine() - 1;
					applies = applyAt >= afterLastHunk
							&& canApplyAt(hunkLines, newLines, applyAt);
				}
			} else {
				int maxShift = applyAt - afterLastHunk;
				for (int shift = 0; shift <= maxShift; shift++) {
					if (canApplyAt(hunkLines, newLines, applyAt - shift)) {
						applies = true;
						applyAt -= shift;
						break;
					}
				}
				if (!applies) {
					// Try shifting the hunk downwards
					applyAt = hh.getNewStartLine() - 1 + lineNumberShift;
					maxShift = newLines.size() - applyAt - oldLinesInHunk;
					for (int shift = 1; shift <= maxShift; shift++) {
						if (canApplyAt(hunkLines, newLines, applyAt + shift)) {
							applies = true;
							applyAt += shift;
							break;
						}
					}
				}
			}
			if (!applies) {
				throw new PatchApplyException(MessageFormat
						.format(JGitText.get().patchApplyException, hh));
			}
			// Hunk applies at applyAt. Apply it, and update afterLastHunk and
			// lineNumberShift
			lineNumberShift = applyAt - hh.getNewStartLine() + 1;
			int sz = hunkLines.size();
			for (int j = 1; j < sz; j++) {
				String hunkLine = hunkLines.get(j);
				switch (hunkLine.charAt(0)) {
				case ' ':
					applyAt++;
					break;
				case '-':
					newLines.remove(applyAt);
					break;
				case '+':
					newLines.add(applyAt++, hunkLine.substring(1));
					break;
				default:
					break;
				}
			}
			afterLastHunk = applyAt;
		}
		if (!isNoNewlineAtEndOfFile(fh)) {
			newLines.add(""); //$NON-NLS-1$
		}
		if (!rt.isMissingNewlineAtEnd()) {
			oldLines.add(""); //$NON-NLS-1$
		}
		if (!isChanged(oldLines, newLines)) {
			return; // Don't touch the file
		}
		try (Writer fw = Files.newBufferedWriter(f.toPath())) {
			for (Iterator<String> l = newLines.iterator(); l.hasNext();) {
				fw.write(l.next());
				if (l.hasNext()) {
					// Don't bother handling line endings - if it was Windows,
					// the \r is still there!
					fw.write('\n');
				}
			}
		}
		getRepository().getFS().setExecute(f, fh.getNewMode() == FileMode.EXECUTABLE_FILE);
	}

	private boolean canApplyAt(List<String> hunkLines, List<String> newLines,
			int line) {
		int sz = hunkLines.size();
		int limit = newLines.size();
		int pos = line;
		for (int j = 1; j < sz; j++) {
			String hunkLine = hunkLines.get(j);
			switch (hunkLine.charAt(0)) {
			case ' ':
			case '-':
				if (pos >= limit
						|| !newLines.get(pos).equals(hunkLine.substring(1))) {
					return false;
				}
				pos++;
				break;
			default:
				break;
			}
		}
		return true;
	}

	private static boolean isChanged(List<String> ol, List<String> nl) {
		if (ol.size() != nl.size())
			return true;
		for (int i = 0; i < ol.size(); i++)
			if (!ol.get(i).equals(nl.get(i)))
				return true;
		return false;
	}

	private boolean isNoNewlineAtEndOfFile(FileHeader fh) {
		List<? extends HunkHeader> hunks = fh.getHunks();
		if (hunks == null || hunks.isEmpty()) {
			return false;
		}
		HunkHeader lastHunk = hunks.get(hunks.size() - 1);
		RawText lhrt = new RawText(lastHunk.getBuffer());
		return lhrt.getString(lhrt.size() - 1)
				.equals("\\ No newline at end of file"); //$NON-NLS-1$
	}
}
