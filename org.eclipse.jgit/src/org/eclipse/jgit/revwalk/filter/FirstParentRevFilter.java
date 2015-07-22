/*
 * Copyright (C) 2015, Google Inc.
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

package org.eclipse.jgit.revwalk.filter;

import java.io.IOException;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.StopWalkException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevFlag;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * RevFilter that only follows first-parent links.
 * <p>
 * Equivalent to {@code git log --first-parent}.
 * <p>
 * This filter may be used in combination with {@link AndRevFilter} or {@link
 * OrRevFilter}, but if so, it must be the first filter in the list, since its
 * {@code include} method has side effects that should not be short-circuited.
 *
 * @since 4.1
 */
public class FirstParentRevFilter extends RevFilter {
	/**
	 * Create a new filter.
	 * <p>
	 * As a side effect, allocates two flags on the walk. These flags may be
	 * disposed with {@link #dispose()}.
	 *
	 * @param rw
	 *            RevWalk to filter; must have at least 2 flags free.
	 * @return new filter.
	 */
	public static FirstParentRevFilter create(RevWalk rw) {
		return new FirstParentRevFilter(rw);
	}

	private final RevWalk rw;
	private final RevFlag start;
	private final RevFlag firstParent;

	private FirstParentRevFilter(RevWalk rw) {
		this.rw = rw;
		start = rw.newFlag("LATER_PARENT"); //$NON-NLS-1$
		firstParent = rw.newFlag("START"); //$NON-NLS-1$
		rw.setStartFlag(start);
	}

	@Override
	public boolean include(RevWalk walker, RevCommit cmit)
			throws StopWalkException, MissingObjectException,
			IncorrectObjectTypeException, IOException {
		boolean include = cmit.has(start) || cmit.has(firstParent);
		for (int i = 0; i < cmit.getParentCount(); i++) {
			RevCommit p = cmit.getParent(i);
			if (i == 0 && include) {
				p.add(firstParent);
			}
		}
		return include;
	}

	@Override
	public RevFilter clone() {
		return this;
	}

	@Override
	public boolean requiresCommitBody() {
		return false;
	}

	@Override
	public String toString() {
		return "FIRST_PARENT"; //$NON-NLS-1$
	}

	/** Dispose flags allocated by this instance. */
	public void dispose() {
		rw.disposeFlag(start);
		rw.disposeFlag(firstParent);
	}
}
