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

/** Region of the blame of a file. */
public class CacheRegion implements Serializable, Comparable<CacheRegion> {
	private static final long serialVersionUID = 1L;

	private final String sourcePath;

	private final ObjectId sourceCommit;

	private final PersonIdent sourceAuthor;

	private final int count;

	private transient int start;

	public CacheRegion(String path, ObjectId commit, PersonIdent author,
			int start, int end) {
		if (!(path != null && commit != null && author != null)
				&& !(path == null && commit == null && author == null)) {
			throw new IllegalArgumentException(
					String.format("expected all null or none: %s, %s, %s", path,
							commit, author));
		}
		this.sourcePath = path;
		this.sourceCommit = commit;
		this.sourceAuthor = author;
		this.start = start;
		this.count = end - start;
	}

	public void setStart(int start) {
		this.start = start;
	}

	public int getStart() {
		return start;
	}

	public int getEnd() {
		return start + count;
	}

	public int getCount() {
		return count;
	}

	public String getSourcePath() {
		return sourcePath;
	}

	public ObjectId getSourceCommit() {
		return sourceCommit;
	}

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
}
