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
package org.eclipse.jgit.blame;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.eclipse.jgit.JGitText;
import org.eclipse.jgit.diff.DiffAlgorithm;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.diff.HistogramDiff;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.diff.RenameDetector;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.FollowFilter;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;

/**
 * Generator of author information for lines based on when they were introduced
 * into a file.
 */
public class BlameGenerator {

	private static class Session {

		private RevWalk walk;

		private RevCommit commit;

		private String path;

		private RawText text;

		private Line[] lines;

		private boolean merges;
	}

	private final Repository repository;

	private final String path;

	private AnyObjectId start;

	private AnyObjectId end;

	private DiffAlgorithm diffAlgorithm = new HistogramDiff();

	private RawTextComparator textComparator = RawTextComparator.DEFAULT;

	private final Session session = new Session();

	/**
	 * Create a blame generator for the repository and path
	 *
	 * @param repository
	 * @param path
	 */
	public BlameGenerator(Repository repository, String path) {
		this.repository = repository;
		this.path = path;
	}

	/**
	 * Set starting commit id
	 *
	 * @param commitId
	 * @return this builder
	 */
	public BlameGenerator setStart(ObjectId commitId) {
		start = commitId;
		return this;
	}

	/**
	 * Set ending commit id
	 *
	 * @param commitId
	 * @return this builder
	 */
	public BlameGenerator setEnd(ObjectId commitId) {
		end = commitId;
		return this;
	}

	/**
	 * Set diff algorithm to use when comparing revisions
	 *
	 * @param algorithm
	 * @return this builder
	 */
	public BlameGenerator setDiffAlgorithm(DiffAlgorithm algorithm) {
		diffAlgorithm = algorithm;
		return this;
	}

	/**
	 * Set raw text comparator to use when comparing revisions
	 *
	 * @param comparator
	 * @return this builder
	 */
	public BlameGenerator setTextComparator(RawTextComparator comparator) {
		textComparator = comparator;
		return this;
	}

	/**
	 * Get raw text of blob
	 *
	 * @param blob
	 * @return raw text
	 * @throws IOException
	 */
	protected RawText getRawText(ObjectId blob) throws IOException {
		ObjectLoader loader = repository.open(blob, Constants.OBJ_BLOB);
		return new RawText(loader.getCachedBytes(Integer.MAX_VALUE));
	}

	private void createLines(RevCommit commit) {
		int count = session.text.size();
		Line[] lines = new Line[count];
		for (int i = 0; i < count; i++)
			lines[i] = new Line(commit, i);
		session.lines = lines;
	}

	/**
	 * Merge lines from one side of a diff to another.
	 *
	 * @param commit
	 *            the commit at the diff "A" side
	 * @param size
	 * @param diffs
	 */
	private void merge(RevCommit commit, int size, EditList diffs) {
		Line[] aLines = new Line[size];
		Line[] bLines = session.lines;
		boolean merges = false;
		int aIndex = 0;
		int bIndex = 0;

		for (Edit diff : diffs) {
			while (aIndex < diff.getBeginA()) {
				Line line = bLines[bIndex++];
				if (line != null) {
					line.setCommit(commit);
					merges = true;
				}
				aLines[aIndex++] = line;
			}
			aIndex = diff.getEndA();
			while (bIndex < diff.getEndB())
				bLines[bIndex++].markBound();
			bIndex = diff.getEndB();
		}

		while (bIndex < bLines.length) {
			Line line = bLines[bIndex++];
			if (line != null) {
				line.setCommit(commit);
				merges = true;
			}
			aLines[aIndex++] = line;
		}

		session.lines = aLines;
		session.merges = merges;
	}

	/**
	 * Checks if the given path has changed due to rename or copy and return the
	 * previous path.
	 *
	 * @param commit
	 * @throws IOException
	 */
	private void updatePath(RevCommit commit) throws IOException {
		TreeWalk treeWalk = new TreeWalk(session.walk.getObjectReader());
		try {
			treeWalk.setFilter(TreeFilter.ANY_DIFF);
			RenameDetector detector = new RenameDetector(repository);
			treeWalk.reset(commit.getTree(), session.commit.getTree());
			detector.addAll(DiffEntry.scan(treeWalk));
			for (DiffEntry ent : detector.compute())
				if ((ent.getChangeType() == ChangeType.RENAME || ent
						.getChangeType() == ChangeType.COPY)
						&& ent.getNewPath().equals(session.path)) {
					session.path = ent.getOldPath();
					break;
				}
		} finally {
			treeWalk.release();
		}
	}

	/**
	 * Get blob id from given commit for current path being blamed.
	 *
	 * @param commit
	 * @return object id or null if none for this commit
	 * @throws IOException
	 */
	private ObjectId getBlob(RevCommit commit) throws IOException {
		TreeWalk walk = TreeWalk.forPath(session.walk.getObjectReader(),
				session.path, commit.getTree());
		return walk != null ? walk.getObjectId(0) : null;
	}

	/**
	 * Start the blame generation and generates the lines at the latest
	 * revision.
	 *
	 * Subsequent calls to {@link #next()} will update the lines with the blame
	 * data from the previous revisions.
	 *
	 * @return unmodifiable list of lines in the latest revision or null if the
	 *         latest revision has no lines
	 * @throws IOException
	 */
	public List<Line> start() throws IOException {
		if (session.walk != null)
			throw new IllegalStateException(
					JGitText.get().blameHasAlreadyBeenStarted);

		RevWalk revWalk = new RevWalk(repository);
		revWalk.setRetainBody(true);
		revWalk.setTreeFilter(FollowFilter.create(path));
		session.walk = revWalk;

		AnyObjectId head = start != null ? start : repository
				.resolve(Constants.HEAD);
		revWalk.markStart(revWalk.parseCommit(head));
		if (end != null)
			revWalk.markUninteresting(revWalk.parseCommit(end));

		RevCommit commit = revWalk.next();
		if (commit == null)
			return null;

		session.path = path;
		session.commit = commit;
		ObjectId blob = getBlob(commit);
		if (blob == null)
			return null;
		session.text = getRawText(blob);
		createLines(commit);

		return Collections.unmodifiableList(Arrays.asList(session.lines));
	}

	/**
	 * Generate the blame data for the next commit in the already started rev
	 * walk. This must be called after {@link #start()} has been called.
	 *
	 * @return true if more blame data exists, false if done
	 * @throws IOException
	 */
	public boolean next() throws IOException {
		if (session.walk == null)
			throw new IllegalStateException(
					JGitText.get().blameHasNotBeenStarted);

		RevCommit commit = session.walk.next();
		if (commit == null) {
			for (int i = 0; i < session.lines.length; i++)
				session.lines[i].markBound();
			return false;
		}
		ObjectId blob = getBlob(commit);
		// Check for rename when blob is missing
		if (blob == null) {
			updatePath(commit);
			blob = getBlob(commit);
		}
		if (blob == null) {
			for (int i = 0; i < session.lines.length; i++)
				session.lines[i].markBound();
			return false;
		}

		RawText text = getRawText(blob);
		EditList diffs = diffAlgorithm.diff(textComparator, text, session.text);
		merge(commit, text.size(), diffs);
		session.text = text;
		session.commit = commit;
		return session.merges;
	}

	/**
	 * Release the current blame session started from calling {@link #start()}
	 *
	 * @return this generator
	 */
	public BlameGenerator release() {
		if (session.walk != null) {
			session.walk.release();
			session.walk = null;
		}
		session.lines = null;
		session.merges = false;
		session.path = null;
		session.text = null;
		return this;
	}

	/**
	 * @return repository
	 */
	public Repository getRepository() {
		return repository;
	}

	/**
	 * @return path
	 */
	public String getPath() {
		return path;
	}
}
