/*
 * Copyright (C) 2010, Google Inc.
 * Copyright (C) 2009, Robin Rosenberg
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

package org.eclipse.jgit.internal.storage.file;

import java.io.File;
import java.io.IOException;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefRename;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.FileUtils;

/**
 * Rename any reference stored by {@link RefDirectory}.
 * <p>
 * This class works by first renaming the source reference to a temporary name,
 * then renaming the temporary name to the final destination reference.
 * <p>
 * This strategy permits switching a reference like {@code refs/heads/foo},
 * which is a file, to {@code refs/heads/foo/bar}, which is stored inside a
 * directory that happens to match the source name.
 */
class RefDirectoryRename extends RefRename {
	private final RefDirectory refdb;

	/**
	 * The value of the source reference at the start of the rename.
	 * <p>
	 * At the end of the rename the destination reference must have this same
	 * value, otherwise we have a concurrent update and the rename must fail
	 * without making any changes.
	 */
	private ObjectId objId;

	/** True if HEAD must be moved to the destination reference. */
	private boolean updateHEAD;

	/** A reference we backup {@link #objId} into during the rename. */
	private RefDirectoryUpdate tmp;

	RefDirectoryRename(RefDirectoryUpdate src, RefDirectoryUpdate dst) {
		super(src, dst);
		refdb = src.getRefDatabase();
	}

	@Override
	protected Result doRename() throws IOException {
		if (source.getRef().isSymbolic())
			return Result.IO_FAILURE; // not supported

		objId = source.getOldObjectId();
		updateHEAD = needToUpdateHEAD();
		tmp = refdb.newTemporaryUpdate();
		final RevWalk rw = new RevWalk(refdb.getRepository());
		try {
			// First backup the source so its never unreachable.
			tmp.setNewObjectId(objId);
			tmp.setForceUpdate(true);
			tmp.disableRefLog();
			switch (tmp.update(rw)) {
			case NEW:
			case FORCED:
			case NO_CHANGE:
				break;
			default:
				return tmp.getResult();
			}

			// Save the source's log under the temporary name, we must do
			// this before we delete the source, otherwise we lose the log.
			if (!renameLog(source, tmp))
				return Result.IO_FAILURE;

			// If HEAD has to be updated, link it now to destination.
			// We have to link before we delete, otherwise the delete
			// fails because its the current branch.
			RefUpdate dst = destination;
			if (updateHEAD) {
				if (!linkHEAD(destination)) {
					renameLog(tmp, source);
					return Result.LOCK_FAILURE;
				}

				// Replace the update operation so HEAD will log the rename.
				dst = refdb.newUpdate(Constants.HEAD, false);
				dst.setRefLogIdent(destination.getRefLogIdent());
				dst.setRefLogMessage(destination.getRefLogMessage(), false);
			}

			// Delete the source name so its path is free for replacement.
			source.setExpectedOldObjectId(objId);
			source.setForceUpdate(true);
			source.disableRefLog();
			if (source.delete(rw) != Result.FORCED) {
				renameLog(tmp, source);
				if (updateHEAD)
					linkHEAD(source);
				return source.getResult();
			}

			// Move the log to the destination.
			if (!renameLog(tmp, destination)) {
				renameLog(tmp, source);
				source.setExpectedOldObjectId(ObjectId.zeroId());
				source.setNewObjectId(objId);
				source.update(rw);
				if (updateHEAD)
					linkHEAD(source);
				return Result.IO_FAILURE;
			}

			// Create the destination, logging the rename during the creation.
			dst.setExpectedOldObjectId(ObjectId.zeroId());
			dst.setNewObjectId(objId);
			if (dst.update(rw) != Result.NEW) {
				// If we didn't create the destination we have to undo
				// our work. Put the log back and restore source.
				if (renameLog(destination, tmp))
					renameLog(tmp, source);
				source.setExpectedOldObjectId(ObjectId.zeroId());
				source.setNewObjectId(objId);
				source.update(rw);
				if (updateHEAD)
					linkHEAD(source);
				return dst.getResult();
			}

			return Result.RENAMED;
		} finally {
			// Always try to free the temporary name.
			try {
				refdb.delete(tmp);
			} catch (IOException err) {
				FileUtils.delete(refdb.fileFor(tmp.getName()));
			}
			rw.release();
		}
	}

	private boolean renameLog(RefUpdate src, RefUpdate dst) {
		File srcLog = refdb.getLogWriter().logFor(src.getName());
		File dstLog = refdb.getLogWriter().logFor(dst.getName());

		if (!srcLog.exists())
			return true;

		if (!rename(srcLog, dstLog))
			return false;

		try {
			final int levels = RefDirectory.levelsIn(src.getName()) - 2;
			RefDirectory.delete(srcLog, levels);
			return true;
		} catch (IOException e) {
			rename(dstLog, srcLog);
			return false;
		}
	}

	private static boolean rename(File src, File dst) {
		if (src.renameTo(dst))
			return true;

		File dir = dst.getParentFile();
		if ((dir.exists() || !dir.mkdirs()) && !dir.isDirectory())
			return false;
		return src.renameTo(dst);
	}

	private boolean linkHEAD(RefUpdate target) {
		try {
			RefUpdate u = refdb.newUpdate(Constants.HEAD, false);
			u.disableRefLog();
			switch (u.link(target.getName())) {
			case NEW:
			case FORCED:
			case NO_CHANGE:
				return true;
			default:
				return false;
			}
		} catch (IOException e) {
			return false;
		}
	}
}
