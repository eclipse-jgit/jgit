/*
 * Copyright (C) 2011, Google Inc.
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

import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import java.io.IOException;

import org.eclipse.jgit.JGitText;
import org.eclipse.jgit.diff.DiffAlgorithm;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.diff.HistogramDiff;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.diff.RenameDetector;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.MutableObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevFlag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.treewalk.filter.TreeFilter;

/**
 * Generate author information for lines based on introduction to the file.
 * <p>
 * Applications that want a simple one-shot computation of blame for a file
 * should use {@link #computeBlameResult()} to prepare the entire result in one
 * method call. This may block for significant time as the history of the
 * repository must be traversed until information is gathered for every line.
 * <p>
 * Applications that want more incremental update behavior may use either the
 * raw {@link #next()} streaming approach supported by this class, or construct
 * a {@link BlameResult} using {@link BlameResult#create(BlameGenerator)} and
 * incrementally construct the result with {@link BlameResult#computeNext()}.
 * <p>
 * This class is not thread-safe.
 * <p>
 * An instance of BlameGenerator can only be used once. To blame multiple files
 * the application must create a new BlameGenerator.
 * <p>
 * During blame processing there are two files involved:
 * <ul>
 * <li>result - The file whose lines are being examined. This is the revision
 * the user is trying to view blame/annotation information alongside of.</li>
 * <li>source - The file that was blamed with supplying one or more lines of
 * data into result. The source may be a different file path (due to copy or
 * rename). Source line numbers may differ from result line numbers due to lines
 * being added/removed in intermediate revisions.</li>
 * </ul>
 * <p>
 * The blame algorithm is implemented by initially assigning responsibility for
 * all lines of the result to the starting commit. A difference against the
 * commit's ancestor is computed, and responsibility is passed to the ancestor
 * commit for any lines that are common. The starting commit is blamed only for
 * the lines that do not appear in the ancestor, if any. The loop repeats using
 * the ancestor, until there are no more lines to acquire information on, or the
 * file's creation point is discovered in history.
 */
public class BlameGenerator {
	private final Repository repository;

	private final PathFilter resultPath;

	/** Revision pool used to acquire commits from. */
	private final RevWalk revPool;

	/** Indicates the commit has already been processed. */
	private final RevFlag SEEN;

	private final ObjectReader reader;

	private final TreeWalk treeWalk;

	private final MutableObjectId idBuf;

	private DiffAlgorithm diffAlgorithm = new HistogramDiff();

	private RawTextComparator textComparator = RawTextComparator.DEFAULT;

	private RenameDetector renameDetector;

	private AnyObjectId start;

	/** True if the generator has not yet started.  */
	private boolean notStarted;

	/** Potential candidates, sorted by commit time descending. */
	private Candidate queue;

	/** Number of lines that still need to be discovered. */
	private int remaining;

	/** Blame is currently assigned to this source. */
	private Candidate currentSource;

	/**
	 * Create a blame generator for the repository and path
	 *
	 * @param repository
	 *            repository to access revision data from.
	 * @param path
	 *            initial path of the file to start scanning.
	 */
	public BlameGenerator(Repository repository, String path) {
		this.repository = repository;
		this.resultPath = PathFilter.create(path);

		revPool = new RevWalk(repository);
		revPool.setRetainBody(true);
		SEEN = revPool.newFlag("SEEN");
		reader = revPool.getObjectReader();
		treeWalk = new TreeWalk(reader);
		idBuf = new MutableObjectId();
		setFollowFileRenames(true);

		remaining = -1;
		notStarted = true;
	}

	/** @return repository being scanned for revision history. */
	public Repository getRepository() {
		return repository;
	}

	/** @return path file path being processed. */
	public String getResultPath() {
		return resultPath.getPath();
	}

	/**
	 * Set starting commit id. If not set, HEAD is assumed.
	 *
	 * @param commitId
	 * @return {@code this}
	 */
	public BlameGenerator setStartRevision(ObjectId commitId) {
		start = commitId;
		return this;
	}

	/**
	 * Difference algorithm to use when comparing revisions.
	 *
	 * @param algorithm
	 * @return {@code this}
	 */
	public BlameGenerator setDiffAlgorithm(DiffAlgorithm algorithm) {
		diffAlgorithm = algorithm;
		return this;
	}

	/**
	 * Text comparator to use when comparing revisions.
	 *
	 * @param comparator
	 * @return {@code this}
	 */
	public BlameGenerator setTextComparator(RawTextComparator comparator) {
		textComparator = comparator;
		return this;
	}

	/**
	 * Enable (or disable) following file renames, on by default.
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
	public BlameGenerator setFollowFileRenames(boolean follow) {
		if (follow)
			renameDetector = new RenameDetector(getRepository());
		else
			renameDetector = null;
		return this;
	}

	/**
	 * Obtain the RenameDetector if {@code setFollowFileRenames(true)}.
	 *
	 * @return the rename detector, allowing the application to configure its
	 *         settings for rename score and breaking behavior.
	 */
	public RenameDetector getRenameDetector() {
		return renameDetector;
	}

	/**
	 * Execute the generator in a blocking fashion until all data is ready.
	 *
	 * @return the complete result. Null if no file exists for the given path.
	 * @throws IOException
	 *             the repository cannot be read.
	 */
	public BlameResult computeBlameResult() throws IOException {
		try {
			BlameResult r = BlameResult.create(this);
			if (r != null)
				r.computeAll();
			return r;
		} finally {
			release();
		}
	}

	/**
	 * Step the blame algorithm one iteration.
	 *
	 * @return true if there is at least one more revision to consider. False if
	 *         blame is done executing and has no more lines to describe.
	 * @throws IOException
	 *             repository cannot be read.
	 */
	public boolean next() throws IOException {
		// If there is a source still pending, produce the next region.
		if (currentSource != null) {
			Region r = currentSource.regionList;
			Region n = r.next;
			remaining -= r.length;
			if (n != null) {
				currentSource.regionList = n;
				return true;
			}
			currentSource = null;
		}

		// If there are no lines remaining, the entire result is done,
		// even if there are revisions still available for the path.
		if (remaining == 0)
			return done();

		for (;;) {
			Candidate n = pop();
			if (n == null) {
				if (notStarted && start()) {
					notStarted = false;
					continue;
				}
				return done();
			}

			RevCommit source = n.sourceCommit;
			int pCnt = source.getParentCount();
			if (pCnt == 1) {
				if (processOne(n))
					return true;

			} else if (1 < pCnt) {
				if (processMerge(n))
					return true;

			} else /* if (pCnt == 0) */{
				// Root commit, with at least one surviving region.
				// Assign the remaining blame here.
				return result(n);
			}
		}
	}

	private boolean done() {
		release();
		return false;
	}

	private boolean result(Candidate n) throws IOException {
		revPool.parseBody(n.sourceCommit);
		currentSource = n;
		return true;
	}

	private Candidate pop() {
		Candidate n = queue;
		if (n != null)
			queue = n.queueNext;
		return n;
	}

	private void push(Candidate toInsert) {
		// Mark sources to ensure they get discarded (above) if
		// another path to the same commit.
		toInsert.sourceCommit.add(SEEN);

		// Insert into the queue using descending commit time, so
		// the most recent commit will pop next.
		int time = toInsert.sourceCommit.getCommitTime();
		Candidate n = queue;
		if (n == null || time >= n.sourceCommit.getCommitTime()) {
			toInsert.queueNext = n;
			queue = toInsert;
			return;
		}

		for (Candidate p = n;; p = n) {
			n = p.queueNext;
			if (n == null || time >= n.sourceCommit.getCommitTime()) {
				toInsert.queueNext = n;
				p.queueNext = toInsert;
				return;
			}
		}
	}

	private boolean processOne(Candidate n) throws IOException {
		RevCommit source = n.sourceCommit;
		RevCommit parent = source.getParent(0);
		if (parent.has(SEEN))
			return false;
		revPool.parseHeaders(parent);

		if (find(parent, n.sourcePath)) {
			if (idBuf.equals(n.sourceBlob)) {
				// The common case of the file not being modified in
				// a simple string-of-pearls history. Blame parent.
				n.sourceCommit = parent;
				push(n);
				return false;
			}

			Candidate next = new Candidate(parent, n.sourcePath);
			next.sourceBlob = idBuf.toObjectId();
			next.loadText(reader);
			return split(next, n);
		}

		DiffEntry r = findRename(parent, source, n.sourcePath);
		if (r == null)
			return result(n);

		if (0 == r.getOldId().prefixCompare(n.sourceBlob)) {
			// A 100% rename without any content change can also
			// skip directly to the parent.
			n.sourceCommit = parent;
			n.sourcePath = PathFilter.create(r.getOldPath());
			push(n);
			return false;
		}

		Candidate next = new Candidate(parent, PathFilter.create(r.getOldPath()));
		next.sourceBlob = r.getOldId().toObjectId();
		next.renameScore = r.getScore();
		next.loadText(reader);
		return split(next, n);
	}

	private boolean processMerge(Candidate n) throws IOException {
		RevCommit source = n.sourceCommit;
		int pCnt = source.getParentCount();

		for (int pIdx = 0; pIdx < pCnt; pIdx++) {
			RevCommit parent = source.getParent(pIdx);
			if (parent.has(SEEN))
				continue;
			revPool.parseHeaders(parent);
		}

		// If any single parent exactly matches the merge, follow only
		// that one parent through history.
		ObjectId[] ids = new ObjectId[pCnt];
		for (int pIdx = 0; pIdx < pCnt; pIdx++) {
			RevCommit parent = source.getParent(pIdx);
			if (parent.has(SEEN))
				continue;
			if (!find(parent, n.sourcePath))
				continue;
			if (idBuf.equals(n.sourceBlob)) {
				n.sourceCommit = parent;
				push(n);
				return false;
			}
			ids[pIdx] = idBuf.toObjectId();
		}

		// If rename detection is enabled, search for any relevant names.
		DiffEntry[] renames = null;
		if (renameDetector != null) {
			renames = new DiffEntry[pCnt];
			for (int pIdx = 0; pIdx < pCnt; pIdx++) {
				RevCommit parent = source.getParent(pIdx);
				if (parent.has(SEEN))
					continue;
				DiffEntry r = findRename(parent, source, n.sourcePath);
				if (r == null)
					continue;

				if (0 == r.getOldId().prefixCompare(n.sourceBlob)) {
					// A 100% rename without any content change can also
					// skip directly to the parent.
					n.sourceCommit = parent;
					n.sourcePath = PathFilter.create(r.getOldPath());
					push(n);
					return false;
				}

				renames[pIdx] = r;
			}
		}

		// Construct the candidate for each parent.
		Candidate[] parents = new Candidate[pCnt];
		for (int pIdx = 0; pIdx < pCnt; pIdx++) {
			RevCommit parent = source.getParent(pIdx);
			if (parent.has(SEEN))
				continue;
			Candidate p;

			if (renames != null && renames[pIdx] != null) {
				p = new Candidate(parent,
						PathFilter.create(renames[pIdx].getOldPath()));
				p.renameScore = renames[pIdx].getScore();
				p.sourceBlob = renames[pIdx].getOldId().toObjectId();
			} else if (ids[pIdx] != null) {
				p = new Candidate(parent, n.sourcePath);
				p.sourceBlob = ids[pIdx];
			} else {
				continue;
			}
			p.loadText(reader);

			EditList editList = diffAlgorithm.diff(textComparator,
					p.sourceText, n.sourceText);
			if (editList.isEmpty()) {
				// Ignoring whitespace (or some other special comparator) can
				// cause non-identical blobs to have an empty edit list. In
				// a case like this push the parent alone.
				p.regionList = n.regionList;
				push(p);
				return false;
			}

			p.takeBlame(editList, n);

			// Only remember this parent candidate if there is at least
			// one region that was blamed on the parent.
			if (p.regionList != null)
				parents[pIdx] = p;
		}

		// Push any parents that are still candidates.
		for (int pIdx = 0; pIdx < pCnt; pIdx++) {
			if (parents[pIdx] != null)
				push(parents[pIdx]);
		}

		// If there are any regions surviving, they do not exist in any
		// parent and thus belong to the merge itself.
		if (n.regionList != null)
			return result(n);
		return false;
	}

	private boolean split(Candidate parent, Candidate source)
			throws IOException {
		EditList editList = diffAlgorithm.diff(textComparator,
				parent.sourceText, source.sourceText);
		if (editList.isEmpty()) {
			// Ignoring whitespace (or some other special comparator) can
			// cause non-identical blobs to have an empty edit list. In
			// a case like this push the parent alone.
			parent.regionList = source.regionList;
			push(parent);
			return false;
		}

		parent.takeBlame(editList, source);
		if (parent.regionList != null)
			push(parent);
		if (source.regionList != null)
			return result(source);
		return false;
	}

	/** @return current revision being blamed. */
	public RevCommit getSourceCommit() {
		return currentSource.sourceCommit;
	}

	/** @return current author being blamed. */
	public PersonIdent getSourceAuthor() {
		return getSourceCommit().getAuthorIdent();
	}

	/** @return current committer being blamed. */
	public PersonIdent getSourceCommitter() {
		return getSourceCommit().getCommitterIdent();
	}

	/** @return path of the file being blamed. */
	public String getSourcePath() {
		return currentSource.sourcePath.getPath();
	}

	/** @return rename score if a rename occurred in {@link #getSourceCommit}. */
	public int getRenameScore() {
		return currentSource.renameScore;
	}

	/**
	 * @return first line of the source data that has been blamed for the
	 *         current region. This is line number of where the region was added
	 *         during {@link #getSourceCommit()} in file
	 *         {@link #getSourcePath()}.
	 */
	public int getSourceStart() {
		return currentSource.regionList.sourceStart;
	}

	/**
	 * @return one past the range of the source data that has been blamed for
	 *         the current region. This is line number of where the region was
	 *         added during {@link #getSourceCommit()} in file
	 *         {@link #getSourcePath()}.
	 */
	public int getSourceEnd() {
		Region r = currentSource.regionList;
		return r.sourceStart + r.length;
	}

	/**
	 * @return first line of the result that {@link #getSourceCommit()} has been
	 *         blamed for providing. Line numbers use 0 based indexing.
	 */
	public int getResultStart() {
		return currentSource.regionList.resultStart;
	}

	/**
	 * @return one past the range of the result that {@link #getSourceCommit()}
	 *         has been blamed for providing. Line numbers use 0 based indexing.
	 *         Because a source cannot be blamed for an empty region of the
	 *         result, {@link #getResultEnd()} is always at least one larger
	 *         than {@link #getResultStart()}.
	 */
	public int getResultEnd() {
		Region r = currentSource.regionList;
		return r.resultStart + r.length;
	}

	/**
	 * @return number of lines in the current region being blamed to
	 *         {@link #getSourceCommit()}. This is always the value of the
	 *         expression {@code getResultEnd() - getResultStart()}, but also
	 *         {@code getSourceEnd() - getSourceStart()}.
	 */
	public int getRegionLength() {
		return currentSource.regionList.length;
	}

	/**
	 * @return complete contents of the source file blamed for the current
	 *         output region. This is the contents of {@link #getSourcePath()}
	 *         within {@link #getSourceCommit()}. The source contents is
	 *         temporarily available as an artifact of the blame algorithm. Most
	 *         applications will want the result contents for display to users.
	 */
	public RawText getSourceContents() {
		return currentSource.sourceText;
	}

	/**
	 * @return revision blame will produce results for. This value is accessible
	 *         only after being configured and only immediately before the first
	 *         call to {@link #next()}.
	 * @throws IOException
	 *             repository cannot be read.
	 * @throws IllegalStateException
	 *             {@link #next()} has already been invoked.
	 */
	public RevCommit getResultCommit() throws IOException {
		if (notStarted) {
			if (start())
				notStarted = false;
			else
				return null;
		}

		Candidate c = queue;
		if (c.queueNext != null || remaining != c.sourceText.size())
			throw new IllegalStateException(JGitText.get().blameHasAlreadyBeenStarted);
		return c.sourceCommit;
	}

	/**
	 * @return complete file contents of the result file blame is annotating.
	 *         This value is accessible only after being configured and only
	 *         immediately before the first call to {@link #next()}. Returns
	 *         null if the path does not exist.
	 * @throws IOException
	 *             repository cannot be read.
	 * @throws IllegalStateException
	 *             {@link #next()} has already been invoked.
	 */
	public RawText getResultContents() throws IOException {
		if (notStarted) {
			if (start())
				notStarted = false;
			else
				return null;
		}

		Candidate c = queue;
		if (c.queueNext != null || remaining != c.sourceText.size())
			throw new IllegalStateException(JGitText.get().blameHasAlreadyBeenStarted);
		return c.sourceText;
	}

	private boolean start() throws IOException {
		RevCommit commit;
		if (start != null) {
			commit = revPool.parseCommit(start);
		} else {
			AnyObjectId head = repository.resolve(Constants.HEAD);
			if (head == null)
				return false;
			commit = revPool.parseCommit(head);
		}

		if (!find(commit, resultPath))
			return false;

		Candidate toInsert = new Candidate(commit, resultPath);
		toInsert.sourceBlob = idBuf.toObjectId();
		toInsert.loadText(reader);
		toInsert.regionList = new Region(0, 0, toInsert.sourceText.size());
		remaining = toInsert.sourceText.size();
		queue = toInsert;
		currentSource = null;
		commit.add(SEEN);
		return true;
	}

	/**
	 * Release the current blame session started from calling {@link #start()}
	 *
	 * @return this generator
	 */
	public BlameGenerator release() {
		revPool.release();
		queue = null;
		currentSource = null;
		return this;
	}

	private boolean find(RevCommit commit, PathFilter path) throws IOException {
		treeWalk.setFilter(path);
		treeWalk.reset(commit.getTree());
		while (treeWalk.next()) {
			if (path.isDone(treeWalk)) {
				if (treeWalk.getFileMode(0).getObjectType() != OBJ_BLOB)
					return false;
				treeWalk.getObjectId(idBuf, 0);
				return true;
			}

			if (treeWalk.isSubtree())
				treeWalk.enterSubtree();
		}
		return false;
	}

	private DiffEntry findRename(RevCommit parent, RevCommit commit,
			PathFilter path) throws IOException {
		if (renameDetector == null)
			return null;

		treeWalk.setFilter(TreeFilter.ANY_DIFF);
		treeWalk.reset(parent.getTree(), commit.getTree());
		renameDetector.addAll(DiffEntry.scan(treeWalk));
		for (DiffEntry ent : renameDetector.compute()) {
			if (isRename(ent) && ent.getNewPath().equals(path.getPath()))
				return ent;
		}
		return null;
	}

	private static boolean isRename(DiffEntry ent) {
		return ent.getChangeType() == ChangeType.RENAME
				|| ent.getChangeType() == ChangeType.COPY;
	}
}
