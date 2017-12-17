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

package org.eclipse.jgit.pgm.debug;

import static org.eclipse.jgit.lib.Constants.HEAD;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.internal.storage.reftree.RefTree;
import org.eclipse.jgit.internal.storage.reftree.RefTreeDatabase;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.pgm.Command;
import org.eclipse.jgit.pgm.TextBuiltin;
import org.eclipse.jgit.revwalk.RevWalk;
import org.kohsuke.args4j.Option;

@Command(usage = "usage_RebuildRefTree")
class RebuildRefTree extends TextBuiltin {
	@Option(name = "--enable", usage = "usage_RebuildRefTreeEnable")
	boolean enable;

	private String txnNamespace;
	private String txnCommitted;

	/** {@inheritDoc} */
	@Override
	protected void run() throws Exception {
		try (ObjectReader reader = db.newObjectReader();
				RevWalk rw = new RevWalk(reader);
				ObjectInserter inserter = db.newObjectInserter()) {
			RefDatabase refDb = db.getRefDatabase();
			if (refDb instanceof RefTreeDatabase) {
				RefTreeDatabase d = (RefTreeDatabase) refDb;
				refDb = d.getBootstrap();
				txnNamespace = d.getTxnNamespace();
				txnCommitted = d.getTxnCommitted();
			} else {
				RefTreeDatabase d = new RefTreeDatabase(db, refDb);
				txnNamespace = d.getTxnNamespace();
				txnCommitted = d.getTxnCommitted();
			}

			errw.format("Rebuilding %s from %s", //$NON-NLS-1$
					txnCommitted, refDb.getClass().getSimpleName());
			errw.println();
			errw.flush();

			CommitBuilder b = new CommitBuilder();
			Ref ref = refDb.exactRef(txnCommitted);
			RefUpdate update = refDb.newUpdate(txnCommitted, true);
			ObjectId oldTreeId;

			if (ref != null && ref.getObjectId() != null) {
				ObjectId oldId = ref.getObjectId();
				update.setExpectedOldObjectId(oldId);
				b.setParentId(oldId);
				oldTreeId = rw.parseCommit(oldId).getTree();
			} else {
				update.setExpectedOldObjectId(ObjectId.zeroId());
				oldTreeId = ObjectId.zeroId();
			}

			RefTree tree = rebuild(refDb);
			b.setTreeId(tree.writeTree(inserter));
			b.setAuthor(new PersonIdent(db));
			b.setCommitter(b.getAuthor());
			if (b.getTreeId().equals(oldTreeId)) {
				return;
			}

			update.setNewObjectId(inserter.insert(b));
			inserter.flush();

			RefUpdate.Result result = update.update(rw);
			switch (result) {
			case NEW:
			case FAST_FORWARD:
				break;
			default:
				throw die(String.format("%s: %s", update.getName(), result)); //$NON-NLS-1$
			}

			if (enable && !(db.getRefDatabase() instanceof RefTreeDatabase)) {
				StoredConfig cfg = db.getConfig();
				cfg.setInt("core", null, "repositoryformatversion", 1); //$NON-NLS-1$ //$NON-NLS-2$
				cfg.setString("extensions", null, "refStorage", "reftree"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				cfg.save();
				errw.println("Enabled reftree."); //$NON-NLS-1$
				errw.flush();
			}
		}
	}

	private RefTree rebuild(RefDatabase refdb) throws IOException {
		RefTree tree = RefTree.newEmptyTree();
		List<org.eclipse.jgit.internal.storage.reftree.Command> cmds
			= new ArrayList<>();

		Ref head = refdb.exactRef(HEAD);
		if (head != null) {
			cmds.add(new org.eclipse.jgit.internal.storage.reftree.Command(
					null,
					head));
		}

		for (Ref r : refdb.getRefs(RefDatabase.ALL).values()) {
			if (r.getName().equals(txnCommitted) || r.getName().equals(HEAD)
					|| r.getName().startsWith(txnNamespace)) {
				continue;
			}
			cmds.add(new org.eclipse.jgit.internal.storage.reftree.Command(
					null,
					db.peel(r)));
		}
		tree.apply(cmds);
		return tree;
	}
}
