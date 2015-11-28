/*
 * Copyright (C) 2015, Google Inc.
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

package org.eclipse.jgit.internal.storage.reftree;

import static org.eclipse.jgit.internal.storage.reftree.RefTreeDb.R_TXN;
import static org.eclipse.jgit.internal.storage.reftree.RefTreeDb.R_TXN_COMMITTED;
import static org.eclipse.jgit.transport.ReceiveCommand.Result.NOT_ATTEMPTED;
import static org.eclipse.jgit.transport.ReceiveCommand.Result.OK;
import static org.eclipse.jgit.transport.ReceiveCommand.Result.REJECTED_OTHER_REASON;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TreeFormatter;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;

/** Batch update a {@link RefTreeDb}. */
class Batch extends BatchRefUpdate {
	private final RefTreeDb refdb;
	private Ref src;
	private ObjectId parentId;
	private ObjectId parentTree;
	private RefTree tree;
	private PersonIdent author;
	private ObjectId nextId;

	Batch(RefTreeDb refdb) {
		super(refdb);
		this.refdb = refdb;
	}

	@Override
	public void execute(RevWalk rw, ProgressMonitor monitor)
			throws IOException {
		List<Command> forTree = new ArrayList<>(getCommands().size());
		List<ReceiveCommand> forStore = new ArrayList<>();
		for (ReceiveCommand c : getCommands()) {
			if (c.getRefName().startsWith(R_TXN)) {
				forStore.add(c);
			} else {
				forTree.add(new Command(rw, c));
			}
		}

		ReceiveCommand commit = null;
		if (!forTree.isEmpty()) {
			init(rw);
			if (!apply(forTree)) {
				reject(JGitText.get().transactionAborted);
				return;
			} else if (nextId != null) {
				commit = new ReceiveCommand(parentId, nextId, R_TXN_COMMITTED);
				forStore.add(commit);
			}
		}

		if (!forStore.isEmpty()) {
			BatchRefUpdate u = newBootstrapBatch();
			u.addCommand(forStore);
			u.execute(rw, monitor);
			if (commit != null) {
				if (commit.getResult() == OK) {
					for (Command c : forTree) {
						c.setResult(OK);
					}
				} else {
					String msg = commit.getMessage();
					if (msg != null) {
						msg = commit.getResult().name();
					}
					reject(msg);
				}
			}
		}
	}

	private BatchRefUpdate newBootstrapBatch() {
		BatchRefUpdate u = refdb.getBootstrap().newBatchUpdate();
		u.setAllowNonFastForwards(isAllowNonFastForwards());
		u.setPushCertificate(getPushCertificate());
		if (isRefLogDisabled()) {
			u.disableRefLog();
		} else {
			u.setRefLogIdent(author != null ? author : getRefLogIdent());
			u.setRefLogMessage(
					getRefLogMessage(),
					isRefLogIncludingResult());
		}
		return u;
	}

	void init(RevWalk rw) throws IOException {
		src = refdb.exactRef(R_TXN_COMMITTED);
		if (src != null && src.getObjectId() != null) {
			RevCommit c = rw.parseCommit(src.getObjectId());
			parentId = c;
			parentTree = c.getTree();
			tree = RefTree.readTree(rw.getObjectReader(), parentTree);
		} else {
			tree = RefTree.newEmptyTree();
			parentId = ObjectId.zeroId();
			parentTree = new ObjectInserter.Formatter()
					.idFor(new TreeFormatter());
		}
	}

	@Nullable
	Ref exactRef(String name) throws IOException {
		return tree.exactRef(name);
	}

	void execute(RevWalk rw, List<Command> todo) throws IOException {
		if (apply(todo) && nextId != null) {
			commit(rw);
		}
	}

	private boolean apply(List<Command> todo) throws IOException {
		if (!tree.apply(todo)) {
			// apply set rejection information on commands.
			return false;
		}

		Repository repo = refdb.getRepository();
		try (ObjectInserter ins = repo.newObjectInserter()) {
			CommitBuilder b = new CommitBuilder();
			b.setTreeId(tree.writeTree(ins));
			if (parentTree.equals(b.getTreeId())) {
				for (Command c : todo) {
					c.setResult(OK);
				}
				return true;
			}
			if (!parentId.equals(ObjectId.zeroId())) {
				b.setParentId(parentId);
			}

			author = getRefLogIdent();
			if (author == null) {
				author = new PersonIdent(repo);
			}
			b.setAuthor(author);
			b.setCommitter(author);
			b.setMessage(getRefLogMessage());
			nextId = ins.insert(b);
			ins.flush();
		}
		return true;
	}

	private void commit(RevWalk rw) throws IOException {
		RefUpdate u = refdb.newUpdate(R_TXN_COMMITTED, false);
		u.setExpectedOldObjectId(parentId);
		u.setNewObjectId(nextId);
		u.setRefLogIdent(author);
		u.setRefLogMessage("commit", false); //$NON-NLS-1$
		Result result = u.update(rw);
		switch (result) {
		case NEW:
		case FAST_FORWARD:
		case NO_CHANGE:
			for (ReceiveCommand c : getCommands()) {
				c.setResult(OK);
			}
			break;

		default:
			reject(result.name());
			break;
		}
	}

	private void reject(String msg) {
		for (ReceiveCommand c : getCommands()) {
			if (c.getResult() == NOT_ATTEMPTED) {
				c.setResult(REJECTED_OTHER_REASON, msg);
				msg = JGitText.get().transactionAborted;
			}
		}
	}
}
