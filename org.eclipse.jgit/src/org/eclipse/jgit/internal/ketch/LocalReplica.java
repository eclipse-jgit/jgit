/*
 * Copyright (C) 2016, Google Inc.
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

package org.eclipse.jgit.internal.ketch;

import static org.eclipse.jgit.internal.ketch.KetchConstants.ACCEPTED;
import static org.eclipse.jgit.internal.ketch.KetchConstants.COMMITTED;
import static org.eclipse.jgit.internal.ketch.KetchReplica.CommitMethod.ALL_REFS;
import static org.eclipse.jgit.internal.ketch.KetchReplica.CommitMethod.TXN_COMMITTED;
import static org.eclipse.jgit.lib.RefDatabase.ALL;
import static org.eclipse.jgit.transport.ReceiveCommand.Result.NOT_ATTEMPTED;
import static org.eclipse.jgit.transport.ReceiveCommand.Result.OK;
import static org.eclipse.jgit.transport.ReceiveCommand.Result.REJECTED_OTHER_REASON;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.storage.reftree.RefTreeDatabase;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;

/** Ketch replica running on the same system as the {@link KetchLeader}. */
public class LocalReplica extends KetchReplica {
	/**
	 * Configure a local replica.
	 *
	 * @param leader
	 *            instance this replica follows.
	 * @param name
	 *            unique-ish name identifying this replica for debugging.
	 * @param cfg
	 *            how Ketch should treat the local system.
	 */
	public LocalReplica(KetchLeader leader, String name, ReplicaConfig cfg) {
		super(leader, name, cfg);
	}

	@Override
	protected String describeForLog() {
		return String.format("%s (leader)", getName()); //$NON-NLS-1$
	}

	/**
	 * @param repo
	 *            repository to initialize state from.
	 * @throws IOException
	 *             cannot read repository state.
	 */
	protected void initialize(Repository repo) throws IOException {
		String txnNamespace = getTxnNamespace();
		RefDatabase refdb = repo.getRefDatabase();
		if (refdb instanceof RefTreeDatabase) {
			RefTreeDatabase treeDb = (RefTreeDatabase) refdb;
			if (!txnNamespace.equals(treeDb.getTxnNamespace())) {
				throw new IOException(MessageFormat.format(
						KetchText.get().mismatchedTxnNamespace,
						txnNamespace, treeDb.getTxnNamespace()));
			}
			refdb = treeDb.getBootstrap();
		}
		initialize(refdb.exactRef(
				txnNamespace + ACCEPTED,
				txnNamespace + COMMITTED));
	}

	@Override
	protected void start(final ReplicaPushRequest req) {
		getSystem().getExecutor().execute(new Runnable() {
			@Override
			public void run() {
				try (Repository git = getLeader().openRepository()) {
					try {
						update(git, req);
						req.done(git);
					} catch (Throwable err) {
						req.setException(git, err);
					}
				} catch (IOException err) {
					req.setException(null, err);
				}
			}
		});
	}

	private void update(Repository repo, ReplicaPushRequest req)
			throws IOException {
		RefDatabase refdb = repo.getRefDatabase();
		CommitMethod method = getCommitMethod();

		// Local replica probably uses RefTreeDatabase, the request should
		// be only for the txnNamespace, so drop to the bootstrap layer.
		if (refdb instanceof RefTreeDatabase) {
			if (!isOnlyTxnNamespace(req.getCommands())) {
				return;
			}

			refdb = ((RefTreeDatabase) refdb).getBootstrap();
			method = TXN_COMMITTED;
		}

		BatchRefUpdate batch = refdb.newBatchUpdate();
		batch.setRefLogIdent(getSystem().newCommitter());
		batch.setRefLogMessage("ketch", false); //$NON-NLS-1$
		batch.setAllowNonFastForwards(true);

		// JGit does not execute multiple references atomically.
		// Run everything else first, then accepted (if present),
		// then committed (if present). This ensures an earlier
		// failure will not update these critical references.
		String acceptedRefName = getTxnNamespace() + ACCEPTED;
		String committedRefName = getTxnNamespace() + COMMITTED;

		ReceiveCommand accepted = null;
		ReceiveCommand committed = null;
		for (ReceiveCommand cmd : req.getCommands()) {
			if (acceptedRefName.equals(cmd.getRefName())) {
				accepted = cmd;
			} else if (committedRefName.equals(cmd.getRefName())) {
				committed = cmd;
			} else {
				batch.addCommand(cmd);
			}
		}
		if (committed != null && method == ALL_REFS) {
			Map<String, Ref> refs = refdb.getRefs(ALL);
			batch.addCommand(commit(repo, refs, committed.getNewId()));
		}
		if (accepted != null) {
			batch.addCommand(accepted);
		}
		if (committed != null) {
			batch.addCommand(committed);
		}

		try (RevWalk rw = new RevWalk(repo)) {
			batch.execute(rw, NullProgressMonitor.INSTANCE);
		}

		// KetchReplica only cares about accepted and committed in
		// advertisement. If they failed, read the current value.
		List<String> failed = new ArrayList<>(2);
		checkFailed(failed, accepted);
		checkFailed(failed, committed);
		if (!failed.isEmpty()) {
			String[] arr = failed.toArray(new String[failed.size()]);
			req.setRefs(refdb.exactRef(arr));
		}
	}

	private static void checkFailed(List<String> failed, ReceiveCommand cmd) {
		if (cmd != null && cmd.getResult() != OK) {
			failed.add(cmd.getRefName());
		}
	}

	private boolean isOnlyTxnNamespace(Collection<ReceiveCommand> cmdList) {
		// Be paranoid and reject non txnNamespace names, this
		// is a programming error in Ketch that should not occur.

		String txnNamespace = getTxnNamespace();
		for (ReceiveCommand cmd : cmdList) {
			if (!cmd.getRefName().startsWith(txnNamespace)) {
				cmd.setResult(REJECTED_OTHER_REASON,
						MessageFormat.format(
								KetchText.get().outsideTxnNamespace,
								cmd.getRefName(), txnNamespace));
				abort(cmdList);
				return false;
			}
		}
		return true;
	}

	private static void abort(Collection<ReceiveCommand> cmdList) {
		for (ReceiveCommand c : cmdList) {
			if (c.getResult() == NOT_ATTEMPTED) {
				c.setResult(REJECTED_OTHER_REASON,
						JGitText.get().transactionAborted);
			}
		}
	}
}
