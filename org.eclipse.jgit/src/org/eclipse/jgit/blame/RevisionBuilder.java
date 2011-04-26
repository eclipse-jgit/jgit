/*
 * Copyright (C) 2011, Kevin Sawicki <kevin@github.com>
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
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.diff.DiffAlgorithm;
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
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.FollowFilter;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;

/**
 * Class to assemble the history of every line in every revision of a file.
 */
public class RevisionBuilder {

	private Repository repository;

	private String path;

	private AnyObjectId start;

	private DiffAlgorithm diffAlgorithm;

	private RawTextComparator textComparator;

	/**
	 * Create revision builder for repository and path
	 *
	 * @param repository
	 * @param path
	 */
	public RevisionBuilder(Repository repository, String path) {
		this.repository = repository;
		this.path = path;
	}

	/**
	 * Set starting commit id
	 *
	 * @param commitId
	 * @return this builder
	 */
	public RevisionBuilder setStart(ObjectId commitId) {
		this.start = commitId;
		return this;
	}

	/**
	 * Set diff algorithm
	 *
	 * @param algorithm
	 * @return this builder
	 */
	public RevisionBuilder setDiffAlgorithm(DiffAlgorithm algorithm) {
		this.diffAlgorithm = algorithm;
		return this;
	}

	/**
	 * Set raw text comparator
	 *
	 * @param comparator
	 * @return this builder
	 */
	public RevisionBuilder setTextComparator(RawTextComparator comparator) {
		this.textComparator = comparator;
		return this;
	}

	/**
	 * Parse and add lines to revision for blob
	 *
	 * @param revision
	 * @return raw text
	 * @throws IOException
	 */
	protected RawText buildLines(Revision revision) throws IOException {
		ObjectLoader loader = repository.open(revision.getBlob(),
				Constants.OBJ_BLOB);
		RawText text = new RawText(loader.getCachedBytes());
		int number = revision.getNumber();
		for (int i = 0; i < text.size(); i++)
			revision.addLine(new Line(number));
		return text;
	}

	private void mergeLines(Revision current, Revision next, EditList edits) {
		List<Line> merges = new LinkedList<Line>();
		int currentLine = 0;
		int nextLine = 0;
		for (Edit edit : edits) {
			while (currentLine < edit.getBeginA())
				merges.add(current.getLine(currentLine++).setNumber(nextLine++));
			nextLine = edit.getEndB();
			currentLine = edit.getEndA();
		}
		while (currentLine < current.getLineCount())
			merges.add(current.getLine(currentLine++).setNumber(nextLine++));
		for (Line merge : merges)
			next.merge(merge);
	}

	/**
	 * Build {@link Deque} of revisions
	 *
	 * @return {@link Deque} of revisions
	 * @throws IOException
	 */
	protected Deque<Revision> buildRevisions() throws IOException {
		Deque<Revision> revisions = new LinkedList<Revision>();
		RevWalk revWalk = new RevWalk(repository);
		ObjectReader reader = revWalk.getObjectReader();
		revWalk.setRetainBody(true);
		revWalk.setTreeFilter(FollowFilter.create(this.path));
		TreeWalk treeWalk = new TreeWalk(reader);
		treeWalk.setFilter(TreeFilter.ANY_DIFF);
		RenameDetector detector = new RenameDetector(repository);
		String currentPath = this.path;
		try {
			AnyObjectId head = this.start != null ? this.start : repository
					.resolve(Constants.HEAD);
			revWalk.markStart(revWalk.parseCommit(head));
			for (RevCommit commit : revWalk) {
				Revision revision = new Revision(currentPath);
				revision.setCommit(commit);
				TreeWalk blobWalk = TreeWalk.forPath(reader, currentPath,
						commit.getTree());
				if (blobWalk != null) {
					revision.setBlob(blobWalk.getObjectId(0));
					revisions.addFirst(revision);
				}
				if (commit.getParentCount() == 1) {
					treeWalk.reset(commit.getTree(), commit.getParent(0)
							.getTree());
					detector.reset();
					detector.addAll(DiffEntry.scan(treeWalk));
					for (DiffEntry ent : detector.compute())
						if (ent.getChangeType() == ChangeType.RENAME
								&& ent.getOldPath().equals(currentPath))
							currentPath = ent.getNewPath();
				}
			}
		} finally {
			revWalk.release();
		}
		return revisions;
	}

	/**
	 * Build history
	 *
	 * @return file history
	 * @throws IOException
	 */
	public RevisionContainer build() throws IOException {
		if (diffAlgorithm == null)
			diffAlgorithm = new HistogramDiff();
		if (textComparator == null)
			textComparator = RawTextComparator.DEFAULT;

		Deque<Revision> commits = buildRevisions();

		if (commits.isEmpty())
			return new RevisionContainer();

		RevisionContainer container = new RevisionContainer();
		Iterator<Revision> iterator = commits.iterator();
		Revision current = iterator.next();
		container.addRevision(current);
		RawText currentText = buildLines(current);

		Revision next = null;
		while (iterator.hasNext()) {
			next = iterator.next();
			container.addRevision(next);
			RawText nextText = buildLines(next);
			EditList edits = diffAlgorithm.diff(textComparator, currentText,
					nextText);
			mergeLines(current, next, edits);
			current = next;
			currentText = nextText;
		}
		return container;
	}

	/**
	 * @return repository
	 */
	public Repository getRepository() {
		return this.repository;
	}

	/**
	 * @return path
	 */
	public String getPath() {
		return this.path;
	}

}
