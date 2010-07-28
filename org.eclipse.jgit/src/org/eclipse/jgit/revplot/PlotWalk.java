/*
 * Copyright (C) 2008-2009, Robin Rosenberg <robin.rosenberg@dewire.com>
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

package org.eclipse.jgit.revplot;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.JGitText;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;

/** Specialized RevWalk for visualization of a commit graph. */
public class PlotWalk extends RevWalk {

	private Map<AnyObjectId, Set<Ref>> reverseRefMap;

	@Override
	public void dispose() {
		super.dispose();
		reverseRefMap.clear();
	}

	/**
	 * Create a new revision walker for a given repository.
	 *
	 * @param repo
	 *            the repository the walker will obtain data from.
	 */
	public PlotWalk(final Repository repo) {
		super(repo);
		super.sort(RevSort.TOPO, true);
		reverseRefMap = repo.getAllRefsByPeeledObjectId();
	}

	@Override
	public void sort(final RevSort s, final boolean use) {
		if (s == RevSort.TOPO && !use)
			throw new IllegalArgumentException(JGitText.get().topologicalSortRequired);
		super.sort(s, use);
	}

	@Override
	protected RevCommit createCommit(final AnyObjectId id) {
		return new PlotCommit(id);
	}

	@Override
	public RevCommit next() throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		PlotCommit<?> pc = (PlotCommit) super.next();
		if (pc != null)
			pc.refs = getTags(pc);
		return pc;
	}

	private Ref[] getTags(final AnyObjectId commitId) {
		Collection<Ref> list = reverseRefMap.get(commitId);
		Ref[] tags;
		if (list == null)
			tags = null;
		else {
			tags = list.toArray(new Ref[list.size()]);
			Arrays.sort(tags, new PlotRefComparator());
		}
		return tags;
	}

	class PlotRefComparator implements Comparator<Ref> {
		public int compare(Ref o1, Ref o2) {
			try {
				RevObject obj1 = parseAny(o1.getObjectId());
				RevObject obj2 = parseAny(o2.getObjectId());
				long t1 = timeof(obj1);
				long t2 = timeof(obj2);
				if (t1 > t2)
					return -1;
				if (t1 < t2)
					return 1;
				return 0;
			} catch (IOException e) {
				// ignore
				return 0;
			}
		}

		long timeof(RevObject o) {
			if (o instanceof RevCommit)
				return ((RevCommit) o).getCommitTime();
			if (o instanceof RevTag) {
				RevTag tag = (RevTag) o;
				PersonIdent who = tag.getTaggerIdent();
				return who != null ? who.getWhen().getTime() : 0;
			}
			return 0;
		}
	}
}
