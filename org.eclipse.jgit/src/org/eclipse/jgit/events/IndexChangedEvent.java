/*
 * Copyright (C) 2010, Google Inc.
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

package org.eclipse.jgit.events;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;

/** Describes a change to one or more paths in the index file. */
public class IndexChangedEvent extends RepositoryEvent<IndexChangedListener> {

	private static final Map<Repository, DirCache> oldCaches = new HashMap<Repository, DirCache>();
	private List<String> changes = null;

	@Override
	public Class<IndexChangedListener> getListenerType() {
		return IndexChangedListener.class;
	}

	/**
	 * @return the list of modified paths since the last index changed event
	 */
	public synchronized List<String> getModifiedPaths() {
		if(changes != null)
			return changes;

		Repository repo = getRepository();
		changes = new ArrayList<String>();
		try {
			synchronized (repo) {
				DirCache oldCache = oldCaches.get(repo);
				DirCache newCache = DirCache.read(repo);

				if(newCache.equals(oldCache))
					return changes;

				TreeWalk tw = new TreeWalk(repo);
				try {
					tw.addTree(new DirCacheIterator(oldCache));
					tw.addTree(new DirCacheIterator(newCache));
					tw.setFilter(TreeFilter.ANY_DIFF);

					while (tw.next()) {
						if (tw.isSubtree())
							tw.enterSubtree();
						else
							changes.add(tw.getPathString());
					}
				} finally {
					tw.release();
				}

				oldCaches.put(repo, newCache);
			}
		} catch (Exception e) {
			throw new JGitInternalException("Error calculating changed files",
					e);
			// bad...?
		}

		return changes;
	}

	/**
	 * Sets the initial Repository Index state.
	 *
	 * @param repository
	 *            the repo
	 */
	public static void setInitialIndex(Repository repository) {
		synchronized (repository) {
			try {
				oldCaches.put(repository, DirCache.read(repository));
			} catch (Exception e) {
				// and now?
			}
		}
	}

	@Override
	public synchronized void dispatch(IndexChangedListener listener) {
		listener.onIndexChanged(this);
	}
}
