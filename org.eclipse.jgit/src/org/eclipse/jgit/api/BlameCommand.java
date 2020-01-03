/*
 * Copyright (C) 2011, 2019 GitHub Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.api;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.blame.BlameGenerator;
import org.eclipse.jgit.blame.BlameResult;
import org.eclipse.jgit.diff.DiffAlgorithm;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;

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
				gen.prepareHead();
			}
			return gen.computeBlameResult();
		} catch (IOException e) {
			throw new JGitInternalException(e.getMessage(), e);
		}
	}
}
