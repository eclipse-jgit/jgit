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
