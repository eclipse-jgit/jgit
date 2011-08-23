/*
 * Copyright (C) 2010, Christian Halstrick <christian.halstrick@sap.com>
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
package org.eclipse.jgit.api;

import java.io.IOException;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.treewalk.filter.TreeFilter;

/**
 * A class used to execute a {@code Describe} command. It has setters for all
 * supported options and arguments of this command and a {@link #call()} method
 * to finally execute the command. Each instance of this class should only be
 * used for one invocation of the command (means: one call to {@link #call()})
 * <p>
 *
 * TODO: Implement the following options: --all: Use any ref found in .git/refs
 * --tags: use any tag found in .git/refs instead of just annotated tags
 * --contains: Find the tag that comes after the commit --exact-match,
 * --candidates --long --match --always
 *
 * @see <a
 *      href="http://www.kernel.org/pub/software/scm/git/docs/git-describe.html"
 *      >Git documentation about Describe</a>
 */
public class DescribeCommand extends GitCommand<String> {
	// seems to be the default for git
	private static final int DEFAULT_ABBREVIATION_LENGTH = 7;

	private int abbreviationLength;
	private ObjectId oid;

	/**
	 * @param repo
	 */
	protected DescribeCommand(Repository repo) {
		super(repo);
		abbreviationLength = DEFAULT_ABBREVIATION_LENGTH;
	}

	/**
	 * Set the length of hash abbreviation.
	 *
	 * @param abbrev
	 *            length to abbreviate commands to.
	 */
	public void setAbbrev(final int abbrev) {
		this.abbreviationLength = abbrev;
	}

	/**
	 * Executes the {@code Describe} command with all the options and parameters
	 * collected by the setter methods. Each instance of this class should only
	 * be used for one invocation of the command. Don't call this method twice
	 * on an instance.
	 *
	 * @return a String containing the unique identifier, or an empty string if
	 *         no tags were found.
	 */
	public String call() throws NoHeadException, JGitInternalException {
		checkCallable();

		RevWalk w = new RevWalk(repo);
		String tagName = null;
		Map<String, Ref> allTags = repo.getTags();
		Integer shortestLengthSoFar = null;
		for (Entry<String, Ref> tag : allTags.entrySet()) {

			RevCommit base;
			RevCommit tip;
			try {
				RevObject ro = w.parseCommit(w.parseTag(
						tag.getValue().getObjectId()).getObject());
				if (ro.getType() != Constants.OBJ_COMMIT) {
					// must be a commit object or doesn't make sense.
					continue;
				}
				base = w.parseCommit(ro.getId());
				tip = w.parseCommit(oid);

				if (!w.isMergedInto(base, tip)) {
					// this tag doesn't exist in this commit's history
					continue;
				}
				// This tag does exist in the history, so count the number of
				// commits since it

				int i = countCommits(tip.getId(), base.getId());
				if (shortestLengthSoFar == null) {
					// found, and first
					shortestLengthSoFar = i;
					tagName = tag.getKey();
				}
				if (shortestLengthSoFar > i) {
					shortestLengthSoFar = i;
					tagName = tag.getKey();
				}

			} catch (MissingObjectException e) {
				e.printStackTrace();
				continue;
			} catch (IncorrectObjectTypeException e) {
				// Not a "real" error - just means there was an object tagged
				// which wasn't a commit - the correct thing to do here is
				// ignore it.
				continue;
			} catch (IOException e) {
				e.printStackTrace();
				continue;
			}
		}
		if (shortestLengthSoFar == null) {
			// not found
			setCallable(false);
			return null;
		}
		setCallable(false);
		return tagName + "-" + shortestLengthSoFar.toString() + "-" + "g"
				+ oid.abbreviate(abbreviationLength).name();
	}

	/**
	 * This method counts the number of commits that are parents of the given
	 * commit.
	 *
	 * Because REvCommit objects must be created by the same RevWalk object,
	 * ObjectIds are passed in instead of RevCommit objects. Don't screw that
	 * up!
	 *
	 * @param tip
	 *            starting commit
	 * @param end
	 *            Stoping commit
	 * @return count of commits in the history of the given object
	 * @throws IOException
	 * @throws IncorrectObjectTypeException
	 * @throws MissingObjectException
	 */
	private int countCommits(final ObjectId tip, final ObjectId end)
			throws MissingObjectException, IncorrectObjectTypeException,
			IOException {
		RevWalk w = new RevWalk(repo);
		w.reset();
		w.setRevFilter(RevFilter.ALL);
		w.setTreeFilter(TreeFilter.ALL);
		w.markStart(w.parseCommit(tip));
		w.markUninteresting(w.parseCommit(end));
		int count = 0;
		while (w.next() != null) {
			++count;
		}
		return count;
	}

	/**
	 * @return the object to run describe for.
	 */
	public ObjectId getObjectId() {
		return oid;
	}

	/**
	 * Set the object id to run describe for.
	 *
	 * @param oid
	 */
	public void setObjectId(ObjectId oid) {
		this.oid = oid;
	}
}
