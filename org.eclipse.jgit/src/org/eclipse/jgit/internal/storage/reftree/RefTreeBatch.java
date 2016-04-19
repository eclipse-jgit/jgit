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

package org.eclipse.jgit.internal.storage.reftree;

import static org.eclipse.jgit.lib.Constants.OBJ_TREE;
import static org.eclipse.jgit.transport.ReceiveCommand.Result.NOT_ATTEMPTED;
import static org.eclipse.jgit.transport.ReceiveCommand.Result.OK;
import static org.eclipse.jgit.transport.ReceiveCommand.Result.REJECTED_NONFASTFORWARD;
import static org.eclipse.jgit.transport.ReceiveCommand.Result.REJECTED_OTHER_REASON;
import static org.eclipse.jgit.transport.ReceiveCommand.Type.UPDATE;
import static org.eclipse.jgit.transport.ReceiveCommand.Type.UPDATE_NONFASTFORWARD;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;

/** Batch update a {@link RefTreeDatabase}. */
class RefTreeBatch extends BatchRefUpdate {
	private final RefTreeDatabase refdb;
	private Ref src;
	private ObjectId parentCommitId;
	private ObjectId parentTreeId;
	private RefTree tree;
	private PersonIdent author;
	private ObjectId newCommitId;

	RefTreeBatch(RefTreeDatabase refdb) {
		super(refdb);
		this.refdb = refdb;
	}

	@Override
	public void execute(RevWalk rw, ProgressMonitor monitor)
			throws IOException {
		List<Command> todo = new ArrayList<>(getCommands().size());
		for (ReceiveCommand c : getCommands()) {
			if (!isAllowNonFastForwards()) {
				if (c.getType() == UPDATE) {
					c.updateType(rw);
				}
				if (c.getType() == UPDATE_NONFASTFORWARD) {
					c.setResult(REJECTED_NONFASTFORWARD);
					if (isAtomic()) {
						ReceiveCommand.abort(getCommands());
						return;
					} else {
						continue;
					}
				}
			}
			todo.add(new Command(rw, c));
		}
		init(rw);
		execute(rw, todo);
	}

	void init(RevWalk rw) throws IOException {
		src = refdb.getBootstrap().exactRef(refdb.getTxnCommitted());
		if (src != null && src.getObjectId() != null) {
			RevCommit c = rw.parseCommit(src.getObjectId());
			parentCommitId = c;
			parentTreeId = c.getTree();
			tree = RefTree.read(rw.getObjectReader(), c.getTree());
		} else {
			parentCommitId = ObjectId.zeroId();
			parentTreeId = new ObjectInserter.Formatter()
					.idFor(OBJ_TREE, new byte[] {});
			tree = RefTree.newEmptyTree();
		}
	}

	@Nullable
	Ref exactRef(ObjectReader reader, String name) throws IOException {
		return tree.exactRef(reader, name);
	}

	/**
	 * Execute an update from {@link RefTreeUpdate} or {@link RefTreeRename}.
	 *
	 * @param rw
	 *            current RevWalk handling the update or rename.
	 * @param todo
	 *            commands to execute. Must never be a bootstrap reference name.
	 * @throws IOException
	 *             the storage system is unable to read or write data.
	 */
	void execute(RevWalk rw, List<Command> todo) throws IOException {
		for (Command c : todo) {
			if (c.getResult() != NOT_ATTEMPTED) {
				Command.abort(todo, null);
				return;
			}
			if (refdb.conflictsWithBootstrap(c.getRefName())) {
				c.setResult(REJECTED_OTHER_REASON, MessageFormat
						.format(JGitText.get().invalidRefName, c.getRefName()));
				Command.abort(todo, null);
				return;
			}
		}

		if (apply(todo) && newCommitId != null) {
			commit(rw, todo);
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
			if (parentTreeId.equals(b.getTreeId())) {
				for (Command c : todo) {
					c.setResult(OK);
				}
				return true;
			}
			if (!parentCommitId.equals(ObjectId.zeroId())) {
				b.setParentId(parentCommitId);
			}

			author = getRefLogIdent();
			if (author == null) {
				author = new PersonIdent(repo);
			}
			b.setAuthor(author);
			b.setCommitter(author);
			b.setMessage(getRefLogMessage());
			newCommitId = ins.insert(b);
			ins.flush();
		}
		return true;
	}

	private void commit(RevWalk rw, List<Command> todo) throws IOException {
		ReceiveCommand commit = new ReceiveCommand(
				parentCommitId, newCommitId,
				refdb.getTxnCommitted());
		updateBootstrap(rw, commit);

		if (commit.getResult() == OK) {
			for (Command c : todo) {
				c.setResult(OK);
			}
		} else {
			Command.abort(todo, commit.getResult().name());
		}
	}

	private void updateBootstrap(RevWalk rw, ReceiveCommand commit)
			throws IOException {
		BatchRefUpdate u = refdb.getBootstrap().newBatchUpdate();
		u.setAllowNonFastForwards(true);
		u.setPushCertificate(getPushCertificate());
		if (isRefLogDisabled()) {
			u.disableRefLog();
		} else {
			u.setRefLogIdent(author);
			u.setRefLogMessage(getRefLogMessage(), false);
		}
		u.addCommand(commit);
		u.execute(rw, NullProgressMonitor.INSTANCE);
	}
}
