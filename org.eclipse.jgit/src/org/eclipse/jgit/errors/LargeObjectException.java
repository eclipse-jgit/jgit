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

package org.eclipse.jgit.errors;

import java.text.MessageFormat;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;

/**
 * An object is too big to load into memory as a single byte array.
 */
public class LargeObjectException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	private ObjectId objectId;

	/**
	 * Create a large object exception, where the object isn't known.
	 */
	public LargeObjectException() {
		// Do nothing.
	}

	/**
	 * Create a large object exception, naming the object that is too big.
	 *
	 * @param id
	 *            identity of the object that is too big to be loaded as a byte
	 *            array in this JVM.
	 */
	public LargeObjectException(AnyObjectId id) {
		setObjectId(id);
	}

	/**
	 * Get identity of the object that is too large; may be null
	 *
	 * @return identity of the object that is too large; may be null
	 */
	public ObjectId getObjectId() {
		return objectId;
	}

	/**
	 * Get the hex encoded name of the object, or 'unknown object'
	 *
	 * @return either the hex encoded name of the object, or 'unknown object'
	 */
	protected String getObjectName() {
		if (getObjectId() != null)
			return getObjectId().name();
		return JGitText.get().unknownObject;
	}

	/**
	 * Set the identity of the object, if its not already set.
	 *
	 * @param id
	 *            the id of the object that is too large to process.
	 */
	public void setObjectId(AnyObjectId id) {
		if (objectId == null)
			objectId = id.copy();
	}

	/** {@inheritDoc} */
	@Override
	public String getMessage() {
		return MessageFormat.format(JGitText.get().largeObjectException,
				getObjectName());
	}

	/** An error caused by the JVM being out of heap space. */
	public static class OutOfMemory extends LargeObjectException {
		private static final long serialVersionUID = 1L;

		/**
		 * Construct a wrapper around the original OutOfMemoryError.
		 *
		 * @param cause
		 *            the original root cause.
		 */
		public OutOfMemory(OutOfMemoryError cause) {
			initCause(cause);
		}

		@Override
		public String getMessage() {
			return MessageFormat.format(JGitText.get().largeObjectOutOfMemory,
					getObjectName());
		}
	}

	/** Object size exceeds JVM limit of 2 GiB per byte array. */
	public static class ExceedsByteArrayLimit extends LargeObjectException {
		private static final long serialVersionUID = 1L;

		@Override
		public String getMessage() {
			return MessageFormat
					.format(JGitText.get().largeObjectExceedsByteArray,
							getObjectName());
		}
	}

	/** Object size exceeds the caller's upper limit. */
	public static class ExceedsLimit extends LargeObjectException {
		private static final long serialVersionUID = 1L;

		private final long limit;

		private final long size;

		/**
		 * Construct an exception for a particular size being exceeded.
		 *
		 * @param limit
		 *            the limit the caller imposed on the object.
		 * @param size
		 *            the actual size of the object.
		 */
		public ExceedsLimit(long limit, long size) {
			this.limit = limit;
			this.size = size;
		}

		@Override
		public String getMessage() {
			return MessageFormat.format(JGitText.get().largeObjectExceedsLimit,
					getObjectName(), Long.valueOf(limit), Long.valueOf(size));
		}
	}
}
