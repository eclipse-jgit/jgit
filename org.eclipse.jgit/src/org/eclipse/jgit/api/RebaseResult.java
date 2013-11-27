/*
 * Copyright (C) 2010, 2013, Mathias Kinzler <mathias.kinzler@sap.com>
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

import org.eclipse.jgit.merge.ResolveMerger;
import org.eclipse.jgit.merge.ResolveMerger.MergeFailureReason;
import org.eclipse.jgit.revwalk.RevCommit;

/**
 * The result of a {@link RebaseCommand} execution
 */
public class RebaseResult {
	/**
	 * The overall status
	 */
	public enum Status {
		/**
		 * Rebase was successful, HEAD points to the new commit
		 */
		OK {
			@Override
			public boolean isSuccessful() {
				return true;
			}
		},
		/**
		 * Aborted; the original HEAD was restored
		 */
		ABORTED {
			@Override
			public boolean isSuccessful() {
				return false;
			}
		},
		/**
		 * Stopped due to a conflict; must either abort or resolve or skip
		 */
		STOPPED {
			@Override
			public boolean isSuccessful() {
				return false;
			}
		},
		/**
		 * Stopped for editing in the context of an interactive rebase
		 *
		 * @since 3.2
		 */
		EDIT {
			@Override
			public boolean isSuccessful() {
				return false;
			}
		},
		/**
		 * Failed; the original HEAD was restored
		 */
		FAILED {
			@Override
			public boolean isSuccessful() {
				return false;
			}
		},
		/**
		 * The repository contains uncommitted changes and the rebase is not a
		 * fast-forward
		 * 
		 * @since 3.2
		 */
		UNCOMMITTED_CHANGES {
			@Override
			public boolean isSuccessful() {
				return false;
			}
		},
		/**
		 * Conflicts: checkout of target HEAD failed
		 */
		CONFLICTS {
			@Override
			public boolean isSuccessful() {
				return false;
			}
		},
		/**
		 * Already up-to-date
		 */
		UP_TO_DATE {
			@Override
			public boolean isSuccessful() {
				return true;
			}
		},
		/**
		 * Fast-forward, HEAD points to the new commit
		 */
		FAST_FORWARD {
			@Override
			public boolean isSuccessful() {
				return true;
			}
		},

		/**
		 * Continue with nothing left to commit (possibly want skip).
		 *
		 * @since 2.0
		 */
		NOTHING_TO_COMMIT {
			@Override
			public boolean isSuccessful() {
				return false;
			}
		},

		/**
		 * Interactive rebase has been prepared
		 * @since 3.2
		 */
		INTERACTIVE_PREPARED {
			@Override
			public boolean isSuccessful() {
				return false;
			}
		};

		/**
		 * @return whether the status indicates a successful result
		 */
		public abstract boolean isSuccessful();
	}

	static final RebaseResult OK_RESULT = new RebaseResult(Status.OK);

	static final RebaseResult ABORTED_RESULT = new RebaseResult(Status.ABORTED);

	static final RebaseResult UP_TO_DATE_RESULT = new RebaseResult(
			Status.UP_TO_DATE);

	static final RebaseResult FAST_FORWARD_RESULT = new RebaseResult(
			Status.FAST_FORWARD);

	static final RebaseResult NOTHING_TO_COMMIT_RESULT = new RebaseResult(
			Status.NOTHING_TO_COMMIT);

	static final RebaseResult INTERACTIVE_PREPARED_RESULT =  new RebaseResult(
			Status.INTERACTIVE_PREPARED);

	private final Status status;

	private final RevCommit currentCommit;

	private Map<String, MergeFailureReason> failingPaths;

	private List<String> conflicts;

	private List<String> uncommittedChanges;

	private RebaseResult(Status status) {
		this.status = status;
		currentCommit = null;
	}

	private RebaseResult(Status status, RevCommit commit) {
		this.status = status;
		currentCommit = commit;
	}

	/**
	 * Create <code>RebaseResult</code>
	 *
	 * @param status
	 * @param commit
	 *            current commit
	 * @return the RebaseResult
	 */
	static RebaseResult result(RebaseResult.Status status,
			RevCommit commit) {
		return new RebaseResult(status, commit);
	}

	/**
	 * Create <code>RebaseResult</code> with status {@link Status#FAILED}
	 *
	 * @param failingPaths
	 *            list of paths causing this rebase to fail
	 * @return the RebaseResult
	 */
	static RebaseResult failed(
			Map<String, MergeFailureReason> failingPaths) {
		RebaseResult result = new RebaseResult(Status.FAILED);
		result.failingPaths = failingPaths;
		return result;
	}

	/**
	 * Create <code>RebaseResult</code> with status {@link Status#CONFLICTS}
	 *
	 * @param conflicts
	 *            the list of conflicting paths
	 * @return the RebaseResult
	 */
	static RebaseResult conflicts(List<String> conflicts) {
		RebaseResult result = new RebaseResult(Status.CONFLICTS);
		result.conflicts = conflicts;
		return result;
	}

	/**
	 * Create <code>RebaseResult</code> with status
	 * {@link Status#UNCOMMITTED_CHANGES}
	 *
	 * @param uncommittedChanges
	 *            the list of paths
	 * @return the RebaseResult
	 */
	static RebaseResult uncommittedChanges(List<String> uncommittedChanges) {
		RebaseResult result = new RebaseResult(Status.UNCOMMITTED_CHANGES);
		result.uncommittedChanges = uncommittedChanges;
		return result;
	}

	/**
	 * @return the overall status
	 */
	public Status getStatus() {
		return status;
	}

	/**
	 * @return the current commit if status is {@link Status#STOPPED}, otherwise
	 *         <code>null</code>
	 */
	public RevCommit getCurrentCommit() {
		return currentCommit;
	}

	/**
	 * @return the list of paths causing this rebase to fail (see
	 *         {@link ResolveMerger#getFailingPaths()} for details) if status is
	 *         {@link Status#FAILED}, otherwise <code>null</code>
	 */
	public Map<String, MergeFailureReason> getFailingPaths() {
		return failingPaths;
	}

	/**
	 * @return the list of conflicts if status is {@link Status#CONFLICTS}
	 */
	public List<String> getConflicts() {
		return conflicts;
	}

	/**
	 * @return the list of uncommitted changes if status is
	 *         {@link Status#UNCOMMITTED_CHANGES}
	 * 
	 * @since 3.2
	 */
	public List<String> getUncommittedChanges() {
		return uncommittedChanges;
	}

}
