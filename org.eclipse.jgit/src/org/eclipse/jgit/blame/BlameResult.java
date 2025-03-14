/*
 * Copyright (C) 2011, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.blame;

import java.io.IOException;

import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;

/**
 * Collects line annotations for inspection by applications.
 * <p>
 * A result is usually updated incrementally as the BlameGenerator digs back
 * further through history. Applications that want to lay annotations down text
 * to the original source file in a viewer may find the BlameResult structure an
 * easy way to acquire the information, at the expense of keeping tables in
 * memory tracking every line of the result file.
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
public class BlameResult {
	/**
	 * Construct a new BlameResult for a generator.
	 *
	 * @param gen
	 *            the generator the result will consume records from.
	 * @return the new result object. null if the generator cannot find the path
	 *         it starts from.
	 * @throws java.io.IOException
	 *             the repository cannot be read.
	 */
	public static BlameResult create(BlameGenerator gen) throws IOException {
		String path = gen.getResultPath();
		RawText contents = gen.getResultContents();
		if (contents == null) {
			gen.close();
			return null;
		}
		return new BlameResult(gen, path, contents);
	}

	private final String resultPath;

	private final RevCommit[] sourceCommits;

	private final PersonIdent[] sourceAuthors;

	private final PersonIdent[] sourceCommitters;

	private final String[] sourcePaths;

	/** Warning: these are actually 1-based. */
	private final int[] sourceLines;

	private RawText resultContents;

	private BlameGenerator generator;

	private int lastLength;

	BlameResult(BlameGenerator bg, String path, RawText text) {
		generator = bg;
		generator.setUseCache(false);
		resultPath = path;
		resultContents = text;

		int cnt = text.size();
		sourceCommits = new RevCommit[cnt];
		sourceAuthors = new PersonIdent[cnt];
		sourceCommitters = new PersonIdent[cnt];
		sourceLines = new int[cnt];
		sourcePaths = new String[cnt];
	}

	/**
	 * Get result path
	 *
	 * @return path of the file this result annotates
	 */
	public String getResultPath() {
		return resultPath;
	}

	/**
	 * Get result contents
	 *
	 * @return contents of the result file, available for display
	 */
	public RawText getResultContents() {
		return resultContents;
	}

	/**
	 * Throw away the {@link #getResultContents()}.
	 */
	public void discardResultContents() {
		resultContents = null;
	}

	/**
	 * Check if the given result line has been annotated yet.
	 *
	 * @param idx
	 *            line to read data of, 0 based.
	 * @return true if the data has been annotated, false otherwise.
	 */
	public boolean hasSourceData(int idx) {
		return sourceLines[idx] != 0;
	}

	/**
	 * Check if the given result line has been annotated yet.
	 *
	 * @param start
	 *            first index to examine.
	 * @param end
	 *            last index to examine.
	 * @return true if the data has been annotated, false otherwise.
	 */
	public boolean hasSourceData(int start, int end) {
		for (; start < end; start++)
			if (sourceLines[start] == 0)
				return false;
		return true;
	}

	/**
	 * Get the commit that provided the specified line of the result.
	 * <p>
	 * The source commit may be null if the line was blamed to an uncommitted
	 * revision, such as the working tree copy, or during a reverse blame if the
	 * line survives to the end revision (e.g. the branch tip).
	 *
	 * @param idx
	 *            line to read data of, 0 based.
	 * @return commit that provided line {@code idx}. May be null.
	 */
	public RevCommit getSourceCommit(int idx) {
		return sourceCommits[idx];
	}

	/**
	 * Get the author that provided the specified line of the result.
	 *
	 * @param idx
	 *            line to read data of, 0 based.
	 * @return author that provided line {@code idx}. May be null.
	 */
	public PersonIdent getSourceAuthor(int idx) {
		return sourceAuthors[idx];
	}

	/**
	 * Get the committer that provided the specified line of the result.
	 *
	 * @param idx
	 *            line to read data of, 0 based.
	 * @return committer that provided line {@code idx}. May be null.
	 */
	public PersonIdent getSourceCommitter(int idx) {
		return sourceCommitters[idx];
	}

	/**
	 * Get the file path that provided the specified line of the result.
	 *
	 * @param idx
	 *            line to read data of, 0 based.
	 * @return source file path that provided line {@code idx}.
	 */
	public String getSourcePath(int idx) {
		return sourcePaths[idx];
	}

	/**
	 * Get the corresponding line number in the source file.
	 *
	 * @param idx
	 *            line to read data of, 0 based.
	 * @return matching line number in the source file.
	 */
	public int getSourceLine(int idx) {
		return sourceLines[idx] - 1;
	}

	/**
	 * Compute all pending information.
	 *
	 * @throws java.io.IOException
	 *             the repository cannot be read.
	 */
	public void computeAll() throws IOException {
		BlameGenerator gen = generator;
		if (gen == null)
			return;

		try {
			while (gen.next())
				loadFrom(gen);
		} finally {
			gen.close();
			generator = null;
		}
	}

	/**
	 * Compute the next available segment and return the first index.
	 * <p>
	 * Computes one segment and returns to the caller the first index that is
	 * available. After return the caller can also inspect {@link #lastLength()}
	 * to determine how many lines of the result were computed.
	 *
	 * @return index that is now available. -1 if no more are available.
	 * @throws java.io.IOException
	 *             the repository cannot be read.
	 */
	public int computeNext() throws IOException {
		BlameGenerator gen = generator;
		if (gen == null) {
			return -1;
		}

		if (gen.next()) {
			loadFrom(gen);
			lastLength = gen.getRegionLength();
			return gen.getResultStart();
		}
		gen.close();
		generator = null;
		return -1;
	}

	/**
	 * Get last length
	 *
	 * @return length of the last segment found by {@link #computeNext()}
	 */
	public int lastLength() {
		return lastLength;
	}

	/**
	 * Compute until the entire range has been populated.
	 *
	 * @param start
	 *            first index to examine (inclusive).
	 * @param end
	 *            end index (exclusive).
	 * @throws java.io.IOException
	 *             the repository cannot be read.
	 */
	public void computeRange(int start, int end) throws IOException {
		BlameGenerator gen = generator;
		if (gen == null)
			return;
		if (start == 0 && end == resultContents.size()) {
			computeAll();
			return;
		}

		while (start < end) {
			if (hasSourceData(start, end))
				return;

			if (!gen.next()) {
				gen.close();
				generator = null;
				return;
			}

			loadFrom(gen);

			// If the result contains either end of our current range bounds,
			// update the bounds to avoid scanning that section during the
			// next loop iteration.

			int resLine = gen.getResultStart();
			int resEnd = gen.getResultEnd();

			if (resLine <= start && start < resEnd)
				start = resEnd;

			if (resLine <= end && end < resEnd)
				end = resLine;
		}
	}

	@Override
	public String toString() {
		StringBuilder r = new StringBuilder();
		r.append("BlameResult: "); //$NON-NLS-1$
		r.append(getResultPath());
		return r.toString();
	}

	private void loadFrom(BlameGenerator gen) {
		RevCommit srcCommit = gen.getSourceCommit();
		PersonIdent srcAuthor = gen.getSourceAuthor();
		PersonIdent srcCommitter = gen.getSourceCommitter();
		String srcPath = gen.getSourcePath();
		int srcLine = gen.getSourceStart();
		int resLine = gen.getResultStart();
		int resEnd = gen.getResultEnd();

		for (; resLine < resEnd; resLine++) {
			// Reverse blame can generate multiple results for the same line.
			// Favor the first one selected, as this is the oldest and most
			// likely to be nearest to the inquiry made by the user.
			if (sourceLines[resLine] != 0)
				continue;

			sourceCommits[resLine] = srcCommit;
			sourceAuthors[resLine] = srcAuthor;
			sourceCommitters[resLine] = srcCommitter;
			sourcePaths[resLine] = srcPath;

			// Since sourceLines is 1-based to permit hasSourceData to use 0 to
			// mean the line has not been annotated yet, pre-increment instead
			// of the traditional post-increment when making the assignment.
			sourceLines[resLine] = ++srcLine;
		}
	}
}
