/*
 * Copyright (C) 2008, Google Inc.
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
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
		for (final byte b : pathRaw)
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
