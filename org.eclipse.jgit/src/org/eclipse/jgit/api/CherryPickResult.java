/*
 * Copyright (C) 2011, Philipp Thun <philipp.thun@sap.com>
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

import java.util.List;
import java.util.Map;

import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.merge.ResolveMerger.MergeFailureReason;
import org.eclipse.jgit.revwalk.RevCommit;

/**
 * Encapsulates the result of a {@link org.eclipse.jgit.api.CherryPickCommand}.
 */
public class CherryPickResult {

	/**
	 * The cherry-pick status
	 */
	public enum CherryPickStatus {
		/** */
		OK {
			@Override
			public String toString() {
				return "Ok"; //$NON-NLS-1$
			}
		},
		/** */
		FAILED {
			@Override
			public String toString() {
				return "Failed"; //$NON-NLS-1$
			}
		},
		/** */
		CONFLICTING {
			@Override
			public String toString() {
				return "Conflicting"; //$NON-NLS-1$
			}
		}
	}

	private final CherryPickStatus status;

	private final RevCommit newHead;

	private final List<Ref> cherryPickedRefs;

	private final Map<String, MergeFailureReason> failingPaths;

	/**
	 * Constructor for CherryPickResult
	 *
	 * @param newHead
	 *            commit the head points at after this cherry-pick
	 * @param cherryPickedRefs
	 *            list of successfully cherry-picked <code>Ref</code>'s
	 */
	public CherryPickResult(RevCommit newHead, List<Ref> cherryPickedRefs) {
		this.status = CherryPickStatus.OK;
		this.newHead = newHead;
		this.cherryPickedRefs = cherryPickedRefs;
		this.failingPaths = null;
	}

	/**
	 * Constructor for CherryPickResult
	 *
	 * @param failingPaths
	 *            list of paths causing this cherry-pick to fail (see
	 *            {@link org.eclipse.jgit.merge.ResolveMerger#getFailingPaths()}
	 *            for details)
	 */
	public CherryPickResult(Map<String, MergeFailureReason> failingPaths) {
		this.status = CherryPickStatus.FAILED;
		this.newHead = null;
		this.cherryPickedRefs = null;
		this.failingPaths = failingPaths;
	}

	private CherryPickResult(CherryPickStatus status) {
		this.status = status;
		this.newHead = null;
		this.cherryPickedRefs = null;
		this.failingPaths = null;
	}

	/**
	 * A <code>CherryPickResult</code> with status
	 * {@link CherryPickStatus#CONFLICTING}
	 */
	public static final CherryPickResult CONFLICT = new CherryPickResult(
			CherryPickStatus.CONFLICTING);

	/**
	 * Get status
	 *
	 * @return the status this cherry-pick resulted in
	 */
	public CherryPickStatus getStatus() {
		return status;
	}

	/**
	 * Get the new head after this cherry-pick
	 *
	 * @return the commit the head points at after this cherry-pick,
	 *         <code>null</code> if {@link #getStatus} is not
	 *         {@link org.eclipse.jgit.api.CherryPickResult.CherryPickStatus#OK}
	 */
	public RevCommit getNewHead() {
		return newHead;
	}

	/**
	 * Get the cherry-picked {@code Ref}s
	 *
	 * @return the list of successfully cherry-picked <code>Ref</code>'s,
	 *         <code>null</code> if {@link #getStatus} is not
	 *         {@link org.eclipse.jgit.api.CherryPickResult.CherryPickStatus#OK}
	 */
	public List<Ref> getCherryPickedRefs() {
		return cherryPickedRefs;
	}

	/**
	 * Get the list of paths causing this cherry-pick to fail
	 *
	 * @return the list of paths causing this cherry-pick to fail (see
	 *         {@link org.eclipse.jgit.merge.ResolveMerger#getFailingPaths()}
	 *         for details), <code>null</code> if {@link #getStatus} is not
	 *         {@link org.eclipse.jgit.api.CherryPickResult.CherryPickStatus#FAILED}
	 */
	public Map<String, MergeFailureReason> getFailingPaths() {
		return failingPaths;
	}
}
