/*
 * Copyright (C) 2016, Google Inc.
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

package org.eclipse.jgit.transport;

import org.eclipse.jgit.lib.Constants;

/**
 * Statistics about {@link PackParser}.
 *
 * @since 4.6
 */
public class ReceivedPackStatistics {
	private long numBytesRead;

	private long numWholeCommit;
	private long numWholeTree;
	private long numWholeBlob;
	private long numWholeTag;
	private long numOfsDelta;
	private long numRefDelta;

	private long numDeltaCommit;
	private long numDeltaTree;
	private long numDeltaBlob;
	private long numDeltaTag;

	/** @return number of bytes read from the input stream */
	public long getNumBytesRead() {
		return numBytesRead;
	}

	/** @return number of whole commit objects in the pack */
	public long getNumWholeCommit() {
		return numWholeCommit;
	}

	/** @return number of whole tree objects in the pack */
	public long getNumWholeTree() {
		return numWholeTree;
	}

	/** @return number of whole blob objects in the pack */
	public long getNumWholeBlob() {
		return numWholeBlob;
	}

	/** @return number of whole tag objects in the pack */
	public long getNumWholeTag() {
		return numWholeTag;
	}

	/** @return number of offset delta objects in the pack */
	public long getNumOfsDelta() {
		return numOfsDelta;
	}

	/** @return number of ref delta objects in the pack */
	public long getNumRefDelta() {
		return numRefDelta;
	}

	/** @return number of delta commit objects in the pack */
	public long getNumDeltaCommit() {
		return numDeltaCommit;
	}

	/** @return number of delta tree objects in the pack */
	public long getNumDeltaTree() {
		return numDeltaTree;
	}

	/** @return number of delta blob objects in the pack */
	public long getNumDeltaBlob() {
		return numDeltaBlob;
	}

	/** @return number of delta tag objects in the pack */
	public long getNumDeltaTag() {
		return numDeltaTag;
	}

	/** A builder for {@link ReceivedPackStatistics}. */
	public static class Builder {
		private long numBytesRead;

		private long numWholeCommit;
		private long numWholeTree;
		private long numWholeBlob;
		private long numWholeTag;
		private long numOfsDelta;
		private long numRefDelta;

		private long numDeltaCommit;
		private long numDeltaTree;
		private long numDeltaBlob;
		private long numDeltaTag;

		/**
		 * @param numBytesRead number of bytes read from the input stream
		 * @return this
		 */
		public Builder setNumBytesRead(long numBytesRead) {
			this.numBytesRead = numBytesRead;
			return this;
		}

		/**
		 * Increment a whole object count.
		 *
		 * @param type OBJ_COMMIT, OBJ_TREE, OBJ_BLOB, or OBJ_TAG
		 * @return this
		 */
		public Builder addWholeObject(int type) {
			switch (type) {
				case Constants.OBJ_COMMIT:
					numWholeCommit++;
					break;
				case Constants.OBJ_TREE:
					numWholeTree++;
					break;
				case Constants.OBJ_BLOB:
					numWholeBlob++;
					break;
				case Constants.OBJ_TAG:
					numWholeTag++;
					break;
				default:
					throw new IllegalArgumentException(
							type + " cannot be a whole object"); //$NON-NLS-1$
			}
			return this;
		}

		/** @return this */
		public Builder addOffsetDelta() {
			numOfsDelta++;
			return this;
		}

		/** @return this */
		public Builder addRefDelta() {
			numRefDelta++;
			return this;
		}

		/**
		 * Increment a delta object count.
		 *
		 * @param type OBJ_COMMIT, OBJ_TREE, OBJ_BLOB, or OBJ_TAG
		 * @return this
		 */
		public Builder addDeltaObject(int type) {
			switch (type) {
				case Constants.OBJ_COMMIT:
					numDeltaCommit++;
					break;
				case Constants.OBJ_TREE:
					numDeltaTree++;
					break;
				case Constants.OBJ_BLOB:
					numDeltaBlob++;
					break;
				case Constants.OBJ_TAG:
					numDeltaTag++;
					break;
				default:
					throw new IllegalArgumentException(
							"delta should be a delta to a whole object. " + //$NON-NLS-1$
							type + " cannot be a whole object"); //$NON-NLS-1$
			}
			return this;
		}

		ReceivedPackStatistics build() {
			ReceivedPackStatistics s = new ReceivedPackStatistics();
			s.numBytesRead = numBytesRead;
			s.numWholeCommit = numWholeCommit;
			s.numWholeTree = numWholeTree;
			s.numWholeBlob = numWholeBlob;
			s.numWholeTag = numWholeTag;
			s.numOfsDelta = numOfsDelta;
			s.numRefDelta = numRefDelta;
			s.numDeltaCommit = numDeltaCommit;
			s.numDeltaTree = numDeltaTree;
			s.numDeltaBlob = numDeltaBlob;
			s.numDeltaTag = numDeltaTag;
			return s;
		}
	}
}
