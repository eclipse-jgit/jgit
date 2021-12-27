/*
 * Copyright (C) 2009, 2021 Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.treewalk.filter;

import java.io.IOException;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.treewalk.TreeWalk;

/**
 * Includes tree entries only if they start with the configured path (suffix
 * match).
 * <p>
 * For example, <code>PathPrefixFilter.create("foo")</code> will match all
 * paths starting with <code>foo/</code>.
 * <p>
 * Using this filter is recommended instead of filtering the entries using
 * {@link org.eclipse.jgit.treewalk.TreeWalk#getPathString()} and
 * <code>startsWith</code> or some other type of string match function.
 */
public class PathPrefixFilter extends TreeFilter {

    /**
     * Create a new tree filter for a user supplied path prefix.
     * <p>
     * Path strings use '/' to delimit directories on all platforms.
     *
     * @param path
     *            the path prefix to filter on. Must not be the empty string.
     * @return a new filter for the requested path.
     * @throws java.lang.IllegalArgumentException
     *             the path supplied was the empty string.
     */
    public static PathPrefixFilter create(String path) {
        if (path.length() == 0) {
            throw new IllegalArgumentException( JGitText.get().emptyPathNotPermitted );
        }

        return new PathPrefixFilter(path);
    }

    final String pathStr;
    final byte[] pathRaw;

    private PathPrefixFilter(String s) {
        pathStr = s;
        pathRaw = Constants.encode(pathStr);
    }

    /** {@inheritDoc} */
    @Override
    public TreeFilter clone() {
        return this;
    }

    /** {@inheritDoc} */
    @Override
    public boolean include(TreeWalk walker) throws IOException {
        if (walker.isSubtree()) {
            return true;
        }

        return walker.isPathPrefix(pathRaw, pathRaw.length) == 0;

    }

    @Override
    public int matchFilter(TreeWalk walker) throws IOException {
        if (walker.isSubtree()) {
            return -1;
        }

        return super.matchFilter(walker);
    }

    /** {@inheritDoc} */
    @Override
    public boolean shouldBeRecursive() {
        return true;
    }
}
