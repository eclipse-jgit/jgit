/*
 * Copyright (C) 2010, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
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
	 * Create a large object exception, where the object isn't known.
	 *
	 * @param cause
	 *            the cause
	 * @since 4.10
	 */
	public LargeObjectException(Throwable cause) {
		initCause(cause);
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
