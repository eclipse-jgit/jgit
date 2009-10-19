/*
 * Copyright (C) 2009, Robin Rosenberg
 * Copyright (C) 2009, Robin Rosenberg <robin.rosenberg@dewire.com>
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

package org.eclipse.jgit.lib;

import java.io.IOException;

import org.eclipse.jgit.lib.RefUpdate.Result;

/**
 * A RefUpdate combination for renaming a ref
 */
public class RefRename {
	private RefUpdate newToUpdate;

	private RefUpdate oldFromDelete;

	private Result renameResult = Result.NOT_ATTEMPTED;

	RefRename(final RefUpdate toUpdate, final RefUpdate fromUpdate) {
		newToUpdate = toUpdate;
		oldFromDelete = fromUpdate;
	}

	/**
	 * @return result of rename operation
	 */
	public Result getResult() {
		return renameResult;
	}

	/**
	 * @return the result of the new ref update
	 * @throws IOException
	 */
	public Result rename() throws IOException {
		Ref oldRef = oldFromDelete.db.readRef(Constants.HEAD);
		boolean renameHEADtoo = oldRef != null
				&& oldRef.getName().equals(oldFromDelete.getName());
		Repository db = oldFromDelete.getRepository();
		try {
			RefLogWriter.renameTo(db, oldFromDelete,
					newToUpdate);
			newToUpdate.setRefLogMessage(null, false);
			String tmpRefName = "RENAMED-REF.." + Thread.currentThread().getId();
			RefUpdate tmpUpdateRef = db.updateRef(tmpRefName);
			if (renameHEADtoo) {
				try {
					oldFromDelete.db.link(Constants.HEAD, tmpRefName);
				} catch (IOException e) {
					RefLogWriter.renameTo(db,
							newToUpdate, oldFromDelete);
					return renameResult = Result.LOCK_FAILURE;
				}
			}
			tmpUpdateRef.setNewObjectId(oldFromDelete.getOldObjectId());
			tmpUpdateRef.setForceUpdate(true);
			Result update = tmpUpdateRef.update();
			if (update != Result.FORCED && update != Result.NEW && update != Result.NO_CHANGE) {
				RefLogWriter.renameTo(db,
						newToUpdate, oldFromDelete);
				if (renameHEADtoo) {
					oldFromDelete.db.link(Constants.HEAD, oldFromDelete.getName());
				}
				return renameResult = update;
			}

			oldFromDelete.setExpectedOldObjectId(oldFromDelete.getOldObjectId());
			oldFromDelete.setForceUpdate(true);
			Result delete = oldFromDelete.delete();
			if (delete != Result.FORCED) {
				if (db.getRef(
						oldFromDelete.getName()) != null) {
					RefLogWriter.renameTo(db,
							newToUpdate, oldFromDelete);
					if (renameHEADtoo) {
						oldFromDelete.db.link(Constants.HEAD, oldFromDelete
								.getName());
					}
				}
				return renameResult = delete;
			}

			newToUpdate.setNewObjectId(tmpUpdateRef.getNewObjectId());
			Result updateResult = newToUpdate.update();
			if (updateResult != Result.NEW) {
				RefLogWriter.renameTo(db, newToUpdate, oldFromDelete);
				if (renameHEADtoo) {
					oldFromDelete.db.link(Constants.HEAD, oldFromDelete.getName());
				}
				oldFromDelete.setExpectedOldObjectId(null);
				oldFromDelete.setNewObjectId(oldFromDelete.getOldObjectId());
				oldFromDelete.setForceUpdate(true);
				oldFromDelete.setRefLogMessage(null, false);
				Result undelete = oldFromDelete.update();
				if (undelete != Result.NEW && undelete != Result.LOCK_FAILURE)
					return renameResult = Result.IO_FAILURE;
				return renameResult = Result.LOCK_FAILURE;
			}

			if (renameHEADtoo) {
				oldFromDelete.db.link(Constants.HEAD, newToUpdate.getName());
			} else {
				db.fireRefsMaybeChanged();
			}
			RefLogWriter.append(this, newToUpdate.getName(), "Branch: renamed "
					+ db.shortenRefName(oldFromDelete.getName()) + " to "
					+ db.shortenRefName(newToUpdate.getName()));
			if (renameHEADtoo)
				RefLogWriter.append(this, Constants.HEAD, "Branch: renamed "
						+ db.shortenRefName(oldFromDelete.getName()) + " to "
						+ db.shortenRefName(newToUpdate.getName()));
			return renameResult = Result.RENAMED;
		} catch (RuntimeException e) {
			throw e;
		}
	}

	ObjectId getObjectId() {
		return oldFromDelete.getOldObjectId();
	}

	Repository getRepository() {
		return oldFromDelete.getRepository();
	}

	PersonIdent getRefLogIdent() {
		return newToUpdate.getRefLogIdent();
	}

	String getToName() {
		return newToUpdate.getName();
	}
}
