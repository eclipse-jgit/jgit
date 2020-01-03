/*
 * Copyright (C) 2010, Mathias Kinzler <mathias.kinzler@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.api;

import org.eclipse.jgit.transport.FetchResult;

/**
 * Encapsulates the result of a {@link org.eclipse.jgit.api.PullCommand}
 */
public class PullResult {
	private final FetchResult fetchResult;

	private final MergeResult mergeResult;

	private final RebaseResult rebaseResult;

	private final String fetchedFrom;

	PullResult(FetchResult fetchResult, String fetchedFrom,
			MergeResult mergeResult) {
		this.fetchResult = fetchResult;
		this.fetchedFrom = fetchedFrom;
		this.mergeResult = mergeResult;
		this.rebaseResult = null;
	}

	PullResult(FetchResult fetchResult, String fetchedFrom,
			RebaseResult rebaseResult) {
		this.fetchResult = fetchResult;
		this.fetchedFrom = fetchedFrom;
		this.mergeResult = null;
		this.rebaseResult = rebaseResult;
	}

	/**
	 * Get fetch result
	 *
	 * @return the fetch result, or <code>null</code>
	 */
	public FetchResult getFetchResult() {
		return this.fetchResult;
	}

	/**
	 * Get merge result
	 *
	 * @return the merge result, or <code>null</code>
	 */
	public MergeResult getMergeResult() {
		return this.mergeResult;
	}

	/**
	 * Get rebase result
	 *
	 * @return the rebase result, or <code>null</code>
	 */
	public RebaseResult getRebaseResult() {
		return this.rebaseResult;
	}

	/**
	 * Get name of the remote configuration from which fetch was tried
	 *
	 * @return the name of the remote configuration from which fetch was tried,
	 *         or <code>null</code>
	 */
	public String getFetchedFrom() {
		return this.fetchedFrom;
	}

	/**
	 * Whether the pull was successful
	 *
	 * @return whether the pull was successful
	 */
	public boolean isSuccessful() {
		if (mergeResult != null)
			return mergeResult.getMergeStatus().isSuccessful();
		else if (rebaseResult != null)
			return rebaseResult.getStatus().isSuccessful();
		return true;
	}

	/** {@inheritDoc} */
	@SuppressWarnings("nls")
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		if (fetchResult != null)
			sb.append(fetchResult.toString());
		else
			sb.append("No fetch result");
		sb.append("\n");
		if (mergeResult != null)
			sb.append(mergeResult.toString());
		else if (rebaseResult != null)
			sb.append(rebaseResult.toString());
		else
			sb.append("No update result");
		return sb.toString();
	}
}
