/*
 * Copyright (C) 2011, GitHub Inc.
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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.blame.BlameGenerator;
import org.eclipse.jgit.blame.BlameResult;
import org.eclipse.jgit.diff.DiffAlgorithm;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.CoreConfig.AutoCRLF;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.treewalk.WorkingTreeOptions;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.io.AutoLFInputStream;

/**
 * Blame command for building a {@link org.eclipse.jgit.blame.BlameResult} for a
 * file path.
 */
public class BlameCommand extends GitCommand<BlameResult> {

	private String path;

	private DiffAlgorithm diffAlgorithm;

	private RawTextComparator textComparator;

	private ObjectId startCommit;

	private Collection<ObjectId> reverseEndCommits;

	private Boolean followFileRenames;

	/**
	 * Constructor for BlameCommand
	 *
	 * @param repo
	 *            the {@link org.eclipse.jgit.lib.Repository}
	 */
	public BlameCommand(Repository repo) {
		super(repo);
	}

	/**
	 * Set file path.
	 *
	 * @param filePath
	 *            file path (with <code>/</code> as separator)
	 * @return this command
	 */
	public BlameCommand setFilePath(String filePath) {
		this.path = filePath;
		return this;
	}

	/**
	 * Set diff algorithm
	 *
	 * @param diffAlgorithm
	 *            a {@link org.eclipse.jgit.diff.DiffAlgorithm} object.
	 * @return this command
	 */
	public BlameCommand setDiffAlgorithm(DiffAlgorithm diffAlgorithm) {
		this.diffAlgorithm = diffAlgorithm;
		return this;
	}

	/**
	 * Set raw text comparator
	 *
	 * @param textComparator
	 *            a {@link org.eclipse.jgit.diff.RawTextComparator}
	 * @return this command
	 */
	public BlameCommand setTextComparator(RawTextComparator textComparator) {
		this.textComparator = textComparator;
		return this;
	}

	/**
	 * Set start commit id
	 *
	 * @param commit
	 *            id of a commit
	 * @return this command
	 */
	public BlameCommand setStartCommit(AnyObjectId commit) {
		this.startCommit = commit.toObjectId();
		return this;
	}

	/**
	 * Enable (or disable) following file renames.
	 * <p>
	 * If true renames are followed using the standard FollowFilter behavior
	 * used by RevWalk (which matches {@code git log --follow} in the C
	 * implementation). This is not the same as copy/move detection as
	 * implemented by the C implementation's of {@code git blame -M -C}.
	 *
	 * @param follow
	 *            enable following.
	 * @return {@code this}
	 */
	public BlameCommand setFollowFileRenames(boolean follow) {
		followFileRenames = Boolean.valueOf(follow);
		return this;
	}

	/**
	 * Configure the command to compute reverse blame (history of deletes).
	 *
	 * @param start
	 *            oldest commit to traverse from. The result file will be loaded
	 *            from this commit's tree.
	 * @param end
	 *            most recent commit to stop traversal at. Usually an active
	 *            branch tip, tag, or HEAD.
	 * @return {@code this}
	 * @throws java.io.IOException
	 *             the repository cannot be read.
	 */
	public BlameCommand reverse(AnyObjectId start, AnyObjectId end)
			throws IOException {
		return reverse(start, Collections.singleton(end.toObjectId()));
	}

	/**
	 * Configure the generator to compute reverse blame (history of deletes).
	 *
	 * @param start
	 *            oldest commit to traverse from. The result file will be loaded
	 *            from this commit's tree.
	 * @param end
	 *            most recent commits to stop traversal at. Usually an active
	 *            branch tip, tag, or HEAD.
	 * @return {@code this}
	 * @throws java.io.IOException
	 *             the repository cannot be read.
	 */
	public BlameCommand reverse(AnyObjectId start, Collection<ObjectId> end)
			throws IOException {
		startCommit = start.toObjectId();
		reverseEndCommits = new ArrayList<>(end);
		return this;
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Generate a list of lines with information about when the lines were
	 * introduced into the file path.
	 */
	@Override
	public BlameResult call() throws GitAPIException {
		checkCallable();
		try (BlameGenerator gen = new BlameGenerator(repo, path)) {
			if (diffAlgorithm != null)
				gen.setDiffAlgorithm(diffAlgorithm);
			if (textComparator != null)
				gen.setTextComparator(textComparator);
			if (followFileRenames != null)
				gen.setFollowFileRenames(followFileRenames.booleanValue());

			if (reverseEndCommits != null)
				gen.reverse(startCommit, reverseEndCommits);
			else if (startCommit != null)
				gen.push(null, startCommit);
			else {
				gen.push(null, repo.resolve(Constants.HEAD));
				if (!repo.isBare()) {
					DirCache dc = repo.readDirCache();
					int entry = dc.findEntry(path);
					if (0 <= entry)
						gen.push(null, dc.getEntry(entry).getObjectId());

					File inTree = new File(repo.getWorkTree(), path);
					if (repo.getFS().isFile(inTree)) {
						RawText rawText = getRawText(inTree);
						gen.push(null, rawText);
					}
				}
			}
			return gen.computeBlameResult();
		} catch (IOException e) {
			throw new JGitInternalException(e.getMessage(), e);
		}
	}

	private RawText getRawText(File inTree) throws IOException,
			FileNotFoundException {
		RawText rawText;

		WorkingTreeOptions workingTreeOptions = getRepository().getConfig()
				.get(WorkingTreeOptions.KEY);
		AutoCRLF autoCRLF = workingTreeOptions.getAutoCRLF();
		switch (autoCRLF) {
		case FALSE:
		case INPUT:
			// Git used the repo format on checkout, but other tools
			// may change the format to CRLF. We ignore that here.
			rawText = new RawText(inTree);
			break;
		case TRUE:
			try (AutoLFInputStream in = new AutoLFInputStream(
					new FileInputStream(inTree), true)) {
				// Canonicalization should lead to same or shorter length
				// (CRLF to LF), so the file size on disk is an upper size bound
				rawText = new RawText(toByteArray(in, (int) inTree.length()));
			}
			break;
		default:
			throw new IllegalArgumentException(
					"Unknown autocrlf option " + autoCRLF); //$NON-NLS-1$
		}
		return rawText;
	}

	private static byte[] toByteArray(InputStream source, int upperSizeLimit)
			throws IOException {
		byte[] buffer = new byte[upperSizeLimit];
		try {
			int read = IO.readFully(source, buffer, 0);
			if (read == upperSizeLimit) {
				return buffer;
			}
			byte[] copy = new byte[read];
			System.arraycopy(buffer, 0, copy, 0, read);
			return copy;
		} finally {
			source.close();
		}
	}
}
