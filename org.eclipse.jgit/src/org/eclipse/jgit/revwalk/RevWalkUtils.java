/*
 * Copyright (C) 2011-2012, Robin Stocker <robin@nibor.org>
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

package org.eclipse.jgit.revwalk;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdSubclassMap;
import org.eclipse.jgit.lib.Ref;

/**
 * Utility methods for {@link RevWalk}.
 */
public final class RevWalkUtils {

	private RevWalkUtils() {
		// Utility class
	}

	/**
	 * Count the number of commits that are reachable from <code>start</code>
	 * until a commit that is reachable from <code>end</code> is encountered. In
	 * other words, count the number of commits that are in <code>start</code>,
	 * but not in <code>end</code>.
	 * <p>
	 * Note that this method calls {@link RevWalk#reset()} at the beginning.
	 * Also note that the existing rev filter on the walk is left as-is, so be
	 * sure to set the right rev filter before calling this method.
	 *
	 * @param walk
	 *            the rev walk to use
	 * @param start
	 *            the commit to start counting from
	 * @param end
	 *            the commit where counting should end, or null if counting
	 *            should be done until there are no more commits
	 *
	 * @return the number of commits
	 * @throws MissingObjectException
	 * @throws IncorrectObjectTypeException
	 * @throws IOException
	 */
	public static int count(final RevWalk walk, final RevCommit start,
			final RevCommit end) throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		return find(walk, start, end).size();
	}

	/**
	 * Find commits that are reachable from <code>start</code> until a commit
	 * that is reachable from <code>end</code> is encountered. In other words,
	 * Find of commits that are in <code>start</code>, but not in
	 * <code>end</code>.
	 * <p>
	 * Note that this method calls {@link RevWalk#reset()} at the beginning.
	 * Also note that the existing rev filter on the walk is left as-is, so be
	 * sure to set the right rev filter before calling this method.
	 *
	 * @param walk
	 *            the rev walk to use
	 * @param start
	 *            the commit to start counting from
	 * @param end
	 *            the commit where counting should end, or null if counting
	 *            should be done until there are no more commits
	 * @return the commits found
	 * @throws MissingObjectException
	 * @throws IncorrectObjectTypeException
	 * @throws IOException
	 */
	public static List<RevCommit> find(final RevWalk walk,
			final RevCommit start, final RevCommit end)
			throws MissingObjectException, IncorrectObjectTypeException,
			IOException {
		walk.reset();
		walk.markStart(start);
		if (end != null)
			walk.markUninteresting(end);

		List<RevCommit> commits = new ArrayList<RevCommit>();
		for (RevCommit c : walk)
			commits.add(c);
		return commits;
	}

	/**
	 * Find the list of branches a given commit is reachable from when following
	 * parent.s
	 * <p>
	 * Note that this method calls {@link RevWalk#reset()} at the beginning.
	 * <p>
	 * In order to improve performance this method assumes clock skew among
	 * committers is never larger than 24 hours.
	 *
	 * @param commit
	 *            the commit we are looking at
	 * @param revWalk
	 *            The RevWalk to be used.
	 * @param refs
	 *            the set of branches we want to see reachability from
	 * @return the list of branches a given commit is reachable from
	 * @throws MissingObjectException
	 * @throws IncorrectObjectTypeException
	 * @throws IOException
	 */
	public static List<Ref> findBranchesReachableFrom(RevCommit commit,
			RevWalk revWalk, Collection<Ref> refs)
			throws MissingObjectException, IncorrectObjectTypeException,
			IOException {

		List<Ref> result = new ArrayList<Ref>();
		// searches from branches can be cut off early if any parent of the
		// search-for commit is found. This is quite likely, so optimize for this.
		revWalk.markStart(Arrays.asList(commit.getParents()));
		ObjectIdSubclassMap<ObjectId> cutOff = new ObjectIdSubclassMap<ObjectId>();

		final int SKEW = 24*3600; // one day clock skew

		for (Ref ref : refs) {
			RevObject maybehead = revWalk.parseAny(ref.getObjectId());
			if (!(maybehead instanceof RevCommit))
				continue;
			RevCommit headCommit = (RevCommit) maybehead;

			// if commit is in the ref branch, then the tip of ref should be
			// newer than the commit we are looking for. Allow for a large
			// clock skew.
			if (headCommit.getCommitTime() + SKEW < commit.getCommitTime())
				continue;

			List<ObjectId> maybeCutOff = new ArrayList<ObjectId>(cutOff.size()); // guess rough size
			revWalk.resetRetain();
			revWalk.markStart(headCommit);
			RevCommit current;
			Ref found = null;
			while ((current = revWalk.next()) != null) {
				if (AnyObjectId.equals(current, commit)) {
					found = ref;
					break;
				}
				if (cutOff.contains(current))
					break;
				maybeCutOff.add(current.toObjectId());
			}
			if (found != null)
				result.add(ref);
			else
				for (ObjectId id : maybeCutOff)
					cutOff.addIfAbsent(id);

		}
		return result;
	}

	/**
	 * Process .git/shallow and mark these commits as shallow (no parents) for
	 * the specified RevWalk.
	 * <p>
	 * This is necessary for shallow clones to avoid MissingObjectException when
	 * performing the RevWalk.
	 *
	 * @param revWalk
	 *            The RevWalk to be used.
	 * @throws IOException
	 */
	public static void initializeShallowCommits(RevWalk revWalk) throws IOException {
		final File shallow = new File(revWalk.repository.getDirectory(),
				"shallow");
		if (!shallow.isFile())
			return;

		final BufferedReader reader = new BufferedReader(
				new FileReader(shallow));
		try {
			String line;
			while ((line = reader.readLine()) != null) {
				final ObjectId id = ObjectId.fromString(line);
				final RevCommit commit = revWalk.lookupCommit(id);
				commit.parents = new RevCommit[0];
			}
		} finally {
			reader.close();
		}
	}
}
