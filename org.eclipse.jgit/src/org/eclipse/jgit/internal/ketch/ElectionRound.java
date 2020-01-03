/*
 * Copyright (C) 2016, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.ketch;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.eclipse.jgit.internal.ketch.KetchConstants.TERM;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeoutException;

import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TreeFormatter;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.time.ProposedTimestamp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The initial {@link Round} for a leaderless repository, used to establish a
 * leader.
 */
class ElectionRound extends Round {
	private static final Logger log = LoggerFactory.getLogger(ElectionRound.class);

	private long term;

	ElectionRound(KetchLeader leader, LogIndex head) {
		super(leader, head);
	}

	@Override
	void start() throws IOException {
		ObjectId id;
		try (Repository git = leader.openRepository();
				ProposedTimestamp ts = getSystem().getClock().propose();
				ObjectInserter inserter = git.newObjectInserter()) {
			id = bumpTerm(git, ts, inserter);
			inserter.flush();
			blockUntil(ts);
		}
		runAsync(id);
	}

	@Override
	void success() {
		// Do nothing upon election, KetchLeader will copy the term.
	}

	long getTerm() {
		return term;
	}

	private ObjectId bumpTerm(Repository git, ProposedTimestamp ts,
			ObjectInserter inserter) throws IOException {
		CommitBuilder b = new CommitBuilder();
		if (!ObjectId.zeroId().equals(acceptedOldIndex)) {
			try (RevWalk rw = new RevWalk(git)) {
				RevCommit c = rw.parseCommit(acceptedOldIndex);
				if (getSystem().requireMonotonicLeaderElections()) {
					if (ts.read(SECONDS) < c.getCommitTime()) {
						throw new TimeIsUncertainException();
					}
				}
				b.setTreeId(c.getTree());
				b.setParentId(acceptedOldIndex);
				term = parseTerm(c.getFooterLines(TERM)) + 1;
			}
		} else {
			term = 1;
			b.setTreeId(inserter.insert(new TreeFormatter()));
		}

		StringBuilder msg = new StringBuilder();
		msg.append(KetchConstants.TERM.getName())
				.append(": ") //$NON-NLS-1$
				.append(term);

		String tag = leader.getSystem().newLeaderTag();
		if (tag != null && !tag.isEmpty()) {
			msg.append(' ').append(tag);
		}

		b.setAuthor(leader.getSystem().newCommitter(ts));
		b.setCommitter(b.getAuthor());
		b.setMessage(msg.toString());

		if (log.isDebugEnabled()) {
			log.debug("Trying to elect myself " + b.getMessage()); //$NON-NLS-1$
		}
		return inserter.insert(b);
	}

	private static long parseTerm(List<String> footer) {
		if (footer.isEmpty()) {
			return 0;
		}

		String s = footer.get(0);
		int p = s.indexOf(' ');
		if (p > 0) {
			s = s.substring(0, p);
		}
		return Long.parseLong(s, 10);
	}

	private void blockUntil(ProposedTimestamp ts) throws IOException {
		try {
			ts.blockUntil(getSystem().getMaxWaitForMonotonicClock());
		} catch (InterruptedException | TimeoutException e) {
			throw new TimeIsUncertainException(e);
		}
	}
}
