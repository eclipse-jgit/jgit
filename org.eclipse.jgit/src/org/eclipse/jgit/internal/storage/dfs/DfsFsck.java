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
import java.nio.channels.Channels;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.internal.storage.pack.CorruptPackIndexException;
import org.eclipse.jgit.internal.storage.pack.FsckPackParser;
import org.eclipse.jgit.internal.storage.pack.FsckPackParser.CorruptIndex;
import org.eclipse.jgit.internal.storage.pack.FsckPackParser.CorruptObject;
import org.eclipse.jgit.internal.storage.pack.PackExt;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectChecker;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.revwalk.ObjectWalk;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.transport.PackedObjectInfo;

/**
 * Verify the validity and connectivity of the objects in a dfs repository.
 */
public class DfsFsck {
	private final DfsRepository repo;

	private final DfsObjDatabase objdb;

	private final DfsReader ctx;

	private final ObjectChecker objChecker;

	private final Set<CorruptObject> corruptObjects = new HashSet<>();

	private final Set<ObjectId> missingObjects = new HashSet<>();

	private final Set<CorruptIndex> corruptIndices = new HashSet<>();

	/**
	 * Initialize a Dfs Fsck.
	 *
	 * @param repository the dfs repository to check.
	 * @param objectChecker A customized object checker.
	 */
	public DfsFsck(DfsRepository repository, ObjectChecker objectChecker) {
		repo = repository;
		objChecker = objectChecker;
		objdb = repo.getObjectDatabase();
		ctx = objdb.newReader();
	}

	public DfsFsck(DfsRepository repository) {
		this(repository, new ObjectChecker());
	}

	public void check(ProgressMonitor pm) throws IOException {
		if (pm == null) {
			pm = NullProgressMonitor.INSTANCE;
		}

		try {
			for (DfsPackFile pack : objdb.getPacks()) {
				DfsPackDescription packDesc = pack.getPackDescription();
				List<PackedObjectInfo> objectsInPack;
				try (ReadableChannel channel =
						repo.getObjectDatabase()
								.openFile(packDesc,
										PackExt.PACK)) {
					FsckPackParser parser = new FsckPackParser(repo,
							channel, objChecker, packDesc.getObjectCount());
					parser.parse(pm);
					corruptObjects.addAll(parser.getCorruptObjects());
					objectsInPack = parser.getSortedObjectList(null);
					parser.verifyIndex(objectsInPack, pack.getPackIndex(ctx));

					// checking connectivity for objects in the pack.
					ObjectWalk objectWalk = new ObjectWalk(ctx);
					for (PackedObjectInfo o : objectsInPack) {
						RevObject object = objectWalk.parseAny(o);
						objectWalk.markStart(object);
						objectWalk.checkConnectivity();
						objectWalk.markUninteresting(object);
					}
				} catch (MissingObjectException e) {
					missingObjects.add(e.getObjectId());
				} catch (CorruptPackIndexException e) {
					corruptIndices.add(new CorruptIndex(pack.getPackName(),
							e.getErrorType()));
				}
			}
		} finally {
			ctx.close();
		}
	}

	/**
	 * @return corrupted objects from all pack files.
	 * @since 4.9
	 */
	public Set<CorruptObject> getCorruptObjects() {
		return corruptObjects;
	}

	/**
	 * @return missing objects that should present in pack files.
	 * @since 4.9
	 */
	public Set<ObjectId> getMissingObjects() {
		return missingObjects;
	}

	/**
	 * @return corrupted index files associated with the packs.
	 * @since 4.9
	 */
	public Set<CorruptIndex> getCorruptIndices() {
		return corruptIndices;
	}
}
