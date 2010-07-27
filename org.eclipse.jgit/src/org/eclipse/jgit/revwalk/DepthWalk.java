/*
 * Copyright (C) 2010, Garmin International
 * Copyright (C) 2010, Matt Fischer <matt.fischer@garmin.com>
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

package org.eclipse.jgit.revwalk;

import java.io.IOException;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;

/**
 * Interface for revision walkers which perform depth filtering
 */
public interface DepthWalk {

	/**
	 * Comparison modes for depth testing
	 */
	public enum CompareMode {
		/** Commits must be exactly equal to the specified depth */
		EQUAL,

		/** Commits must be less than or equal to the specified depth */
		LESS_THAN_EQUAL
	}

	/**
	 * @return Depth to filter to
	 */
	public int getDepth();

	/**
	 * @return Comparison mode for this walker
	 */
	public CompareMode getCompareMode();

	/**
	 * Wrapper object for RevCommit which adds a depth tag.
	 */
	public class Commit extends RevCommit {

		private int depth;

		/**
		 * @param id
		 */
		protected Commit(AnyObjectId id) {
			super(id);
			depth = Integer.MAX_VALUE;
		}

		/**
		 * Set the depth of this commit
		 * @param depth Distance to nearest root in commit graph
		 */
		public void setDepth(int depth) {
			this.depth = depth;
		}

		/**
		 * @return The depth of this commit
		 */
		public int getDepth() {
			return depth;
		}
	}

	/**
	 * Subclass of RevWalk which performs depth filtering
	 */
	public class RevWalk extends org.eclipse.jgit.revwalk.RevWalk implements DepthWalk {
		private final int depth;
		private final CompareMode compareMode;

		/**
		 * @param repo Repository to walk
		 * @param depth Maximum depth to return
		 * @param compareMode Comparison mode
		 */
		public RevWalk(Repository repo, int depth, CompareMode compareMode) {
			super(repo);

			this.depth = depth;
			this.compareMode = compareMode;
		}

		/** Mark a commit as a root (i.e., depth 0)
		 * @param c Commit to mark
		 * @throws MissingObjectException
		 * @throws IncorrectObjectTypeException
		 * @throws IOException
		 */
		public void markStart(RevCommit c) throws MissingObjectException,
		IncorrectObjectTypeException, IOException {
			if (c instanceof Commit) {
				((Commit)c).setDepth(0);
			}

			super.markStart(c);
		}

		@Override
		protected RevCommit createCommit(final AnyObjectId id) {
			// Wrap this commit with a depth tag
			return new Commit(id);
		}

		public int getDepth() {
			return depth;
		}

		public CompareMode getCompareMode() {
			return compareMode;
		}
	}

	/**
	 * Subclass of ObjectWalk which performs depth filtering
	 */
	public class ObjectWalk extends org.eclipse.jgit.revwalk.ObjectWalk implements DepthWalk {
		private final int depth;
		private final CompareMode compareMode;

		/**
		 * @param repo Repository to walk
		 * @param depth Maximum depth to return
		 * @param compareMode Comparison mode
		 */
		public ObjectWalk(Repository repo, int depth, CompareMode compareMode) {
			super(repo);

			this.depth = depth;
			this.compareMode = compareMode;
		}

		/**
		 * @param or Object Reader
		 * @param depth Maximum depth to return
		 * @param compareMode Comparison mode
		 */
		public ObjectWalk(ObjectReader or, int depth, CompareMode compareMode) {
			super(or);

			this.depth = depth;
			this.compareMode = compareMode;
		}

		/** Mark a commit as a root (i.e., depth 0)
		 * @param c Commit to mark
		 * @throws MissingObjectException
		 * @throws IncorrectObjectTypeException
		 * @throws IOException
		 */
		public void markStart(RevObject c) throws MissingObjectException,
		IncorrectObjectTypeException, IOException {
			if (c instanceof Commit) {
				((Commit)c).setDepth(0);
			}

			super.markStart(c);
		}

		@Override
		protected RevCommit createCommit(final AnyObjectId id) {
			// Wrap this commit with a depth tag
			return new Commit(id);
		}

		public int getDepth() {
			return depth;
		}

		public CompareMode getCompareMode() {
			return compareMode;
		}
	}
}
