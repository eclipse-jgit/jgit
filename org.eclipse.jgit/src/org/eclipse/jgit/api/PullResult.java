/*
 * Copyright (C) 2010, Mathias Kinzler <mathias.kinzler@sap.com>
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
package org.eclipse.jgit.api;

import org.eclipse.jgit.transport.FetchResult;

/**
 * Encapsulates the result of a {@link PullCommand}
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
	 * @return the fetch result, or <code>null</code>
	 */
	public FetchResult getFetchResult() {
		return this.fetchResult;
	}

	/**
	 * @return the merge result, or <code>null</code>
	 */
	public MergeResult getMergeResult() {
		return this.mergeResult;
	}

	/**
	 * @return the rebase result, or <code>null</code>
	 */
	public RebaseResult getRebaseResult() {
		return this.rebaseResult;
	}

	/**
	 * @return the name of the remote configuration from which fetch was tried,
	 *         or <code>null</code>
	 */
	public String getFetchedFrom() {
		return this.fetchedFrom;
	}

	/**
	 * @return whether the pull was successful
	 */
	public boolean isSuccessful() {
		if (mergeResult != null)
			return mergeResult.getMergeStatus().isSuccessful();
		else if (rebaseResult != null)
			return rebaseResult.getStatus().isSuccessful();
		return true;
	}

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
