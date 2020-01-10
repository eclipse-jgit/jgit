/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.internal.op.commands;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import org.eclipse.jgit.internal.storage.reftree.Command;
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
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.niofs.internal.op.Git;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import static org.eclipse.jgit.lib.Constants.HEAD;

public class ConvertRefTree {

	private final Git git;
	private String txnNamespace;
	private String txnCommitted;

	public ConvertRefTree(final Git git) {
		this.git = git;
	}

	public void execute() throws IOException {
		try (ObjectReader reader = git.getRepository().newObjectReader();
				RevWalk rw = new RevWalk(reader);
				ObjectInserter inserter = git.getRepository().newObjectInserter()) {
			RefDatabase refDb = git.getRepository().getRefDatabase();
			if (refDb instanceof RefTreeDatabase) {
				RefTreeDatabase d = (RefTreeDatabase) refDb;
				refDb = d.getBootstrap();
				txnNamespace = d.getTxnNamespace();
				txnCommitted = d.getTxnCommitted();
			} else {
				RefTreeDatabase d = new RefTreeDatabase(git.getRepository(), refDb);
				txnNamespace = d.getTxnNamespace();
				txnCommitted = d.getTxnCommitted();
			}

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
			b.setAuthor(new PersonIdent("system", "system", new Date(1481754897254L), TimeZone.getDefault()));
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
				throw new RuntimeException(String.format("%s: %s", update.getName(), result)); // $NON-NLS-1$
			}

			if (!(git.getRepository().getRefDatabase() instanceof RefTreeDatabase)) {
				StoredConfig cfg = git.getRepository().getConfig();
				cfg.setInt("core", null, "repositoryformatversion", 1); // $NON-NLS-1$ //$NON-NLS-2$
				cfg.setString("extensions", null, "refsStorage", "reftree"); //$NON-NLS-3$ //$NON-NLS-2$ //$NON-NLS-3$
				cfg.save();

				final Repository repo = new FileRepositoryBuilder().setGitDir(git.getRepository().getDirectory())
						.build();
				git.updateRepo(repo);
			}
			final File commited = new File(git.getRepository().getDirectory(), txnCommitted);
			final File accepted = new File(git.getRepository().getDirectory(), txnNamespace + "accepted");
			Files.copy(commited.toPath(), accepted.toPath(), StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private RefTree rebuild(RefDatabase refdb) throws IOException {
		RefTree tree = RefTree.newEmptyTree();
		List<Command> cmds = new ArrayList<>();

		Ref head = refdb.exactRef(HEAD);
		if (head != null) {
			cmds.add(new org.eclipse.jgit.internal.storage.reftree.Command(null, head));
		}

		for (Ref r : refdb.getRefsByPrefix(RefDatabase.ALL)) {
			if (r.getName().equals(txnCommitted) || r.getName().equals(HEAD) || r.getName().startsWith(txnNamespace)) {
				continue;
			}
			cmds.add(new org.eclipse.jgit.internal.storage.reftree.Command(null,
					git.getRepository().getRefDatabase().peel(r)));
		}
		tree.apply(cmds);
		return tree;
	}
}
