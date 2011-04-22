/*******************************************************************************
 *  Copyright (c) 2011 GitHub Inc.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *    Kevin Sawicki (GitHub Inc.) - initial API and implementation
 *******************************************************************************/
package org.eclipse.jgit.lines;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.diff.HistogramDiff;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectStream;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.FollowFilter;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.jgit.treewalk.TreeWalk;

/**
 * Class to assemble the history of every line in every revision of a file.
 *
 * @author Kevin Sawicki (kevin@github.com)
 */
public class RevisionBuilder {

	private Repository repository;

	private String path;

	private long diffTime = 0L;

	private long commitWalkTime = 0L;

	private long treeWalkTime = 0L;

	private long parseTime = 0L;

	/**
	 * Create revision builder for repository and path
	 *
	 * @param repository
	 * @param path
	 * @throws IOException
	 */
	public RevisionBuilder(String repository, String path) throws IOException {
		this(new FileRepository(repository), path);
	}

	/**
	 * Create revision build for repository and path
	 *
	 * @param repository
	 * @param path
	 */
	public RevisionBuilder(Repository repository, String path) {
		this.repository = repository;
		this.path = path;
	}

	/**
	 * Get blob id of path in specified revision
	 *
	 * @param revision
	 * @return blob id in tree
	 * @throws IOException
	 */
	protected ObjectId getBlob(Revision revision) throws IOException {
		TreeWalk treeWalk = TreeWalk.forPath(repository, this.path, revision
				.getCommit().getTree());
		try {
			return treeWalk.getObjectId(0);
		} finally {
			treeWalk.release();
		}
	}

	/**
	 * Parse and add lines to revision for blob
	 *
	 * @param revision
	 * @throws IOException
	 */
	protected void buildLines(Revision revision) throws IOException {
		ObjectStream stream = repository.open(revision.getBlob(),
				Constants.OBJ_BLOB).openStream();
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new InputStreamReader(stream));
			String content = null;
			while ((content = reader.readLine()) != null)
				revision.addLine(new Line(revision.getNumber(), content));
		} finally {
			if (reader != null)
				try {
					reader.close();
				} catch (IOException ignore) {
					// Ignored
				}
		}
	}

	/**
	 * Build edit list containing diffs of two revisions
	 *
	 * @param current
	 * @param next
	 * @return edit lits
	 */
	protected EditList buildDiffs(Revision current, Revision next) {
		RawText currentBytes = new RawText(current.getBytes());
		RawText nextBytes = new RawText(next.getBytes());
		HistogramDiff diff = new HistogramDiff();
		return diff.diff(RawTextComparator.DEFAULT, currentBytes, nextBytes);
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
		Deque<Revision> commits = new LinkedList<Revision>();
		RevWalk walk = new RevWalk(repository);
		walk.setRetainBody(true);
		walk.setTreeFilter(FollowFilter.create(this.path));
		try {
			ObjectId head = repository.resolve(Constants.HEAD);
			walk.markStart(walk.parseCommit(head));
			for (RevCommit commit : walk)
				commits.addFirst(new Revision().setCommit(commit));
		} finally {
			walk.release();
		}
		return commits;
	}

	/**
	 * Build history
	 *
	 * @return file history
	 * @throws IOException
	 */
	public RevisionContainer build() throws IOException {
		this.commitWalkTime = 0L;
		this.diffTime = 0L;
		this.parseTime = 0L;
		this.treeWalkTime = 0L;

		long start = System.currentTimeMillis();
		Deque<Revision> commits = buildRevisions();
		this.commitWalkTime += System.currentTimeMillis() - start;

		if (commits.isEmpty()) {
			return new RevisionContainer();
		}

		RevisionContainer file = new RevisionContainer();

		Iterator<Revision> iterator = commits.iterator();

		Revision current = iterator.next();
		file.addRevision(current);

		start = System.currentTimeMillis();
		current.setBlob(getBlob(current));
		treeWalkTime += System.currentTimeMillis() - start;

		start = System.currentTimeMillis();
		buildLines(current);
		parseTime += System.currentTimeMillis() - start;

		Revision revision = null;
		while (iterator.hasNext()) {
			revision = iterator.next();
			file.addRevision(revision);

			start = System.currentTimeMillis();
			revision.setBlob(getBlob(revision));
			treeWalkTime += System.currentTimeMillis() - start;

			start = System.currentTimeMillis();
			buildLines(revision);
			parseTime += System.currentTimeMillis() - start;

			start = System.currentTimeMillis();
			EditList edits = buildDiffs(current, revision);
			diffTime += System.currentTimeMillis() - start;
			mergeLines(current, revision, edits);
			current = revision;
		}
		return file;
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

	/**
	 * @return diffTime
	 */
	public long getDiffTime() {
		return this.diffTime;
	}

	/**
	 * @return commitWalkTime
	 */
	public long getCommitWalkTime() {
		return this.commitWalkTime;
	}

	/**
	 * @return treeWalkTime
	 */
	public long getTreeWalkTime() {
		return this.treeWalkTime;
	}

	/**
	 * @return parseTime
	 */
	public long getParseTime() {
		return this.parseTime;
	}
}
