/*
 * Copyright (C) 2013, Google Inc.
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * Command to find human-readable names of revisions.
 *
 * @see <a href="http://www.kernel.org/pub/software/scm/git/docs/git-name-rev.html"
 *      >Git documentation about name-rev</a>
 */
public class NameRevCommand extends GitCommand<Map<AnyObjectId, String>> {
	/** Amount of slop to allow walking past the earliest requested commit. */
	private static final int COMMIT_TIME_SLOP = 60 * 60 * 24;

	/** Cost of traversing a merge commit compared to a linear history. */
	private static final int MERGE_COST = 65535;

	private static class NameRevCommit extends RevCommit {
		private String tip;
		private int distance;
		private long cost;

		private NameRevCommit(AnyObjectId id) {
			super(id);
		}

		private StringBuilder format() {
			StringBuilder sb = new StringBuilder(String.valueOf(tip));
			if (distance > 0)
				sb.append('~').append(distance);
			return sb;
		}

		@Override
		public String toString() {
			return new StringBuilder(getClass().getSimpleName())
				.append('[').append(format()).append(',').append(cost).append(']')
				.append(' ').append(super.toString()).toString();
		}
	}

	private final RevWalk walk;
	private final List<String> prefixes;
	private final List<AnyObjectId> revs;

	/**
	 * Create a new name-rev command.
	 *
	 * @param repo
	 */
	protected NameRevCommand(Repository repo) {
		super(repo);
		prefixes = new ArrayList<String>(2);
		revs = new ArrayList<AnyObjectId>(2);
		walk = new RevWalk(repo) {
			@Override
			public NameRevCommit createCommit(AnyObjectId id) {
				return new NameRevCommit(id);
			}
		};
	}

	@Override
	public Map<AnyObjectId, String> call() throws GitAPIException {
		try {
			Map<AnyObjectId, String> nonCommits = new HashMap<AnyObjectId, String>();
			LinkedList<NameRevCommit> pending = new LinkedList<NameRevCommit>();
			addPrefixes(nonCommits, pending);
			int cutoff = minCommitTime() - COMMIT_TIME_SLOP;

			while (!pending.isEmpty()) {
				NameRevCommit c = pending.remove();
				if (c.getCommitTime() < cutoff) {
					continue;
				}
				boolean merge = c.getParentCount() > 1;
				long cost = c.cost + (merge ? MERGE_COST : 1);
				for (int i = 0; i < c.getParentCount(); i++) {
					NameRevCommit p = (NameRevCommit) walk.parseCommit(c.getParent(i));
					if (p.tip == null || compare(c.tip, cost, p.tip, p.cost) < 0) {
						if (merge) {
							p.tip = c.format().append('^').append(i + 1).toString();
							p.distance = 0;
						} else {
							p.tip = c.tip;
							p.distance = c.distance + 1;
						}
						p.cost = cost;
						pending.add(p);
					}
				}
			}

			Map<AnyObjectId, String> result =
				new LinkedHashMap<AnyObjectId, String>(revs.size());
			for (AnyObjectId id : revs) {
				RevObject o = walk.parseAny(id);
				if (o instanceof NameRevCommit) {
					NameRevCommit c = (NameRevCommit) o;
					if (c.tip != null)
						result.put(id, simplify(c.format().toString()));
				} else {
					String name = nonCommits.get(id);
					if (name != null)
						result.put(id, simplify(name));
				}
			}

			setCallable(false);
			walk.release();
			return result;
		} catch (IOException e) {
			walk.reset();
			throw new JGitInternalException(e.getMessage(), e);
		}
	}

	/**
	 * Add an object to search for.
	 *
	 * @param id
	 *            object ID to add.
	 * @return {@code this}
	 * @throws MissingObjectException
	 *             the object supplied is not available from the object
	 *             database.
	 * @throws JGitInternalException
	 *             a low-level exception of JGit has occurred. The original
	 *             exception can be retrieved by calling
	 *             {@link Exception#getCause()}.
	 */
	public NameRevCommand add(AnyObjectId id) throws MissingObjectException,
			JGitInternalException {
		checkCallable();
		try {
			walk.parseAny(id);
		} catch (MissingObjectException e) {
			throw e;
		} catch (IOException e) {
			throw new JGitInternalException(e.getMessage(), e);
		}
		revs.add(id);
		return this;
	}

	/**
	 * Add multiple objects to search for.
	 *
	 * @param ids
	 *            object IDs to add.
	 * @return {@code this}
	 * @throws MissingObjectException
	 *             the object supplied is not available from the object
	 *             database.
	 * @throws JGitInternalException
	 *             a low-level exception of JGit has occurred. The original
	 *             exception can be retrieved by calling
	 *             {@link Exception#getCause()}.
	 */
	public NameRevCommand add(Iterable<AnyObjectId> ids)
			throws MissingObjectException, JGitInternalException {
		for (AnyObjectId id : ids) {
			add(id);
		}
		return this;
	}

	/**
	 * Add a ref prefix that all results must match.
	 * <p>
	 * If an object matches refs under multiple prefixes equally well, the first
	 * prefix added to this command is preferred.
	 *
	 * @param prefix
	 *            prefix to add; see {@link RefDatabase#getRefs(String)}
	 * @return {@code this}
	 */
	public NameRevCommand addPrefix(String prefix) {
		checkCallable();
		prefixes.add(prefix);
		return this;
	}

	private void addPrefixes(Map<AnyObjectId, String> nonCommits,
			LinkedList<NameRevCommit> pending) throws IOException {
		if (!prefixes.isEmpty()) {
			for (String prefix : prefixes) {
				addPrefix(prefix, nonCommits, pending);
			}
		} else {
			addPrefix(Constants.R_REFS, nonCommits, pending);
		}
	}

	private void addPrefix(String prefix, Map<AnyObjectId, String> nonCommits,
			LinkedList<NameRevCommit> pending) throws IOException {
		for (Ref ref : repo.getRefDatabase().getRefs(prefix).values()) {
			RevObject o = walk.parseAny(ref.getObjectId());
			while (o instanceof RevTag) {
				RevTag t = (RevTag) o;
				nonCommits.put(o, ref.getName());
				o = t.getObject();
				walk.parseHeaders(o);
			}
			if (o instanceof NameRevCommit) {
				NameRevCommit c = (NameRevCommit) o;
				if (c.tip == null)
					c.tip = ref.getName();
				pending.add(c);
			} else if (!nonCommits.containsKey(o))
				nonCommits.put(o, ref.getName());
		}
	}

	private int minCommitTime() throws IOException {
		int min = Integer.MAX_VALUE;
		for (AnyObjectId id : revs) {
			RevObject o = walk.parseAny(id);
			while (o instanceof RevTag) {
				o = ((RevTag) o).getObject();
				walk.parseHeaders(o);
			}
			if (o instanceof RevCommit) {
				RevCommit c = (RevCommit) o;
				if (c.getCommitTime() < min) {
					min = c.getCommitTime();
				}
			}
		}
		return min;
	}

	private long compare(String leftTip, long leftCost, String rightTip, long rightCost) {
		long c = leftCost - rightCost;
		if (c != 0 || prefixes.isEmpty())
			return c;
		int li = -1;
		int ri = -1;
		for (int i = 0; i < prefixes.size(); i++) {
			String prefix = prefixes.get(i);
			if (li < 0 && leftTip.startsWith(prefix))
				li = i;
			if (ri < 0 && rightTip.startsWith(prefix))
				ri = i;
		}
		// Don't tiebreak if prefixes are the same, in order to prefer first-parent
		// paths.
		return li - ri;
	}

	private static String simplify(String refName) {
		if (refName.startsWith(Constants.R_HEADS))
			return refName.substring(Constants.R_HEADS.length());
		if (refName.startsWith(Constants.R_TAGS))
			return refName.substring(Constants.R_TAGS.length());
		if (refName.startsWith(Constants.R_REFS))
			return refName.substring(Constants.R_REFS.length());
		else
			return refName;
	}
}
