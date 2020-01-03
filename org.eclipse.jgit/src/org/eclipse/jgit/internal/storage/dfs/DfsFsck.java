/*
 * Copyright (C) 2017, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.dfs;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.internal.storage.pack.PackExt.INDEX;
import static org.eclipse.jgit.internal.storage.pack.PackExt.PACK;

import java.io.FileNotFoundException;
import java.io.IOException;

import org.eclipse.jgit.errors.CorruptPackIndexException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.fsck.FsckError;
import org.eclipse.jgit.internal.fsck.FsckError.CorruptIndex;
import org.eclipse.jgit.internal.fsck.FsckError.CorruptObject;
import org.eclipse.jgit.internal.fsck.FsckPackParser;
import org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase.PackSource;
import org.eclipse.jgit.internal.submodule.SubmoduleValidator;
import org.eclipse.jgit.internal.submodule.SubmoduleValidator.SubmoduleValidationException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.GitmoduleEntry;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectChecker;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.ObjectWalk;
import org.eclipse.jgit.revwalk.RevObject;

/**
 * Verify the validity and connectivity of a DFS repository.
 */
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
	 * @throws java.io.IOException
	 *             if encounters IO errors during the process.
	 */
	public FsckError check(ProgressMonitor pm) throws IOException {
		if (pm == null) {
			pm = NullProgressMonitor.INSTANCE;
		}

		FsckError errors = new FsckError();
		if (!connectivityOnly) {
			objChecker.reset();
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

		checkGitModules(pm, errors);
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

	private void checkGitModules(ProgressMonitor pm, FsckError errors)
			throws IOException {
		pm.beginTask(JGitText.get().validatingGitModules,
				objChecker.getGitsubmodules().size());
		for (GitmoduleEntry entry : objChecker.getGitsubmodules()) {
			AnyObjectId blobId = entry.getBlobId();
			ObjectLoader blob = objdb.open(blobId, Constants.OBJ_BLOB);

			try {
				SubmoduleValidator.assertValidGitModulesFile(
						new String(blob.getBytes(), UTF_8));
			} catch (SubmoduleValidationException e) {
				CorruptObject co = new FsckError.CorruptObject(
						blobId.toObjectId(), Constants.OBJ_BLOB,
						e.getFsckMessageId());
				errors.getCorruptObjects().add(co);
			}
			pm.update(1);
		}
		pm.endTask();
	}

	private void checkConnectivity(ProgressMonitor pm, FsckError errors)
			throws IOException {
		pm.beginTask(JGitText.get().countingObjects, ProgressMonitor.UNKNOWN);
		try (ObjectWalk ow = new ObjectWalk(repo)) {
			for (Ref r : repo.getRefDatabase().getRefs()) {
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
	 * It will be reset at the start of each {{@link #check(ProgressMonitor)}
	 * call.
	 *
	 * @param objChecker
	 *            A customized object checker.
	 */
	public void setObjectChecker(ObjectChecker objChecker) {
		this.objChecker = objChecker;
	}

	/**
	 * Whether fsck should bypass object validity and integrity checks and only
	 * check connectivity.
	 *
	 * @param connectivityOnly
	 *            whether fsck should bypass object validity and integrity
	 *            checks and only check connectivity. The default is
	 *            {@code false}, meaning to run all checks.
	 */
	public void setConnectivityOnly(boolean connectivityOnly) {
		this.connectivityOnly = connectivityOnly;
	}
}
