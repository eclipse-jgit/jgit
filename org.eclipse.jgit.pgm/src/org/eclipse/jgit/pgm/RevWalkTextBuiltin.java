/*
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

package org.eclipse.jgit.pgm;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;
import org.eclipse.jgit.diff.DiffConfig;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.pgm.internal.CLIText;
import org.eclipse.jgit.pgm.opt.PathTreeFilterHandler;
import org.eclipse.jgit.revwalk.FollowFilter;
import org.eclipse.jgit.revwalk.ObjectWalk;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevFlag;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.AndRevFilter;
import org.eclipse.jgit.revwalk.filter.AuthorRevFilter;
import org.eclipse.jgit.revwalk.filter.CommitterRevFilter;
import org.eclipse.jgit.revwalk.filter.MessageRevFilter;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.treewalk.filter.AndTreeFilter;
import org.eclipse.jgit.treewalk.filter.TreeFilter;

abstract class RevWalkTextBuiltin extends TextBuiltin {
	RevWalk walk;

	@Option(name = "--objects")
	boolean objects = false;

	@Option(name = "--parents")
	boolean parents = false;

	@Option(name = "--total-count")
	boolean count = false;

	@Option(name = "--all")
	boolean all = false;

	char[] outbuffer = new char[Constants.OBJECT_ID_LENGTH * 2];

	private final EnumSet<RevSort> sorting = EnumSet.noneOf(RevSort.class);

	private void enableRevSort(final RevSort type, final boolean on) {
		if (on)
			sorting.add(type);
		else
			sorting.remove(type);
	}

	@Option(name = "--date-order")
	void enableDateOrder(final boolean on) {
		enableRevSort(RevSort.COMMIT_TIME_DESC, on);
	}

	@Option(name = "--topo-order")
	void enableTopoOrder(final boolean on) {
		enableRevSort(RevSort.TOPO, on);
	}

	@Option(name = "--reverse")
	void enableReverse(final boolean on) {
		enableRevSort(RevSort.REVERSE, on);
	}

	@Option(name = "--boundary")
	void enableBoundary(final boolean on) {
		enableRevSort(RevSort.BOUNDARY, on);
	}

	@Option(name = "--follow", metaVar = "metaVar_path")
	private String followPath;

	@Argument(index = 0, metaVar = "metaVar_commitish")
	private final List<RevCommit> commits = new ArrayList<RevCommit>();

	@Option(name = "--", metaVar = "metaVar_path", multiValued = true, handler = PathTreeFilterHandler.class)
	protected TreeFilter pathFilter = TreeFilter.ALL;

	private final List<RevFilter> revLimiter = new ArrayList<RevFilter>();

	@Option(name = "--author")
	void addAuthorRevFilter(final String who) {
		revLimiter.add(AuthorRevFilter.create(who));
	}

	@Option(name = "--committer")
	void addCommitterRevFilter(final String who) {
		revLimiter.add(CommitterRevFilter.create(who));
	}

	@Option(name = "--grep")
	void addCMessageRevFilter(final String msg) {
		revLimiter.add(MessageRevFilter.create(msg));
	}

	@Override
	protected void run() throws Exception {
		walk = createWalk();
		for (final RevSort s : sorting)
			walk.sort(s, true);

		if (pathFilter == TreeFilter.ALL) {
			if (followPath != null)
				walk.setTreeFilter(FollowFilter.create(followPath,
						db.getConfig().get(DiffConfig.KEY)));
		} else if (pathFilter != TreeFilter.ALL) {
			walk.setTreeFilter(AndTreeFilter.create(pathFilter,
					TreeFilter.ANY_DIFF));
		}

		if (revLimiter.size() == 1)
			walk.setRevFilter(revLimiter.get(0));
		else if (revLimiter.size() > 1)
			walk.setRevFilter(AndRevFilter.create(revLimiter));

		if (all) {
			Map<String, Ref> refs =
				db.getRefDatabase().getRefs(RefDatabase.ALL);
			for (Ref a : refs.values()) {
				ObjectId oid = a.getPeeledObjectId();
				if (oid == null)
					oid = a.getObjectId();
				try {
					commits.add(walk.parseCommit(oid));
				} catch (IncorrectObjectTypeException e) {
					// Ignore all refs which are not commits
				}
			}
		}

		if (commits.isEmpty()) {
			final ObjectId head = db.resolve(Constants.HEAD);
			if (head == null)
				throw die(MessageFormat.format(CLIText.get().cannotResolve, Constants.HEAD));
			commits.add(walk.parseCommit(head));
		}
		for (final RevCommit c : commits) {
			final RevCommit real = argWalk == walk ? c : walk.parseCommit(c);
			if (c.has(RevFlag.UNINTERESTING))
				walk.markUninteresting(real);
			else
				walk.markStart(real);
		}

		final long start = System.currentTimeMillis();
		final int n = walkLoop();
		if (count) {
			final long end = System.currentTimeMillis();
			err.print(n);
			err.print(' ');
			err.println(MessageFormat.format(
							CLIText.get().timeInMilliSeconds,
							Long.valueOf(end - start)));
		}
	}

	protected RevWalk createWalk() {
		if (objects)
			return new ObjectWalk(db);
		if (argWalk != null)
			return argWalk;
		return argWalk = new RevWalk(db);
	}

	protected int walkLoop() throws Exception {
		int n = 0;
		for (final RevCommit c : walk) {
			n++;
			show(c);
		}
		if (walk instanceof ObjectWalk) {
			final ObjectWalk ow = (ObjectWalk) walk;
			for (;;) {
				final RevObject obj = ow.nextObject();
				if (obj == null)
					break;
				show(ow, obj);
			}
		}
		return n;
	}

	/**
	 * "Show" the current RevCommit when called from the main processing loop.
	 * <p>
	 * Implement this methods to define the behavior for subclasses of
	 * RevWalkTextBuiltin.
	 *
	 * @param c
	 *            The current {@link RevCommit}
	 * @throws Exception
	 */
	protected abstract void show(final RevCommit c) throws Exception;

	/**
	 * "Show" the current RevCommit when called from the main processing loop.
	 * <p>
	 * The default implementation does nothing because most subclasses only
	 * process RevCommits.
	 *
	 * @param objectWalk
	 *            the {@link ObjectWalk} used by {@link #walkLoop()}
	 * @param currentObject
	 *            The current {@link RevObject}
	 * @throws Exception
	 */
	protected void show(final ObjectWalk objectWalk,
			final RevObject currentObject) throws Exception {
		// Do nothing by default. Most applications cannot show an object.
	}
}
