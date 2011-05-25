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
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;

/**
 * Generator of author information for lines based on when they were introduced
 * into a file.
 */
public class BlameGenerator {

	private final Repository repository;

	private final String path;

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
	 * Set diff algorithm
	 *
	 * @param algorithm
	 * @return this builder
	 */
	public BlameGenerator setDiffAlgorithm(DiffAlgorithm algorithm) {
		diffAlgorithm = algorithm;
		return this;
	}

	/**
	 * Set raw text comparator
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

	private List<Line> getLines(int count, RevCommit commit) {
		List<Line> lines = new ArrayList<Line>(count);
		for (int i = 0; i < count; i++)
			lines.add(new Line(commit, i));
		return lines;
	}

	/**
	 * Merge lines from one side of a diff to another.
	 *
	 * @param commit
	 *            the commit at the diff "A" side
	 * @param size
	 * @param bLines
	 * @param diffs
	 * @return array of lines for the "A" side
	 */
	private Line[] merge(RevCommit commit, int size, Line[] bLines,
			EditList diffs) {
		Line[] aLines = new Line[size];
		int aIndex = 0;
		int bIndex = 0;

		for (Edit diff : diffs) {
			while (aIndex < diff.getBeginA()) {
				Line line = bLines[bIndex++];
				if (line != null)
					line.setStart(commit);
				aLines[aIndex++] = line;
			}
			aIndex = diff.getEndA();
			bIndex = diff.getEndB();
		}

		while (bIndex < bLines.length) {
			Line line = bLines[bIndex++];
			if (line != null)
				line.setStart(commit);
			aLines[aIndex++] = line;
		}
		return aLines;
	}

	/**
	 * Checks if the given path has changed due to rename or copy and returns
	 * the previous path.
	 *
	 * @param commit
	 * @param detector
	 * @param treeWalk
	 * @param lastPath
	 * @return previous path
	 * @throws IOException
	 */
	private String getPath(RevCommit commit, RenameDetector detector,
			TreeWalk treeWalk, String lastPath) throws IOException {
		// Check if path changes due to rename
		if (commit.getParentCount() == 1) {
			treeWalk.reset(commit.getParent(0).getTree(), commit.getTree());
			detector.reset();
			detector.addAll(DiffEntry.scan(treeWalk));
			for (DiffEntry ent : detector.compute())
				if ((ent.getChangeType() == ChangeType.RENAME || ent
						.getChangeType() == ChangeType.COPY)
						&& ent.getNewPath().equals(lastPath))
					return ent.getOldPath();
		}
		return lastPath;
	}

	/**
	 * Generate the line information containing the revision that introduced
	 * each line currently present in the starting revision of the file path.
	 *
	 * @return lines
	 * @throws IOException
	 */
	public List<Line> generate() throws IOException {
		RevWalk revWalk = new RevWalk(repository);

		String lastPath = path;
		RawText lastText = null;
		Line[] workingLines = null;

		List<Line> lines = null;
		try {
			ObjectReader reader = revWalk.getObjectReader();
			revWalk.setRetainBody(true);
			revWalk.setTreeFilter(FollowFilter.create(path));
			TreeWalk treeWalk = new TreeWalk(reader);
			treeWalk.setFilter(TreeFilter.ANY_DIFF);
			RenameDetector detector = new RenameDetector(repository);

			AnyObjectId head = start != null ? start : repository
					.resolve(Constants.HEAD);
			revWalk.markStart(revWalk.parseCommit(head));
			if (end != null)
				revWalk.markUninteresting(revWalk.parseCommit(end));

			for (RevCommit commit : revWalk) {

				TreeWalk blobWalk = TreeWalk.forPath(reader, lastPath,
						commit.getTree());
				if (blobWalk == null)
					break;

				RawText currentText = getRawText(blobWalk.getObjectId(0));
				if (lastText != null) {
					EditList diffs = diffAlgorithm.diff(textComparator,
							currentText, lastText);
					workingLines = merge(commit, currentText.size(),
							workingLines, diffs);
				} else {
					lines = getLines(currentText.size(), commit);
					workingLines = lines.toArray(new Line[lines.size()]);
				}
				lastText = currentText;
				lastPath = getPath(commit, detector, treeWalk, lastPath);
			}
		} finally {
			revWalk.release();
		}
		return lines;
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
