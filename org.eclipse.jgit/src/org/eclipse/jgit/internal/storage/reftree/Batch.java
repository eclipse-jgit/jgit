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

import static org.eclipse.jgit.internal.storage.reftree.RefTreeDb.R_TXN_COMMITTED;
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
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;

/** Batch update a {@link RefTreeDb}. */
class Batch extends BatchRefUpdate {
	private final RefTreeDb refdb;
	private Ref src;
	private ObjectId oldId;
	private RefTree tree;

	Batch(RefTreeDb refdb) {
		super(refdb);
		this.refdb = refdb;
	}

	@Override
	public void execute(RevWalk rw, ProgressMonitor monitor)
			throws IOException {
		List<Command> todo = new ArrayList<>(getCommands().size());
		for (ReceiveCommand c : getCommands()) {
			todo.add(new Command(rw, c));
		}
		init(rw);
		execute(rw, todo);
	}

	void init(RevWalk rw) throws IOException {
		src = refdb.exactRef(R_TXN_COMMITTED);
		if (src != null && src.getObjectId() != null) {
			RevCommit c = rw.parseCommit(src.getObjectId());
			tree = RefTree.read(rw.getObjectReader(), c);
			oldId = c;
		} else {
			tree = RefTree.newEmptyTree();
			oldId = ObjectId.zeroId();
		}
	}

	@Nullable
	Ref getRef(String name) throws IOException {
		return tree.getRef(name);
	}

	void execute(RevWalk rw, List<Command> todo) throws IOException {
		if (!tree.apply(todo)) {
			// apply set rejection information on commands.
			return;
		}

		Repository repo = refdb.getRepository();
		PersonIdent who = getRefLogIdent();
		if (who == null) {
			who = new PersonIdent(repo);
		}

		ObjectId next;
		try (ObjectInserter ins = repo.newObjectInserter()) {
			CommitBuilder b = new CommitBuilder();
			b.setTreeId(tree.writeTree(ins));
			if (!oldId.equals(ObjectId.zeroId())) {
				b.setParentId(oldId);
			}
			b.setAuthor(who);
			b.setCommitter(who);
			b.setMessage(getRefLogMessage());
			next = ins.insert(b);
			ins.flush();
		}

		RefUpdate u = refdb.newUpdate(R_TXN_COMMITTED, false);
		u.setExpectedOldObjectId(oldId);
		u.setNewObjectId(next);
		u.setRefLogIdent(who);
		u.setRefLogMessage("commit", false); //$NON-NLS-1$
		Result result = u.update(rw);
		switch (result) {
		case NEW:
		case FAST_FORWARD:
		case NO_CHANGE:
			refdb.refresh();
			break;

		default:
			reject(todo, result.name());
			break;
		}
	}

	private void reject(List<Command> cmds, String msg) {
		for (Command c : cmds) {
			c.setResult(REJECTED_OTHER_REASON, msg);
			msg = JGitText.get().transactionAborted;
		}
	}
}
