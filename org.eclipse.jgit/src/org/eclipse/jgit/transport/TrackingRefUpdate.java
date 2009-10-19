/*
 * Copyright (C) 2008, Marek Zawirski <marek.zawirski@gmail.com>
 * Copyright (C) 2008, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
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

package org.eclipse.jgit.transport;

import java.io.IOException;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.revwalk.RevWalk;

/** Update of a locally stored tracking branch. */
public class TrackingRefUpdate {
	private final String remoteName;

	private final RefUpdate update;

	TrackingRefUpdate(final Repository db, final RefSpec spec,
			final AnyObjectId nv, final String msg) throws IOException {
		this(db, spec.getDestination(), spec.getSource(), spec.isForceUpdate(),
				nv, msg);
	}

	TrackingRefUpdate(final Repository db, final String localName,
			final String remoteName, final boolean forceUpdate,
			final AnyObjectId nv, final String msg) throws IOException {
		this.remoteName = remoteName;
		update = db.updateRef(localName);
		update.setForceUpdate(forceUpdate);
		update.setNewObjectId(nv);
		update.setRefLogMessage(msg, true);
	}

	/**
	 * Get the name of the remote ref.
	 * <p>
	 * Usually this is of the form "refs/heads/master".
	 *
	 * @return the name used within the remote repository.
	 */
	public String getRemoteName() {
		return remoteName;
	}

	/**
	 * Get the name of the local tracking ref.
	 * <p>
	 * Usually this is of the form "refs/remotes/origin/master".
	 *
	 * @return the name used within this local repository.
	 */
	public String getLocalName() {
		return update.getName();
	}

	/**
	 * Get the new value the ref will be (or was) updated to.
	 *
	 * @return new value. Null if the caller has not configured it.
	 */
	public ObjectId getNewObjectId() {
		return update.getNewObjectId();
	}

	/**
	 * The old value of the ref, prior to the update being attempted.
	 * <p>
	 * This value may differ before and after the update method. Initially it is
	 * populated with the value of the ref before the lock is taken, but the old
	 * value may change if someone else modified the ref between the time we
	 * last read it and when the ref was locked for update.
	 *
	 * @return the value of the ref prior to the update being attempted; null if
	 *         the updated has not been attempted yet.
	 */
	public ObjectId getOldObjectId() {
		return update.getOldObjectId();
	}

	/**
	 * Get the status of this update.
	 *
	 * @return the status of the update.
	 */
	public Result getResult() {
		return update.getResult();
	}

	void update(final RevWalk walk) throws IOException {
		update.update(walk);
	}

	void delete(final RevWalk walk) throws IOException {
		update.delete(walk);
	}
}
