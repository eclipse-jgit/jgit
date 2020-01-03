/*
 * Copyright (C) 2016, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.reftree;

import static org.eclipse.jgit.lib.Constants.HEAD;
import static org.eclipse.jgit.lib.RefUpdate.Result.REJECTED;
import static org.eclipse.jgit.lib.RefUpdate.Result.RENAMED;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdRef;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefRename;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.SymbolicRef;
import org.eclipse.jgit.revwalk.RevWalk;

/** Single reference rename to {@link RefTreeDatabase}. */
class RefTreeRename extends RefRename {
	private final RefTreeDatabase refdb;

	RefTreeRename(RefTreeDatabase refdb, RefUpdate src, RefUpdate dst) {
		super(src, dst);
		this.refdb = refdb;
	}

	/** {@inheritDoc} */
	@Override
	protected Result doRename() throws IOException {
		try (RevWalk rw = new RevWalk(refdb.getRepository())) {
			RefTreeBatch batch = new RefTreeBatch(refdb);
			batch.setRefLogIdent(getRefLogIdent());
			batch.setRefLogMessage(getRefLogMessage(), false);
			batch.init(rw);

			Ref head = batch.exactRef(rw.getObjectReader(), HEAD);
			Ref oldRef = batch.exactRef(rw.getObjectReader(), source.getName());
			if (oldRef == null) {
				return REJECTED;
			}

			Ref newRef = asNew(oldRef);
			List<Command> mv = new ArrayList<>(3);
			mv.add(new Command(oldRef, null));
			mv.add(new Command(null, newRef));
			if (head != null && head.isSymbolic()
					&& head.getTarget().getName().equals(oldRef.getName())) {
				mv.add(new Command(
					head,
					new SymbolicRef(head.getName(), newRef)));
			}
			batch.execute(rw, mv);
			return RefTreeUpdate.translate(mv.get(1).getResult(), RENAMED);
		}
	}

	private Ref asNew(Ref src) {
		String name = destination.getName();
		if (src.isSymbolic()) {
			return new SymbolicRef(name, src.getTarget());
		}

		ObjectId peeled = src.getPeeledObjectId();
		if (peeled != null) {
			return new ObjectIdRef.PeeledTag(
					src.getStorage(),
					name,
					src.getObjectId(),
					peeled);
		}

		return new ObjectIdRef.PeeledNonTag(
				src.getStorage(),
				name,
				src.getObjectId());
	}
}
