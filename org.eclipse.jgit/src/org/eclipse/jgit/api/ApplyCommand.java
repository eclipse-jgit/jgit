/*
 * Copyright (C) 2011, 2012, IBM Corporation and others.
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
package org.eclipse.jgit.api;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.StandardCopyOption;
import java.text.MessageFormat;
import java.util.ArrayList;
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
import org.eclipse.jgit.util.IO;

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
						FileUtils.rename(f, dest,
								StandardCopyOption.ATOMIC_MOVE);
					} catch (IOException e) {
						throw new PatchApplyException(MessageFormat.format(
								JGitText.get().renameFileFailed, f, dest), e);
					}
					break;
				case COPY:
					f = getFile(fh.getOldPath(), false);
					byte[] bs = IO.readFully(f);
					FileOutputStream fos = new FileOutputStream(getFile(
							fh.getNewPath(),
							true));
					try {
						fos.write(bs);
					} finally {
						fos.close();
					}
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
		for (HunkHeader hh : fh.getHunks()) {

			byte[] b = new byte[hh.getEndOffset() - hh.getStartOffset()];
			System.arraycopy(hh.getBuffer(), hh.getStartOffset(), b, 0,
					b.length);
			RawText hrt = new RawText(b);

			List<String> hunkLines = new ArrayList<>(hrt.size());
			for (int i = 0; i < hrt.size(); i++)
				hunkLines.add(hrt.getString(i));
			int pos = 0;
			for (int j = 1; j < hunkLines.size(); j++) {
				String hunkLine = hunkLines.get(j);
				switch (hunkLine.charAt(0)) {
				case ' ':
					if (!newLines.get(hh.getNewStartLine() - 1 + pos).equals(
							hunkLine.substring(1))) {
						throw new PatchApplyException(MessageFormat.format(
								JGitText.get().patchApplyException, hh));
					}
					pos++;
					break;
				case '-':
					if (hh.getNewStartLine() == 0) {
						newLines.clear();
					} else {
						if (!newLines.get(hh.getNewStartLine() - 1 + pos)
								.equals(hunkLine.substring(1))) {
							throw new PatchApplyException(MessageFormat.format(
									JGitText.get().patchApplyException, hh));
						}
						newLines.remove(hh.getNewStartLine() - 1 + pos);
					}
					break;
				case '+':
					newLines.add(hh.getNewStartLine() - 1 + pos,
							hunkLine.substring(1));
					pos++;
					break;
				}
			}
		}
		if (!isNoNewlineAtEndOfFile(fh))
			newLines.add(""); //$NON-NLS-1$
		if (!rt.isMissingNewlineAtEnd())
			oldLines.add(""); //$NON-NLS-1$
		if (!isChanged(oldLines, newLines))
			return; // don't touch the file
		StringBuilder sb = new StringBuilder();
		for (String l : newLines) {
			// don't bother handling line endings - if it was windows, the \r is
			// still there!
			sb.append(l).append('\n');
		}
		if (sb.length() > 0) {
			sb.deleteCharAt(sb.length() - 1);
		}
		try (Writer fw = new OutputStreamWriter(new FileOutputStream(f),
				UTF_8)) {
			fw.write(sb.toString());
		}

		getRepository().getFS().setExecute(f, fh.getNewMode() == FileMode.EXECUTABLE_FILE);
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
