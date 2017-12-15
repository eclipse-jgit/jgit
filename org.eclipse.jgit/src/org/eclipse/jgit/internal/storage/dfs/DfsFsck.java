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

import static org.eclipse.jgit.internal.storage.pack.PackExt.INDEX;
import static org.eclipse.jgit.internal.storage.pack.PackExt.PACK;

import java.io.FileNotFoundException;
import java.io.IOException;

import org.eclipse.jgit.errors.CorruptPackIndexException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.fsck.FsckError;
import org.eclipse.jgit.internal.fsck.FsckError.CorruptIndex;
import org.eclipse.jgit.internal.fsck.FsckPackParser;
import org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase.PackSource;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectChecker;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.ObjectWalk;
import org.eclipse.jgit.revwalk.RevObject;

/** Verify the validity and connectivity of a DFS repository. */
public class DfsFsck {
	private final DfsRepository repo;
	private final DfsObjDatabase objdb;
	private ObjectChecker objChecker = new ObjectChecker();
	private boolean connectivityOnly;

	/**
	 * Initialize DFS fsck.
	 *
	 * @param repository
	 *            the dfs repository to check.
	 */
	public DfsFsck(DfsRepository repository) {
		repo = repository;
		objdb = repo.getObjectDatabase();
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
		if (pm == null) {
			pm = NullProgressMonitor.INSTANCE;
		}

		FsckError errors = new FsckError();
		if (!connectivityOnly) {
			checkPacks(pm, errors);
		}
		checkConnectivity(pm, errors);
		return errors;
	}

	private void checkPacks(ProgressMonitor pm, FsckError errors)
			throws IOException, FileNotFoundException {
		try (DfsReader ctx = objdb.newReader()) {
			for (DfsPackFile pack : objdb.getPacks()) {
				DfsPackDescription packDesc = pack.getPackDescription();
				if (packDesc.getPackSource()
						== PackSource.UNREACHABLE_GARBAGE) {
					continue;
				}
				try (ReadableChannel rc = objdb.openFile(packDesc, PACK)) {
					verifyPack(pm, errors, ctx, pack, rc);
				} catch (MissingObjectException e) {
					errors.getMissingObjects().add(e.getObjectId());
				} catch (CorruptPackIndexException e) {
					errors.getCorruptIndices().add(new CorruptIndex(
							pack.getPackDescription().getFileName(INDEX),
							e.getErrorType()));
				}
			}
		}
	}

	private void verifyPack(ProgressMonitor pm, FsckError errors, DfsReader ctx,
			DfsPackFile pack, ReadableChannel ch)
					throws IOException, CorruptPackIndexException {
		FsckPackParser fpp = new FsckPackParser(objdb, ch);
		fpp.setObjectChecker(objChecker);
		fpp.overwriteObjectCount(pack.getPackDescription().getObjectCount());
		fpp.parse(pm);
		errors.getCorruptObjects().addAll(fpp.getCorruptObjects());

		fpp.verifyIndex(pack.getPackIndex(ctx));
	}

	private void checkConnectivity(ProgressMonitor pm, FsckError errors)
			throws IOException {
		pm.beginTask(JGitText.get().countingObjects, ProgressMonitor.UNKNOWN);
		try (ObjectWalk ow = new ObjectWalk(repo)) {
			for (Ref r : repo.getAllRefs().values()) {
				ObjectId objectId = r.getObjectId();
				if (objectId == null) {
					// skip unborn branch
					continue;
				}
				RevObject tip;
				try {
					tip = ow.parseAny(objectId);
					if (r.getLeaf().getName().startsWith(Constants.R_HEADS)
							&& tip.getType() != Constants.OBJ_COMMIT) {
						// heads should only point to a commit object
						errors.getNonCommitHeads().add(r.getLeaf().getName());
					}
					ow.markStart(tip);
				} catch (MissingObjectException e) {
					errors.getMissingObjects().add(e.getObjectId());
					continue;
				}
			}
			try {
				ow.checkConnectivity();
			} catch (MissingObjectException e) {
				errors.getMissingObjects().add(e.getObjectId());
			}
		}
		pm.endTask();
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

	/**
	 * @param connectivityOnly
	 *             whether fsck should bypass object validity and integrity
	 *             checks and only check connectivity. The default is
	 *             {@code false}, meaning to run all checks.
	 */
	public void setConnectivityOnly(boolean connectivityOnly) {
		this.connectivityOnly = connectivityOnly;
	}
}
