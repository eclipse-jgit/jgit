/*
 * Copyright (C) 2019, Google LLC.
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

import java.text.MessageFormat;

import org.eclipse.jgit.errors.PackProtocolException;
import org.eclipse.jgit.internal.JGitText;

/**
 * Represents either a filter specified in a protocol "filter" line, or a
 * placeholder to indicate no filtering.
 *
 * @since 5.4
 */
public final class FilterSpec {

	private final long blobLimit;

	private FilterSpec(long blobLimit) {
		this.blobLimit = blobLimit;
	}

	/*
	 * Process the content of "filter" line from the protocol. It has a shape
	 * like "blob:none" or "blob:limit=N", with limit a positive number.
	 *
	 * @param filterLine
	 *            the content of the "filter" line in the protocol
	 * @return a FilterSpec representing the given filter
	 * @throws PackProtocolException
	 *             invalid filter because due to unrecognized format or
	 *             negative/non-numeric filter.
	 */
	static FilterSpec parseFilterLine(String filterLine)
			throws PackProtocolException {
		long blobLimit = -1;

		if (filterLine.equals("blob:none")) { //$NON-NLS-1$
			blobLimit = 0;
		} else if (filterLine.startsWith("blob:limit=")) { //$NON-NLS-1$
			try {
				blobLimit = Long
						.parseLong(filterLine.substring("blob:limit=".length())); //$NON-NLS-1$
			} catch (NumberFormatException e) {
				throw new PackProtocolException(MessageFormat
						.format(JGitText.get().invalidFilter, filterLine));
			}
		}
		/*
		 * We must have (1) either "blob:none" or "blob:limit=" set (because we
		 * only support blob size limits for now), and (2) if the latter, then
		 * it must be nonnegative. Throw if (1) or (2) is not met.
		 */
		if (blobLimit < 0) {
			throw new PackProtocolException(
					MessageFormat.format(
							JGitText.get().invalidFilter, filterLine));
		}

		return new FilterSpec(blobLimit);
	}

	/**
	 * @param blobLimit
	 *            the blob limit in a "blob:[limit]" or "blob:none" filter line
	 * @return a filter spec which filters blobs above a certain size
	 */
	public static FilterSpec blobFilter(long blobLimit) {
		if (blobLimit < 0) {
			throw new IllegalArgumentException(
					"blobLimit cannot be negative: " + blobLimit); //$NON-NLS-1$
		}
		return new FilterSpec(blobLimit);
	}

	/**
	 * A placeholder that indicates no filtering.
	 */
	public static final FilterSpec NO_OP_FILTER = new FilterSpec(-1);

	/**
	 * @return -1 if this filter does not filter blobs based on size, or a
	 *         non-negative integer representing the max size of blobs to allow
	 */
	public long getBlobLimit() {
		return blobLimit;
	}

	/**
	 * @return true if this filter doesn't filter out anything
	 */
	public boolean isNoOp() {
		return blobLimit == -1;
	}

}
