/*
 * Copyright (C) 2025, Google LLC.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.blame.cache;

import java.text.MessageFormat;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.ObjectId;

/**
 * Region of the blame of a file.
 * <p>
 * Usually all parameters are non-null, except when the Region was created
 * to fill an unblamed gap (to cover for bugs in the calculation). In that
 * case, path, commit and author will be null.
 *
 * @since 7.2
 **/
public class CacheRegion implements Comparable<CacheRegion> {
	private final String sourcePath;

	private final ObjectId sourceCommit;

	private final int end;

	private final int start;

	/**
	 * A blamed portion of a file
	 *
	 * @param path
	 *            location of the file
	 * @param commit
	 *            commit that is modifying this region
	 * @param start
	 *            first line of this region (inclusive)
	 * @param end
	 *            last line of this region (non-inclusive!)
	 */
	public CacheRegion(String path, ObjectId commit,
			int start, int end) {
		allOrNoneNull(path, commit);
		this.sourcePath = path;
		this.sourceCommit = commit;
		this.start = start;
		this.end = end;
	}

	/**
	 * First line of this region. Starting by 0, inclusive
	 *
	 * @return first line of this region.
	 */
	public int getStart() {
		return start;
	}

	/**
	 * One after last line in this region (or: last line non-inclusive)
	 *
	 * @return one after last line in this region.
	 */
	public int getEnd() {
		return end;
	}


	/**
	 * Path of the file this region belongs to
	 *
	 * @return path in the repo/commit
	 */
	public String getSourcePath() {
		return sourcePath;
	}

	/**
	 * Commit this region belongs to
	 *
	 * @return commit for this region
	 */
	public ObjectId getSourceCommit() {
		return sourceCommit;
	}

	@Override
	public int compareTo(CacheRegion o) {
		return start - o.start;
	}

	@SuppressWarnings("nls")
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		if (sourceCommit != null) {
			sb.append(sourceCommit.name(), 0, 7).append(' ')
					.append(" (")
					.append(sourcePath).append(')');
		} else {
			sb.append("<unblamed region>");
		}
		sb.append(' ').append("start=").append(start).append(", count=")
				.append(end - start);
		return sb.toString();
	}

	private static void allOrNoneNull(String path, ObjectId commit) {
		if (path != null && commit != null) {
			return;
		}

		if (path == null && commit == null) {
			return;
		}
		throw new IllegalArgumentException(MessageFormat
				.format(JGitText.get().cacheRegionAllOrNoneNull, path, commit));
	}
}
