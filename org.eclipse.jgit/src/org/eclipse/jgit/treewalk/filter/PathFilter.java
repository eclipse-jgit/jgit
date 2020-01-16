/*
 * Copyright (C) 2008, Google Inc.
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.treewalk.filter;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.treewalk.TreeWalk;

/**
 * Includes tree entries only if they match the configured path.
 * <p>
 * Applications should use
 * {@link org.eclipse.jgit.treewalk.filter.PathFilterGroup} to connect these
 * into a tree filter graph, as the group supports breaking out of traversal
 * once it is known the path can never match.
 */
public class PathFilter extends TreeFilter {
	/**
	 * Create a new tree filter for a user supplied path.
	 * <p>
	 * Path strings are relative to the root of the repository. If the user's
	 * input should be assumed relative to a subdirectory of the repository the
	 * caller must prepend the subdirectory's path prior to creating the filter.
	 * <p>
	 * Path strings use '/' to delimit directories on all platforms.
	 *
	 * @param path
	 *            the path to filter on. Must not be the empty string. All
	 *            trailing '/' characters will be trimmed before string's length
	 *            is checked or is used as part of the constructed filter.
	 * @return a new filter for the requested path.
	 * @throws java.lang.IllegalArgumentException
	 *             the path supplied was the empty string.
	 */
	public static PathFilter create(String path) {
		while (path.endsWith("/")) //$NON-NLS-1$
			path = path.substring(0, path.length() - 1);
		if (path.length() == 0)
			throw new IllegalArgumentException(JGitText.get().emptyPathNotPermitted);
		return new PathFilter(path);
	}

	final String pathStr;

	final byte[] pathRaw;

	private PathFilter(String s) {
		pathStr = s;
		pathRaw = Constants.encode(pathStr);
	}

	/**
	 * Get the path this filter matches.
	 *
	 * @return the path this filter matches.
	 */
	public String getPath() {
		return pathStr;
	}

	/** {@inheritDoc} */
	@Override
	public boolean include(TreeWalk walker) {
		return matchFilter(walker) <= 0;
	}

	/** {@inheritDoc} */
	@Override
	public int matchFilter(TreeWalk walker) {
		return walker.isPathMatch(pathRaw, pathRaw.length);
	}

	/** {@inheritDoc} */
	@Override
	public boolean shouldBeRecursive() {
		for (byte b : pathRaw)
			if (b == '/')
				return true;
		return false;
	}

	/** {@inheritDoc} */
	@Override
	public PathFilter clone() {
		return this;
	}

	/** {@inheritDoc} */
	@Override
	@SuppressWarnings("nls")
	public String toString() {
		return "PATH(\"" + pathStr + "\")";
	}

	/**
	 * Whether the path length of this filter matches the length of the current
	 * path of the supplied TreeWalk.
	 *
	 * @param walker
	 *            The walk to check against.
	 * @return {@code true} if the path length of this filter matches the length
	 *         of the current path of the supplied TreeWalk.
	 */
	public boolean isDone(TreeWalk walker) {
		return pathRaw.length == walker.getPathLength();
	}
}
