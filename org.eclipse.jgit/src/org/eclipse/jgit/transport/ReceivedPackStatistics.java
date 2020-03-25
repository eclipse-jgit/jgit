/*
 * Copyright (C) 2016, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.transport;

import org.eclipse.jgit.lib.Constants;

/**
 * Statistics about {@link org.eclipse.jgit.transport.PackParser}.
 *
 * @since 4.6
 */
public class ReceivedPackStatistics {
	private long numBytesRead;
	private long numBytesDuplicated;

	private long numWholeCommit;
	private long numWholeTree;
	private long numWholeBlob;
	private long numWholeTag;
	private long numOfsDelta;
	private long numRefDelta;
	private long numObjectsDuplicated;

	private long numDeltaCommit;
	private long numDeltaTree;
	private long numDeltaBlob;
	private long numDeltaTag;

	/**
	 * Get number of bytes read from the input stream
	 *
	 * @return number of bytes read from the input stream
	 */
	public long getNumBytesRead() {
		return numBytesRead;
	}

	/**
	 * Get number of bytes of objects already in the local database
	 *
	 * @return number of bytes of objects appeared in both the pack sent by the
	 *         client and the local database
	 * @since 5.10
	 */
	public long getNumBytesDuplicated() {
		return numBytesDuplicated;
	}

	/**
	 * Get number of whole commit objects in the pack
	 *
	 * @return number of whole commit objects in the pack
	 */
	public long getNumWholeCommit() {
		return numWholeCommit;
	}

	/**
	 * Get number of whole tree objects in the pack
	 *
	 * @return number of whole tree objects in the pack
	 */
	public long getNumWholeTree() {
		return numWholeTree;
	}

	/**
	 * Get number of whole blob objects in the pack
	 *
	 * @return number of whole blob objects in the pack
	 */
	public long getNumWholeBlob() {
		return numWholeBlob;
	}

	/**
	 * Get number of whole tag objects in the pack
	 *
	 * @return number of whole tag objects in the pack
	 */
	public long getNumWholeTag() {
		return numWholeTag;
	}

	/**
	 * Get number of offset delta objects in the pack
	 *
	 * @return number of offset delta objects in the pack
	 */
	public long getNumOfsDelta() {
		return numOfsDelta;
	}

	/**
	 * Get number of ref delta objects in the pack
	 *
	 * @return number of ref delta objects in the pack
	 */
	public long getNumRefDelta() {
		return numRefDelta;
	}

	/**
	 * Get number of objects already in the local database
	 *
	 * @return number of objects appeared in both the pack sent by the client
	 *         and the local database
	 * @since 5.10
	 */
	public long getNumObjectsDuplicated() {
		return numObjectsDuplicated;
	}

	/**
	 * Get number of delta commit objects in the pack
	 *
	 * @return number of delta commit objects in the pack
	 */
	public long getNumDeltaCommit() {
		return numDeltaCommit;
	}

	/**
	 * Get number of delta tree objects in the pack
	 *
	 * @return number of delta tree objects in the pack
	 */
	public long getNumDeltaTree() {
		return numDeltaTree;
	}

	/**
	 * Get number of delta blob objects in the pack
	 *
	 * @return number of delta blob objects in the pack
	 */
	public long getNumDeltaBlob() {
		return numDeltaBlob;
	}

	/**
	 * Get number of delta tag objects in the pack
	 *
	 * @return number of delta tag objects in the pack
	 */
	public long getNumDeltaTag() {
		return numDeltaTag;
	}

	/** A builder for {@link ReceivedPackStatistics}. */
	public static class Builder {
		private long numBytesRead;
		private long numBytesDuplicated;

		private long numWholeCommit;
		private long numWholeTree;
		private long numWholeBlob;
		private long numWholeTag;
		private long numOfsDelta;
		private long numRefDelta;
		private long numObjectsDuplicated;

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
		 * @param size
		 *            additional bytes already in the local database
		 * @return this
		 * @since 5.10
		 */
		Builder incrementNumBytesDuplicated(long size) {
			numBytesDuplicated += size;
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
		 * Increment the duplicated object count.
		 *
		 * @return this
		 * @since 5.10
		 */
		Builder incrementObjectsDuplicated() {
			numObjectsDuplicated++;
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
			s.numBytesDuplicated = numBytesDuplicated;
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
			s.numObjectsDuplicated = numObjectsDuplicated;
			return s;
		}
	}
}
