/*
 * Copyright (C) 2012, Carl Myers <cmyers@cmyers.org>
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
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.JGitText;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevFlag;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;

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
	// this is the default for git
	private static final int DEFAULT_ABBREVIATION_LENGTH = 7;

	private int abbreviationLength;
	private ObjectId oid;

	/**
	 * @param repo
	 */
	protected DescribeCommand(Repository repo) {
		super(repo);
		abbreviationLength = DEFAULT_ABBREVIATION_LENGTH;
		// Use HEAD by default (mirrors cgit impl)
		try {
			oid = repo.resolve("HEAD");
		} catch (IOException e) {
			// Ignored, will throw if oid not set before call()
		}
	}

	/**
	 * Set the length of hash abbreviation.
	 *
	 * @param abbrev
	 *            length to abbreviate commands to.
	 * @return a reference to {@code this}, allows chaining calls
	 * @throws IllegalArgumentException
	 */
	public DescribeCommand setAbbrev(final int abbrev)
			throws IllegalArgumentException {
		checkCallable();
		if (abbrev < 0 || abbrev == 1 || abbrev > 40) {
			throw new IllegalArgumentException(
					JGitText.get().describeInvalidAbbreviation);
		}
		this.abbreviationLength = abbrev;
		return this;
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
		setCallable(false);

		if (oid == null) {
			throw new JGitInternalException(
					JGitText.get().describeObjectIdNotSet,
					new NullPointerException());
		}

		RevWalk w = new RevWalk(repo);
        Map<RevCommit, List<String>> tagLookup = new HashMap<RevCommit, List<String>>();
		try {
            RevFlag f = w.newFlag("wanted");
            for (Ref tag : repo.getTags().values()) {
                // Tags can point to non-commits - skip those
                if (w.parseCommit(tag.getObjectId()).getType() != Constants.OBJ_COMMIT)
                	continue;
                RevCommit rc = w.parseCommit(tag.getObjectId());
                rc.add(f);
                String fullTagName = tag.getName();
                String[] tagParts = fullTagName.split("/");
                String tagName = tagParts[Array.getLength(tagParts)-1];
                if (tagLookup.containsKey(rc)) {
                	tagLookup.get(rc).add(tagName);
                } else {
                	List<String> l = new ArrayList<String>();
                	l.add(tagName);
                    tagLookup.put(rc, l);
                }
            }

            RevCommit start = w.parseCommit(oid);
            RevCommit candidate = null;
            int candidateDistance = 0;

            w.markStart(start);
            w.setRevFilter(RevFilter.ALL);
            w.sort(RevSort.TOPO);
            RevCommit r = null;
            while ((r = w.next()) != null) {
            	if (r.has(f)) {
            		candidate = r;
                    w.markUninteresting(w.parseCommit(r));
            	}
                ++candidateDistance;
            }

			if (candidate == null) {
				// not found
				return null;
			}

			// Determine tag name - if there happens to be more than one tag at
			// the same commit, use the one with the most recent date.  This is
			// what cgit does.
			int age = 0;
			String tagName = null;
            for (Map.Entry<String, Ref> e : repo.getTags().entrySet()) {
            	ObjectId thisOid = w.parseCommit(e.getValue().getObjectId());
            	ObjectId candidateOid = candidate.getId();
            	if (thisOid.equals(candidateOid)) {
            		if (w.parseCommit(thisOid).getCommitTime() > age) {
            			age = w.parseCommit(thisOid).getCommitTime();
            			tagName = e.getKey();
            		}

            	}
            }

			if (candidateDistance == 1 || abbreviationLength == 0) {
				return tagName;
			}
			return tagName + "-" + Integer.toString(candidateDistance-1) + "-" + "g"
					+ repo.getObjectDatabase().newReader()
							.abbreviate(oid, abbreviationLength).name();
		} catch (MissingObjectException e) {
			throw new JGitInternalException(
					JGitText.get().exceptionCaughtDuringExecutionOfDescribeCommand,
					e);
		} catch (IOException e) {
			throw new JGitInternalException(
					JGitText.get().exceptionCaughtDuringExecutionOfDescribeCommand,
					e);
		} finally {
			w.release();
		}
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
	 * @return this object, for chaining calls
	 */
	public DescribeCommand setObjectId(ObjectId oid) {
		this.oid = oid;
		return this;
	}

	/**
	 * Returns the default abbreviation length
	 *
	 * @return default abbreviation length
	 */
	public static int getDefaultAbbreviationLength() {
		return DEFAULT_ABBREVIATION_LENGTH;
	}
}
