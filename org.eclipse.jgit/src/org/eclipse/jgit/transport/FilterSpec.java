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

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

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

	private final static String COMBINE = "combine:"; //$NON-NLS-1$

	private final static String BLOB_NONE = "blob:none"; //$NON-NLS-1$

	private final static String BLOB_LIMIT = "blob:limit="; //$NON-NLS-1$

	private final static String TREE = "tree:"; //$NON-NLS-1$

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
	 * <li>"blob:none"
	 * <li>"blob:limit=N", with N &gt;= 0
	 * <li>"tree:DEPTH", with DEPTH &gt;= 0
	 * <li>"combine:&gt;filter&lt;+&gt;filter&lt; , (since 5.8)
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

		String[] filters = filterLine.startsWith(COMBINE)
				? filterLine.substring(COMBINE.length()).split("\\+") //$NON-NLS-1$
				: new String[] { filterLine };

		long blobLimit = -1;
		long treeDepthLimit = -1;
		for (String filter : filters) {
			if (filter.equals(BLOB_NONE)) {
				blobLimit = 0;
				continue;
			}

			if (filter.startsWith(BLOB_LIMIT)) {
				blobLimit = parsePositive(filter, BLOB_LIMIT.length());
				continue;
			}

			if (filter.startsWith(TREE)) {
				treeDepthLimit = parsePositive(filter, TREE.length());
				continue;
			}

			// Did not match any known filter format.
			throw new PackProtocolException(MessageFormat
					.format(JGitText.get().invalidFilter, filterLine));
		}

		return new FilterSpec(blobLimit, treeDepthLimit);
	}

	private static long parsePositive(String filterSpec, int offset)
			throws PackProtocolException {
		long value;
		try {
			value = Long.parseLong(filterSpec.substring(offset));
			if (value < 0) {
				throw new PackProtocolException(MessageFormat
						.format(JGitText.get().invalidFilter, filterSpec));
			}

			return value;
		} catch (NumberFormatException e) {
			throw new PackProtocolException(MessageFormat
					.format(JGitText.get().invalidFilter, filterSpec));
		}
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
		}

		List<String> filters = new ArrayList<>();
		if (blobLimit == 0) {
			filters.add(BLOB_NONE);
		} else if (blobLimit > 0) {
			filters.add(BLOB_LIMIT + blobLimit);
		}

		if (treeDepthLimit >= 0) {
			filters.add(TREE + treeDepthLimit);
		}

		return GitProtocolConstants.OPTION_FILTER + " " //$NON-NLS-1$
				+ String.join("+", filters); //$NON-NLS-1$
	}
}
