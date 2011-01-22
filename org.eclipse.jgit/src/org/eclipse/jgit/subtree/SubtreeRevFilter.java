/*
 * Copyright (C) 2011, Garmin International
 * Copyright (C) 2011, Jesse Greenwald <jesse.greenwald@gmail.com>
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

package org.eclipse.jgit.subtree;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.StopWalkException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;

/**
 * This RevFilter will stop the walker from visiting commits that are determined
 * to be part of a sub tree.
 */
public class SubtreeRevFilter extends RevFilter {

	private HashSet<RevCommit> subTrees;

	private Repository repo;

	private boolean includeBoundarySubtrees;

	private Map<String, SubtreeContext> splitters;

	private SubtreeAnalyzer analyzer;

	/**
	 * @param db
	 *            The repository to walk.
	 */
	public SubtreeRevFilter(Repository db) {
		this.repo = db;
		this.analyzer = new SubtreeAnalyzer(repo);
	}

	void setSplitters(Map<String, SubtreeContext> splitters) {
		this.splitters = splitters;
	}

	void setIncludeBoundarySubtrees(boolean include) {
		this.includeBoundarySubtrees = include;
		if (include) {
			if (subTrees == null) {
				subTrees = new HashSet<RevCommit>();
			}
		} else {
			subTrees = null;
		}
	}

	@Override
	public boolean include(RevWalk walker, RevCommit cmit)
			throws StopWalkException, MissingObjectException,
			IncorrectObjectTypeException, IOException {

		if (includeBoundarySubtrees && subTrees.contains(cmit)) {
			for (RevCommit parent : cmit.getParents()) {
				walker.markUninteresting(parent);
			}
		} else {

			Map<String, RevCommit> subtreeParents = analyzer.getSubtreeParents(
					cmit, walker);

			for (String subtreeName : subtreeParents.keySet()) {

				RevCommit subtreeCommit = walker.parseCommit(subtreeParents
						.get(subtreeName));

				if (includeBoundarySubtrees) {
					subTrees.add(subtreeCommit);
				} else {
					walker.markUninteresting(subtreeCommit);
				}

				// If splitters have been supplied, update them.
				if (splitters != null) {

					// Parse the subtree config for this commit, and add any
					// subtree contexts that may be missing.
					Config conf = SubtreeSplitter.loadSubtreeConfig(repo, cmit);
					for (String name : conf
							.getSubsections(SubtreeSplitter.SUBTREE_SECTION)) {
						if (!splitters.containsKey(name)) {
							splitters.put(name, new NameBasedSubtreeContext(
									name));
						}
					}

					for (SubtreeContext splitter : splitters.values()) {
						splitter.setSplitCommit(subtreeCommit,
								SubtreeSplitter.NO_SUBTREE);
					}
					splitters.get(subtreeName).setSplitCommit(subtreeCommit,
							subtreeCommit);
				}

			}
		}

		return true;
	}

	@Override
	public RevFilter clone() {
		return new SubtreeRevFilter(repo);
	}

	/**
	 * Store any cache data to the db so it can be reused.
	 *
	 * @param walk
	 * @throws IOException
	 */
	void flushCache(RevWalk walk) throws IOException {
		analyzer.flushCache(walk);
	}

	/**
	 * Allow this filter to be reused.
	 */
	public void reset() {
		if (subTrees != null) {
			subTrees.clear();
		}
		splitters = null;
	}

	/**
	 * Release the subtree analyzer.
	 */
	public void release() {
		analyzer.release();
	}

	/**
	 * @return The subtree analyzer.
	 */
	public SubtreeAnalyzer getSubtreeAnalyzer() {
		return analyzer;
	}

}
