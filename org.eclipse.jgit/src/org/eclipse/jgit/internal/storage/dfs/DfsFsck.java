/*
 * Copyright (C) 2017, Google Inc.
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

package org.eclipse.jgit.internal.storage.dfs;

import java.io.IOException;
import java.util.List;

import org.eclipse.jgit.errors.CorruptPackIndexException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.internal.fsck.FsckError;
import org.eclipse.jgit.internal.fsck.FsckError.CorruptIndex;
import org.eclipse.jgit.internal.fsck.FsckPackParser;
import org.eclipse.jgit.internal.storage.pack.PackExt;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectChecker;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.ObjectWalk;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.transport.PackedObjectInfo;

/** Verify the validity and connectivity of a DFS repository. */
public class DfsFsck {
	private final DfsRepository repo;

	private final DfsObjDatabase objdb;

	private final DfsReader ctx;

	private ObjectChecker objChecker = new ObjectChecker();

	/**
	 * Initialize DFS fsck.
	 *
	 * @param repository
	 *            the dfs repository to check.
	 */
	public DfsFsck(DfsRepository repository) {
		repo = repository;
		objdb = repo.getObjectDatabase();
		ctx = objdb.newReader();
	}


	/**
	 * Verify the integrity and connectivity of all objects in the object
	 * database.
	 *
	 * @param pm
	 *            callback to provide progress feedback during the check.
	 * @return all errors about the repository.
	 * @throws IOException
	 *             if encounters IO errors during the process.
	 */
	public FsckError check(ProgressMonitor pm) throws IOException {
		FsckError errors = new FsckError();
		try {
			for (DfsPackFile pack : objdb.getPacks()) {
				DfsPackDescription packDesc = pack.getPackDescription();
				try (ReadableChannel channel = repo.getObjectDatabase()
						.openFile(packDesc, PackExt.PACK)) {
					List<PackedObjectInfo> objectsInPack;
					FsckPackParser parser = new FsckPackParser(
							repo.getObjectDatabase(), channel);
					parser.setObjectChecker(objChecker);
					parser.overwriteObjectCount(packDesc.getObjectCount());
					parser.parse(pm);
					errors.getCorruptObjects()
							.addAll(parser.getCorruptObjects());
					objectsInPack = parser.getSortedObjectList(null);
					parser.verifyIndex(objectsInPack, pack.getPackIndex(ctx));
				} catch (MissingObjectException e) {
					errors.getMissingObjects().add(e.getObjectId());
				} catch (CorruptPackIndexException e) {
					errors.getCorruptIndices().add(new CorruptIndex(
							pack.getPackDescription()
									.getFileName(PackExt.INDEX),
							e.getErrorType()));
				}
			}

			try (ObjectWalk ow = new ObjectWalk(ctx)) {
				for (Ref r : repo.getAllRefs().values()) {
					try {
						RevObject tip = ow.parseAny(r.getObjectId());
						if (r.getLeaf().getName().startsWith(Constants.R_HEADS)) {
							// check if heads point to a commit object
							if (tip.getType() != Constants.OBJ_COMMIT) {
								errors.getNonCommitHeads()
										.add(r.getLeaf().getName());
							}
						}
						ow.markStart(tip);
						ow.checkConnectivity();
						ow.markUninteresting(tip);
					} catch (MissingObjectException e) {
						errors.getMissingObjects().add(e.getObjectId());
					}
				}
			}
		} finally {
			ctx.close();
		}
		return errors;
	}

	/**
	 * Use a customized object checker instead of the default one. Caller can
	 * specify a skip list to ignore some errors.
	 *
	 * @param objChecker
	 *            A customized object checker.
	 */
	public void setObjectChecker(ObjectChecker objChecker) {
		this.objChecker = objChecker;
	}
}
