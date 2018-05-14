/*
 * Copyright (C) 2008-2009, Google Inc.
 * Copyright (C) 2009, Robin Rosenberg <robin.rosenberg@dewire.com>
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

package org.eclipse.jgit.merge;

import java.io.IOException;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Repository;

/**
 * Trivial merge strategy to make the resulting tree exactly match an input.
 * <p>
 * This strategy can be used to cauterize an entire side branch of history, by
 * setting the output tree to one of the inputs, and ignoring any of the paths
 * of the other inputs.
 */
public class StrategyOneSided extends MergeStrategy {
	private final String strategyName;

	private final int treeIndex;

	/**
	 * Create a new merge strategy to select a specific input tree.
	 *
	 * @param name
	 *            name of this strategy.
	 * @param index
	 *            the position of the input tree to accept as the result.
	 */
	protected StrategyOneSided(String name, int index) {
		strategyName = name;
		treeIndex = index;
	}

	/** {@inheritDoc} */
	@Override
	public String getName() {
		return strategyName;
	}

	/** {@inheritDoc} */
	@Override
	public Merger newMerger(Repository db) {
		return new OneSide(db, treeIndex);
	}

	/** {@inheritDoc} */
	@Override
	public Merger newMerger(Repository db, boolean inCore) {
		return new OneSide(db, treeIndex);
	}

	/** {@inheritDoc} */
	@Override
	public Merger newMerger(ObjectInserter inserter, Config config) {
		return new OneSide(inserter, treeIndex);
	}

	static class OneSide extends Merger {
		private final int treeIndex;

		protected OneSide(Repository local, int index) {
			super(local);
			treeIndex = index;
		}

		protected OneSide(ObjectInserter inserter, int index) {
			super(inserter);
			treeIndex = index;
		}

		@Override
		protected boolean mergeImpl() throws IOException {
			return treeIndex < sourceTrees.length;
		}

		@Override
		public ObjectId getResultTreeId() {
			return sourceTrees[treeIndex];
		}

		@Override
		public ObjectId getBaseCommitId() {
			return null;
		}
	}
}
