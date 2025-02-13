/*
 * Copyright (C) 2025 Google LLC
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.blame.cache;

import java.io.Serializable;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;

/**
 * Region of the blame of a file.
 * <p>
 * Usually all parameters are non-null, except when the Region was created
 * to fill an unblamed gap (to cover for bugs in the calculation). In that
 * case, path, commit and author will be null.
 *
 * @since 7.2
 **/
public class CacheRegion implements Serializable, Comparable<CacheRegion> {
	private static final long serialVersionUID = 1L;

	private final String sourcePath;

	private final ObjectId sourceCommit;

	private final PersonIdent sourceAuthor;

	private final int count;

	private transient int start;

	/**
	 * A blamed portion of a file
	 *
	 * @param path
	 *            location of the file
	 * @param commit
	 *            commit that is modifying this region
	 * @param author
	 *            author for the commit modifying this region
	 * @param start
	 *            first line of this region (inclusive)
	 * @param end
	 *            last line of this region (non-inclusive!)
	 */
	public CacheRegion(String path, ObjectId commit, PersonIdent author,
			int start, int end) {
		allOrNoneNull(path, commit, author);
		this.sourcePath = path;
		this.sourceCommit = commit;
		this.sourceAuthor = author;
		this.start = start;
		this.count = end - start;
	}

	/**
	 * First line of this region. Starting by 0, inclusive
	 *
	 * @param start
	 *            first line of this region
	 */
	public void setStart(int start) {
		this.start = start;
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
		return start + count;
	}

	/**
	 * Length of this region.
	 *
	 * @return length of this region
	 */
	public int getCount() {
		return count;
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

	/**
	 * Author of this region
	 *
	 * @return person ident with the author/modification time of this region
	 */
	public PersonIdent getSourceAuthor() {
		return sourceAuthor;
	}

	@Override
	public int compareTo(CacheRegion o) {
		return start - o.start;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		if (sourceCommit != null) {
			sb.append(sourceCommit.name(), 0, 7).append(' ')
					.append(sourceAuthor.toExternalString()).append(" (")
					.append(sourcePath).append(')');
		} else {
			sb.append("<unblamed region>");
		}
		sb.append(' ').append("start=").append(start).append(", count=")
				.append(count);
		return sb.toString();
	}

	private static void allOrNoneNull(String path, ObjectId commit,
			PersonIdent author) {
		if (path != null && commit != null && author != null) {
			return;
		}

		if (path == null && commit == null && author == null) {
			return;
		}
		throw new IllegalArgumentException(String.format(
				"expected all null or none: %s, %s, %s", path, commit, author));
	}
}
