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
import java.util.ArrayList;
import java.util.List;

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
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.FollowFilter;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.eclipse.jgit.util.IO;

/**
 * Class to assemble the history of every line in every revision of a file.
 */
public class RevisionBuilder {

	private Repository repository;

	private String path;

	private AnyObjectId start;

	private AnyObjectId end;

	private DiffAlgorithm diffAlgorithm = new HistogramDiff();

	private RawTextComparator textComparator = RawTextComparator.DEFAULT;

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
	 * Set ending commit id
	 *
	 * @param commitId
	 * @return this builder
	 */
	public RevisionBuilder setEnd(ObjectId commitId) {
		this.end = commitId;
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
	 * Get raw text of blob
	 *
	 * @param blob
	 * @return raw text
	 * @throws IOException
	 */
	protected RawText getRawText(ObjectId blob) throws IOException {
		ObjectLoader loader = repository.open(blob, Constants.OBJ_BLOB);
		byte[] contents;
		if (loader.isLarge())
			contents = IO.readWholeStream(loader.openStream(),
					(int) loader.getSize()).array();
		else
			contents = loader.getCachedBytes();
		return new RawText(contents);
	}

	/**
	 * Add lines to revision
	 *
	 * @param revision
	 * @return revision
	 */
	protected Revision addLines(Revision revision) {
		for (int i = 0; i < revision.getLineCount(); i++)
			revision.addLine(new Line(revision.getNumber()));
		return revision;
	}

	private void mergeLines(Revision current, Revision next, EditList edits) {
		int currentLine = 0;
		for (Edit edit : edits) {
			while (currentLine < edit.getBeginA())
				next.addLine(current.getLine(currentLine++));
			for (int i = edit.getBeginB(); i < edit.getEndB(); i++)
				next.addLine(new Line(next.getNumber()));
			currentLine = edit.getEndA();
		}
		while (currentLine < current.getLineCount())
			next.addLine(current.getLine(currentLine++));
	}

	/**
	 * Build history
	 *
	 * @return file history
	 * @throws IOException
	 */
	public Revision buildLatest() throws IOException {
		final Revision[] last = new Revision[] { null };
		build(new RevisionFilter() {

			public boolean include(Revision revision) {
				last[0] = revision;
				return true;
			}
		});
		return last[0];
	}

	/**
	 * Build history
	 *
	 * @return file history
	 * @throws IOException
	 */
	public List<Revision> buildAll() throws IOException {
		final List<Revision> revisions = new ArrayList<Revision>();
		build(new RevisionFilter() {

			public boolean include(Revision revision) {
				revisions.add(revision);
				return true;
			}
		});
		return revisions;
	}

	/**
	 * Build revisions
	 *
	 * @param filter
	 * @throws IOException
	 */
	public void build(RevisionFilter filter) throws IOException {
		RevWalk revWalk = new RevWalk(repository);
		ObjectReader reader = revWalk.getObjectReader();
		revWalk.sort(RevSort.REVERSE);
		revWalk.setRetainBody(true);
		revWalk.setTreeFilter(FollowFilter.create(path));

		TreeWalk treeWalk = new TreeWalk(reader);
		treeWalk.setFilter(TreeFilter.ANY_DIFF);
		RenameDetector detector = new RenameDetector(repository);

		String currentPath = path;
		RawText previousText = null;
		Revision previous = null;
		int rev = 0;
		try {
			AnyObjectId head = start != null ? start : repository
					.resolve(Constants.HEAD);
			revWalk.markStart(revWalk.parseCommit(head));
			if (end != null)
				revWalk.markUninteresting(revWalk.parseCommit(end));
			for (RevCommit commit : revWalk) {
				// Check if path changes due to rename
				if (commit.getParentCount() == 1) {
					treeWalk.reset(commit.getTree(), commit.getParent(0)
							.getTree());
					detector.reset();
					detector.addAll(DiffEntry.scan(treeWalk));
					for (DiffEntry ent : detector.compute())
						if (ent.getChangeType() == ChangeType.RENAME
								&& ent.getOldPath().equals(currentPath)) {
							currentPath = ent.getNewPath();
							break;
						}
				}
				TreeWalk blobWalk = TreeWalk.forPath(reader, currentPath,
						commit.getTree());
				if (blobWalk != null) {
					ObjectId blobId = blobWalk.getObjectId(0);
					RawText currentText = getRawText(blobId);
					Revision revision = new Revision(currentPath,
							currentText.size());
					revision.setNumber(rev++);
					revision.setCommit(commit);
					revision.setBlob(blobId);
					if (!filter.include(revision))
						break;
					if (previous != null)
						mergeLines(previous, revision, diffAlgorithm.diff(
								textComparator, previousText, currentText));
					else
						addLines(revision);
					previous = revision;
					previousText = currentText;
				}
			}
		} finally {
			revWalk.release();
		}
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
