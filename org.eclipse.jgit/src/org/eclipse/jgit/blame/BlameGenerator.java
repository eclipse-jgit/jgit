/*
 * Copyright (C) 2011, Google Inc.
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

import org.eclipse.jgit.JGitText;
import org.eclipse.jgit.diff.DiffAlgorithm;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.diff.HistogramDiff;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.diff.RenameDetector;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.FollowFilter;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevFlag;
import org.eclipse.jgit.revwalk.RevSort;
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
 * repository must be traversed to gather information on every line.
 * <p>
 * Applications that want more incremental update behavior may use either the
 * raw {@link #next()} streaming approach supported by this class, or construct
 * a {@link BlameResult} using {@link BlameResult#create(BlameGenerator)} and
 * incrementally construct the result with {@link BlameResult#computeNext()}.
 * <p>
 * This class is not thread-safe.
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
 */
public class BlameGenerator {
	private final Repository repository;

	private final String resultPath;

	private DiffAlgorithm diffAlgorithm = new HistogramDiff();

	private RawTextComparator textComparator = RawTextComparator.DEFAULT;

	private boolean followFileRenames;

	private AnyObjectId start;

	private AnyObjectId end;

	/** Number of lines that still need to be discovered. */
	private int remaining;

	/** Active traversal between {@link #start} and {@link #end}. */
	private RevWalk walk;

	/** Commit currently being blamed for providing line data. */
	private RevCommit srcCommit;

	/** Path in {@link #srcCommit}, renamed may differ from {@link #resultPath}. */
	private String srcPath;

	/** Complete contents of {@link #srcPath} in {@link #srcCommit}. */
	private RawText srcText;

	/** Maps lines of {@link #srcText} to the starting source file. */
	private int[] srcLineMap;

	/** Differences between {@link #nextCommit} (A) and {@link #srcCommit} (B). */
	private EditList editList;

	/** Current position this class is exporting from {@link #editList}. */
	private int curEdit;

	private RevCommit nextCommit;

	private String nextPath;

	private RawText nextText;

	private int[] nextLineMap;

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
		this.resultPath = path;
	}

	/** @return repository being scanned for revision history. */
	public Repository getRepository() {
		return repository;
	}

	/** @return path file path being processed. */
	public String getResultPath() {
		return resultPath;
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
	public BlameGenerator setFollowFileRenames(boolean follow) {
		followFileRenames = follow;
		return this;
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
	 * Set ending commit id. If not set, blame digs back to file origin.
	 *
	 * @param commitId
	 * @return {@code this}
	 */
	public BlameGenerator setEndRevision(ObjectId commitId) {
		end = commitId;
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
		if (walk == null && !start()) {
			release();
			return false;
		}

		// Advance past the edit examined by the last iteration.
		curEdit++;

		// If there are no lines remaining, the entire result is done,
		// even if there are revisions still available for the path.
		if (remaining == 0) {
			release();
			return false;
		}

		for (;;) {
			if (curEdit == editList.size()) {
				// With no edits remaining obtain the next commit, or give up.
				if (!nextCommit())
					return false;
				continue;
			}

			Edit edit = editList.get(curEdit);
			int len = edit.getLengthB();
			if (len == 0) {
				// An empty B side indicates nothing was supplied.
				curEdit++;

			} else {
				// Blame has been assigned to the current source.
				remaining -= len;
				return true;
			}
		}
	}

	/** @return current revision being blamed. */
	public RevCommit getSourceCommit() {
		return srcCommit;
	}

	/** @return current author being blamed. */
	public PersonIdent getSourceAuthor() {
		return srcCommit.getAuthorIdent();
	}

	/** @return current committer being blamed. */
	public PersonIdent getSourceCommitter() {
		return srcCommit.getCommitterIdent();
	}

	/** @return path of the file being blamed. */
	public String getSourcePath() {
		return srcPath;
	}

	/**
	 * @return first line of the source data that has been blamed for the
	 *         current region. This is line number of where the region was added
	 *         during {@link #getSourceCommit()} in file
	 *         {@link #getSourcePath()}.
	 */
	public int getSourceStart() {
		return editList.get(curEdit).getBeginB();
	}

	/**
	 * @return one past the range of the source data that has been blamed for
	 *         the current region. This is line number of where the region was
	 *         added during {@link #getSourceCommit()} in file
	 *         {@link #getSourcePath()}.
	 */
	public int getSourceEnd() {
		return editList.get(curEdit).getEndB();
	}

	/**
	 * @return first line of the result that {@link #getSourceCommit()} has been
	 *         blamed for providing. Line numbers use 0 based indexing.
	 */
	public int getResultStart() {
		return srcLineMap[editList.get(curEdit).getBeginB()];
	}

	/**
	 * @return one past the range of the result that {@link #getSourceCommit()}
	 *         has been blamed for providing. Line numbers use 0 based indexing.
	 *         Because a source cannot be blamed for an empty region of the
	 *         result, {@link #getResultEnd()} is always at least one larger
	 *         than {@link #getResultStart()}.
	 */
	public int getResultEnd() {
		return srcLineMap[editList.get(curEdit).getEndB() - 1] + 1;
	}

	/**
	 * @return number of lines in the current region being blamed to
	 *         {@link #getSourceCommit()}. This is always the value of the
	 *         expression {@code getResultEnd() - getResultStart()}, but also
	 *         {@code getSourceEnd() - getSourceStart()}.
	 */
	public int getRegionLength() {
		return editList.get(curEdit).getLengthB();
	}

	/**
	 * @return complete contents of the source file blamed for the current
	 *         output region. This is the contents of {@link #getSourcePath()}
	 *         within {@link #getSourceCommit()}. The source contents is
	 *         temporarily available as an artifact of the blame algorithm. Most
	 *         applications will want the result contents for display to users.
	 */
	public RawText getSourceContents() {
		return srcText;
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
		if (walk == null)
			start();

		// After start() but before next() srcCommit should be null and
		// nextCommit has the revision of the result.
		if (srcCommit != null)
			throw new IllegalStateException(JGitText.get().blameHasAlreadyBeenStarted);
		return nextCommit;
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
		if (walk == null && !start())
			return null;

		// After start() but before next() srcCommit should be null
		// and nextText has the contents of the result file.
		if (srcCommit != null)
			throw new IllegalStateException(JGitText.get().blameHasAlreadyBeenStarted);
		return nextText;
	}

	private boolean start() throws IOException {
		walk = new RevWalk(repository);
		walk.setRetainBody(true);

		if (followFileRenames)
			walk.setTreeFilter(FollowFilter.create(resultPath));
		else
			walk.setTreeFilter(PathFilter.create(resultPath));

		AnyObjectId head = start != null ? start : repository.resolve(Constants.HEAD);
		if (head == null)
			return false;
		walk.markStart(walk.parseCommit(head));
		if (end != null) {
			walk.markUninteresting(walk.parseCommit(end));
			walk.sort(RevSort.BOUNDARY, true);
		}

		nextCommit = walk.next();
		nextPath = resultPath;
		if (nextCommit == null)
			return false;

		ObjectId blob = getBlob(nextCommit, nextPath);
		if (blob == null)
			return false;

		nextText = getRawText(blob);
		remaining = nextText.size();

		// The initial line map has to be an identity mapping. The values will
		// be copied into older maps as successive ancestors are blamed for
		// some region of the starting file.
		nextLineMap = new int[nextText.size()];
		for (int i = 0; i < nextLineMap.length; i++)
			nextLineMap[i] = i;

		// Setup an empty edit list, and begin at -1. This will force the next()
		// call above to immediately pop one more commit from the RevWalk
		// and try to assign some blame to a region of the result.
		curEdit = -1;
		editList = new EditList();
		return true;
	}

	private boolean nextCommit() throws IOException {
		RevCommit n = walk.next();
		if (n == null && nextCommit == null)
			return false;

		srcCommit = nextCommit;
		srcPath = nextPath;
		srcText = nextText;
		srcLineMap = nextLineMap;

		if (n != null) {
			if (n.getParentCount() == 0)
				n.add(RevFlag.UNINTERESTING);

			nextCommit = n;
			nextPath = srcPath;
			ObjectId nextBlob = getBlob(nextCommit, nextPath);
			if (nextBlob == null) {
				if (!followFileRenames)
					return false;

				nextPath = wasPathRenamed(nextCommit);
				if (nextPath == null)
					return false;

				nextBlob = getBlob(nextCommit, nextPath);
				if (nextBlob == null)
					return false;
			}

			nextText = getRawText(nextBlob);
			nextLineMap = new int[nextText.size()];
			editList = diffAlgorithm.diff(textComparator, nextText, srcText);
			updateLineMap(editList, nextLineMap, srcLineMap);
		} else {
			// Special case the root (or boundary) to create the file.
			nextCommit = null;
			editList = EditList.singleton(new Edit(0, 0, 0, srcLineMap.length));
		}
		curEdit = 0;
		return true;
	}

	private static void updateLineMap(EditList editList, int[] aMap, int[] bMap) {
		int aIdx = 0;
		int bIdx = 0;

		for (Edit edit : editList) {
			// Anything before this edit is common, map from B, thereby blaming
			// A (or one of its ancestors) for this region of the file.
			while (aIdx < edit.getBeginA())
				aMap[aIdx++] = bMap[bIdx++];

			// For any part of the A side of this edit, there are no matching B
			// lines. Populate with -1 to indicate this part of the map has no
			// meaning in the original source file context and then jump past
			// what is left of B for the next edit.
			for (; aIdx < edit.getEndA(); aIdx++)
				aMap[aIdx] = -1;
			bIdx = edit.getEndB();
		}

		// Everything after the last edit (if still in A's bounds) is common.
		while (aIdx < aMap.length)
			aMap[aIdx++] = bMap[bIdx++];
	}

	/**
	 * Release the current blame session started from calling {@link #start()}
	 *
	 * @return this generator
	 */
	public BlameGenerator release() {
		srcCommit = null;
		srcPath = null;
		srcText = null;
		srcLineMap = null;

		editList = null;

		nextCommit = null;
		nextPath = null;
		nextText = null;
		nextLineMap = null;

		if (walk != null) {
			try {
				walk.release();
			} finally {
				walk = null;
			}
		}
		return this;
	}

	private ObjectId getBlob(RevCommit rev, String path) throws IOException {
		ObjectReader reader = walk.getObjectReader();
		TreeWalk tw = TreeWalk.forPath(reader, path, rev.getTree());
		if (tw == null)
			return null;
		if (tw.getFileMode(0).getObjectType() != Constants.OBJ_BLOB)
			return null;
		return tw.getObjectId(0);
	}

	private RawText getRawText(ObjectId blob) throws IOException {
		ObjectLoader loader = repository.open(blob, Constants.OBJ_BLOB);
		return new RawText(loader.getCachedBytes(Integer.MAX_VALUE));
	}

	private String wasPathRenamed(RevCommit rev) throws IOException {
		TreeWalk tw = new TreeWalk(walk.getObjectReader());
		try {
			tw.setFilter(TreeFilter.ANY_DIFF);
			tw.reset(rev.getTree(), srcCommit.getTree());
			RenameDetector detector = new RenameDetector(repository);
			detector.addAll(DiffEntry.scan(tw));
			for (DiffEntry ent : detector.compute()) {
				if (isRename(ent) && ent.getNewPath().equals(srcPath))
					return ent.getOldPath();
			}
			return null;
		} finally {
			tw.release();
		}
	}

	private boolean isRename(DiffEntry ent) {
		return ent.getChangeType() == ChangeType.RENAME
				|| ent.getChangeType() == ChangeType.COPY;
	}
}
