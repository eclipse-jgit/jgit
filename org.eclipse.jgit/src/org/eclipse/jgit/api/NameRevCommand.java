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
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.FIFORevQueue;
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
public class NameRevCommand extends GitCommand<Map<ObjectId, String>> {
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
			StringBuilder sb = new StringBuilder(tip);
			if (distance > 0)
				sb.append('~').append(distance);
			return sb;
		}

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder(getClass().getSimpleName())
				.append('[');
			if (tip != null)
				sb.append(format());
			else
				sb.append(String.valueOf(null));
			sb.append(',').append(cost).append(']').append(' ')
				.append(super.toString()).toString();
			return sb.toString();
		}
	}

	private final RevWalk walk;
	private final List<String> prefixes;
	private final List<Ref> refs;
	private final List<ObjectId> revs;

	/**
	 * Create a new name-rev command.
	 *
	 * @param repo
	 */
	protected NameRevCommand(Repository repo) {
		super(repo);
		prefixes = new ArrayList<String>(2);
		refs = new ArrayList<Ref>();
		revs = new ArrayList<ObjectId>(2);
		walk = new RevWalk(repo) {
			@Override
			public NameRevCommit createCommit(AnyObjectId id) {
				return new NameRevCommit(id);
			}
		};
	}

	@Override
	public Map<ObjectId, String> call() throws GitAPIException {
		try {
			Map<ObjectId, String> nonCommits = new HashMap<ObjectId, String>();
			FIFORevQueue pending = new FIFORevQueue();
			for (Ref ref : refs) {
				addRef(ref, nonCommits, pending);
			}
			addPrefixes(nonCommits, pending);
			int cutoff = minCommitTime() - COMMIT_TIME_SLOP;

			while (true) {
				NameRevCommit c = (NameRevCommit) pending.next();
				if (c == null)
					break;
				if (c.getCommitTime() < cutoff)
					continue;
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

			Map<ObjectId, String> result =
				new LinkedHashMap<ObjectId, String>(revs.size());
			for (ObjectId id : revs) {
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
	public NameRevCommand add(ObjectId id) throws MissingObjectException,
			JGitInternalException {
		checkCallable();
		try {
			walk.parseAny(id);
		} catch (MissingObjectException e) {
			throw e;
		} catch (IOException e) {
			throw new JGitInternalException(e.getMessage(), e);
		}
		revs.add(id.copy());
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
	public NameRevCommand add(Iterable<ObjectId> ids)
			throws MissingObjectException, JGitInternalException {
		for (ObjectId id : ids)
			add(id);
		return this;
	}

	/**
	 * Add a ref prefix to the set that results must match.
	 * <p>
	 * If an object matches multiple refs equally well, the first matching ref
	 * added with {@link #addRef(Ref)} is preferred, or else the first matching
	 * prefix added by {@link #addPrefix(String)}.
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

	/**
	 * Add all annotated tags under {@code refs/tags/} to the set that all results
	 * must match.
	 * <p>
	 * Calls {@link #addRef(Ref)}; see that method for a note on matching
	 * priority.
	 *
	 * @return {@code this}
	 * @throws JGitInternalException
	 *             a low-level exception of JGit has occurred. The original
	 *             exception can be retrieved by calling
	 *             {@link Exception#getCause()}.
	 */
	public NameRevCommand addAnnotatedTags() {
		checkCallable();
		try {
			for (Ref ref : repo.getRefDatabase().getRefs(Constants.R_TAGS).values()) {
				ObjectId id = ref.getObjectId();
				if (id != null && (walk.parseAny(id) instanceof RevTag))
					addRef(ref);
			}
		} catch (IOException e) {
			throw new JGitInternalException(e.getMessage(), e);
		}
		return this;
	}

	/**
	 * Add a ref to the set that all results must match.
	 * <p>
	 * If an object matches multiple refs equally well, the first matching ref
	 * added with {@link #addRef(Ref)} is preferred, or else the first matching
	 * prefix added by {@link #addPrefix(String)}.
	 *
	 * @param ref
	 *            ref to add.
	 * @return {@code this}
	 */
	public NameRevCommand addRef(Ref ref) {
		checkCallable();
		refs.add(ref);
		return this;
	}

	private void addPrefixes(Map<ObjectId, String> nonCommits,
			FIFORevQueue pending) throws IOException {
		if (!prefixes.isEmpty()) {
			for (String prefix : prefixes)
				addPrefix(prefix, nonCommits, pending);
		} else
			addPrefix(Constants.R_REFS, nonCommits, pending);
	}

	private void addPrefix(String prefix, Map<ObjectId, String> nonCommits,
			FIFORevQueue pending) throws IOException {
		for (Ref ref : repo.getRefDatabase().getRefs(prefix).values())
			addRef(ref, nonCommits, pending);
	}

	private void addRef(Ref ref, Map<ObjectId, String> nonCommits,
			FIFORevQueue pending) throws IOException {
		if (ref.getObjectId() == null)
			return;
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

	private int minCommitTime() throws IOException {
		int min = Integer.MAX_VALUE;
		for (ObjectId id : revs) {
			RevObject o = walk.parseAny(id);
			while (o instanceof RevTag) {
				o = ((RevTag) o).getObject();
				walk.parseHeaders(o);
			}
			if (o instanceof RevCommit) {
				RevCommit c = (RevCommit) o;
				if (c.getCommitTime() < min)
					min = c.getCommitTime();
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
		return refName;
	}
}
