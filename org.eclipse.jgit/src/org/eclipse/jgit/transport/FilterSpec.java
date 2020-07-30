/*
 * Copyright (C) 2019, Google LLC. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.transport;

import static org.eclipse.jgit.transport.GitProtocolConstants.OPTION_FILTER;

import java.text.MessageFormat;

import org.eclipse.jgit.annotations.Nullable;
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

	private final long treeDepthLimit;

	private FilterSpec(long blobLimit, long treeDepthLimit) {
		this.blobLimit = blobLimit;
		this.treeDepthLimit = treeDepthLimit;
	}

	/**
	 * Process the content of "filter" line from the protocol. It has a shape
	 * like:
	 *
	 * <ul>
	 *   <li>"blob:none"
	 *   <li>"blob:limit=N", with N &gt;= 0
	 *   <li>"tree:DEPTH", with DEPTH &gt;= 0
	 * </ul>
	 *
	 * @param filterLine
	 *            the content of the "filter" line in the protocol
	 * @return a FilterSpec representing the given filter
	 * @throws PackProtocolException
	 *             invalid filter because due to unrecognized format or
	 *             negative/non-numeric filter.
	 */
	public static FilterSpec fromFilterLine(String filterLine)
			throws PackProtocolException {
		if (filterLine.equals("blob:none")) { //$NON-NLS-1$
			return FilterSpec.withBlobLimit(0);
		} else if (filterLine.startsWith("blob:limit=")) { //$NON-NLS-1$
			long blobLimit = -1;
			try {
				blobLimit = Long
						.parseLong(filterLine.substring("blob:limit=".length())); //$NON-NLS-1$
			} catch (NumberFormatException e) {
				// Do not change blobLimit so that we throw a
				// PackProtocolException later.
			}
			if (blobLimit >= 0) {
				return FilterSpec.withBlobLimit(blobLimit);
			}
		} else if (filterLine.startsWith("tree:")) { //$NON-NLS-1$
			long treeDepthLimit = -1;
			try {
				treeDepthLimit = Long
						.parseLong(filterLine.substring("tree:".length())); //$NON-NLS-1$
			} catch (NumberFormatException e) {
				// Do not change blobLimit so that we throw a
				// PackProtocolException later.
			}
			if (treeDepthLimit >= 0) {
				return FilterSpec.withTreeDepthLimit(treeDepthLimit);
			}
		}

		// Did not match any known filter format.
		throw new PackProtocolException(
				MessageFormat.format(JGitText.get().invalidFilter, filterLine));
	}

	/**
	 * @param blobLimit
	 *            the blob limit in a "blob:[limit]" or "blob:none" filter line
	 * @return a filter spec which filters blobs above a certain size
	 */
	static FilterSpec withBlobLimit(long blobLimit) {
		if (blobLimit < 0) {
			throw new IllegalArgumentException(
					"blobLimit cannot be negative: " + blobLimit); //$NON-NLS-1$
		}
		return new FilterSpec(blobLimit, -1);
	}

	/**
	 * @param treeDepthLimit
	 *            the tree depth limit in a "tree:[depth]" filter line
	 * @return a filter spec which filters blobs and trees beyond a certain tree
	 *         depth
	 */
	static FilterSpec withTreeDepthLimit(long treeDepthLimit) {
		if (treeDepthLimit < 0) {
			throw new IllegalArgumentException(
					"treeDepthLimit cannot be negative: " + treeDepthLimit); //$NON-NLS-1$
		}
		return new FilterSpec(-1, treeDepthLimit);
	}

	/**
	 * A placeholder that indicates no filtering.
	 */
	public static final FilterSpec NO_FILTER = new FilterSpec(-1, -1);

	/**
	 * @return -1 if this filter does not filter blobs based on size, or a
	 *         non-negative integer representing the max size of blobs to allow
	 */
	public long getBlobLimit() {
		return blobLimit;
	}

	/**
	 * @return -1 if this filter does not filter blobs and trees based on depth,
	 *         or a non-negative integer representing the max tree depth of
	 *         blobs and trees to fetch
	 */
	public long getTreeDepthLimit() {
		return treeDepthLimit;
	}

	/**
	 * @return true if this filter doesn't filter out anything
	 */
	public boolean isNoOp() {
		return blobLimit == -1 && treeDepthLimit == -1;
	}

	/**
	 * @return the filter line which describes this spec, e.g. "filter blob:limit=42"
	 */
	@Nullable
	public String filterLine() {
		if (isNoOp()) {
			return null;
		} else if (blobLimit == 0 && treeDepthLimit == -1) {
			return OPTION_FILTER + " blob:none"; //$NON-NLS-1$
		} else if (blobLimit > 0 && treeDepthLimit == -1) {
			return OPTION_FILTER + " blob:limit=" + blobLimit; //$NON-NLS-1$
		} else if (blobLimit == -1 && treeDepthLimit >= 0) {
			return OPTION_FILTER + " tree:" + treeDepthLimit; //$NON-NLS-1$
		} else {
			throw new IllegalStateException();
		}
	}
}
