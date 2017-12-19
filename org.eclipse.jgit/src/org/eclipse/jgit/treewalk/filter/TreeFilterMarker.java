/*
 * Copyright (C) 2013, Robin Stocker <robin@nibor.org>
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

import java.io.IOException;
import java.text.MessageFormat;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.StopWalkException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.treewalk.TreeWalk;

/**
 * For testing an array of {@link org.eclipse.jgit.treewalk.filter.TreeFilter}
 * during a {@link org.eclipse.jgit.treewalk.TreeWalk} for each entry and
 * returning the result as a bitmask.
 *
 * @since 2.3
 */
public class TreeFilterMarker {

	private final TreeFilter[] filters;

	/**
	 * Construct a TreeFilterMarker. Note that it is stateful and can only be
	 * used for one walk loop.
	 *
	 * @param markTreeFilters
	 *            the filters to use for marking, must not have more elements
	 *            than {@link java.lang.Integer#SIZE}.
	 * @throws java.lang.IllegalArgumentException
	 *             if more tree filters are passed than possible
	 */
	public TreeFilterMarker(TreeFilter[] markTreeFilters) {
		if (markTreeFilters.length > Integer.SIZE) {
			throw new IllegalArgumentException(MessageFormat.format(
					JGitText.get().treeFilterMarkerTooManyFilters,
					Integer.valueOf(Integer.SIZE),
					Integer.valueOf(markTreeFilters.length)));
		}
		filters = new TreeFilter[markTreeFilters.length];
		System.arraycopy(markTreeFilters, 0, filters, 0, markTreeFilters.length);
	}

	/**
	 * Test the filters against the walk. Returns a bitmask where each bit
	 * represents the result of a call to
	 * {@link org.eclipse.jgit.treewalk.filter.TreeFilter#include(TreeWalk)},
	 * ordered by the index for which the tree filters were passed in the
	 * constructor.
	 *
	 * @param walk
	 *            the walk from which to test the current entry
	 * @return the marks bitmask
	 * @throws org.eclipse.jgit.errors.MissingObjectException
	 *             as thrown by
	 *             {@link org.eclipse.jgit.treewalk.filter.TreeFilter#include(TreeWalk)}
	 * @throws org.eclipse.jgit.errors.IncorrectObjectTypeException
	 *             as thrown by
	 *             {@link org.eclipse.jgit.treewalk.filter.TreeFilter#include(TreeWalk)}
	 * @throws java.io.IOException
	 *             as thrown by
	 *             {@link org.eclipse.jgit.treewalk.filter.TreeFilter#include(TreeWalk)}
	 */
	public int getMarks(TreeWalk walk) throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		int marks = 0;
		for (int index = 0; index < filters.length; index++) {
			TreeFilter filter = filters[index];
			if (filter != null) {
				try {
					boolean marked = filter.include(walk);
					if (marked)
						marks |= (1L << index);
				} catch (StopWalkException e) {
					// Don't check tree filter anymore, it will no longer
					// match
					filters[index] = null;
				}
			}
		}
		return marks;
	}

}
