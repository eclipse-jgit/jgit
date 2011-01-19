/*
 * Copyright (C) 2011, Google Inc.
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

package org.eclipse.jgit.storage.dht;

import static org.eclipse.jgit.lib.Constants.OBJECT_ID_LENGTH;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.eclipse.jgit.lib.Constants.OBJ_COMMIT;
import static org.eclipse.jgit.lib.Constants.OBJ_TREE;

import java.io.IOException;
import java.text.MessageFormat;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.ObjectWalk;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.dht.spi.Database;
import org.eclipse.jgit.storage.dht.spi.WriteBuffer;

/** Constructs a cached object list for a repository. */
public class ObjectListCreator {
	private final DhtRepository repository;

	private final Database db;

	private final RepositoryKey repo;

	private final DhtInserterOptions options;

	private ObjectReader reader;

	private ObjectListInfo listInfo;

	private WriteBuffer dbBuffer;

	/**
	 * Initialize an object list creator for a repository.
	 *
	 * @param repository
	 *            the repository to create the list for.
	 */
	public ObjectListCreator(DhtRepository repository) {
		this.repository = repository;
		this.db = repository.getDatabase();
		this.repo = repository.getRepositoryKey();
		this.options = DhtInserterOptions.DEFAULT;
	}

	/**
	 * Create the cached object list.
	 *
	 * @throws IOException
	 *             if the list cannot be created.
	 */
	public void create() throws IOException {
		create(NullProgressMonitor.INSTANCE);
	}

	/**
	 * Create the cached object list.
	 *
	 * @param pm
	 *            optional progress monitor to see enumeration status.
	 * @throws IOException
	 *             if the list cannot be created.
	 */
	public void create(ProgressMonitor pm) throws IOException {
		if (pm == null)
			pm = NullProgressMonitor.INSTANCE;
		reader = repository.newObjectReader();
		try {
			RevCommit name = selectListName(pm);
			if (name == null)
				return;

			createAndPutList(name, pm);
		} finally {
			reader.release();
		}
	}

	private RevCommit selectListName(ProgressMonitor pm) throws IOException {
		pm.beginTask(DhtText.get().objectListSelectingName,
				ProgressMonitor.UNKNOWN);
		try {
			final RevWalk walk = new RevWalk(reader);
			walk.setRetainBody(false);
			walk.sort(RevSort.COMMIT_TIME_DESC);
			for (Ref ref : repository.getAllRefs().values()) {
				if (ref.getObjectId() == null)
					continue;
				try {
					walk.markStart(walk.parseCommit(ref.getObjectId()));
				} catch (MissingObjectException notFound) {
					continue;
				} catch (IncorrectObjectTypeException notCommit) {
					continue;
				}
				pm.update(1);
			}

			final RevCommit first = walk.next();
			if (first == null)
				return null;

			final int mostRecent = first.getCommitTime();
			int commitsToSkip = options.getObjectListCommitsToSkip();
			int ageToSkip = options.getObjectListSecondsToSkip();

			RevCommit last = null;
			while ((last = walk.next()) != null) {
				pm.update(1);
				if (--commitsToSkip == 0)
					return last;
				if (mostRecent - last.getCommitTime() > ageToSkip)
					return last;
			}
			return last;
		} finally {
			pm.endTask();
		}
	}

	private void createAndPutList(RevCommit name, ProgressMonitor pm)
			throws IOException {
		pm.beginTask(MessageFormat.format(DhtText.get().objectListCountingFrom,
				name.abbreviate(8).name()), ProgressMonitor.UNKNOWN);
		try {
			final ObjectWalk walk = new ObjectWalk(reader);
			walk.setRetainBody(false);
			walk.markStart(walk.parseCommit(name));
			walk.sort(RevSort.TOPO);

			listInfo = new ObjectListInfo();
			listInfo.repository = repo;
			listInfo.startingCommit = name;

			listInfo.commits = new ObjectListInfo.Segment(OBJ_COMMIT);
			listInfo.trees = new ObjectListInfo.Segment(OBJ_TREE);
			listInfo.blobs = new ObjectListInfo.Segment(OBJ_BLOB);

			listInfo.commits.chunkStart = OBJ_COMMIT << 29;
			listInfo.trees.chunkStart = OBJ_TREE << 29;
			listInfo.blobs.chunkStart = OBJ_BLOB << 29;

			dbBuffer = db.newWriteBuffer();

			putCommits(walk, pm);
			putTreesAndBlobs(walk, pm);

			db.repository().put(repo, listInfo, dbBuffer);
			dbBuffer.flush();
		} finally {
			pm.endTask();
		}
	}

	private void putCommits(final ObjectWalk walk, ProgressMonitor pm)
			throws MissingObjectException, IncorrectObjectTypeException,
			IOException {
		final int idsPerChunk = options.getChunkSize() / OBJECT_ID_LENGTH;
		RevObject[] commitId = new RevObject[idsPerChunk];
		int[] commitHash = new int[idsPerChunk];
		int commitCnt = 0;

		RevObject o;
		while ((o = walk.next()) != null) {
			commitId[commitCnt++] = o;
			if (commitCnt == idsPerChunk) {
				putChunk(commitId, commitHash, commitCnt, listInfo.commits);
				commitCnt = 0;
			}
			pm.update(1);
		}
		if (0 < commitCnt)
			putChunk(commitId, commitHash, commitCnt, listInfo.commits);
	}

	private void putTreesAndBlobs(final ObjectWalk walk, ProgressMonitor pm)
			throws MissingObjectException, IncorrectObjectTypeException,
			IOException {
		final int idsPerChunk = options.getChunkSize() / (OBJECT_ID_LENGTH + 4);
		RevObject[] treeId = new RevObject[idsPerChunk];
		RevObject[] blobId = new RevObject[idsPerChunk];
		int[] treeHash = new int[idsPerChunk];
		int[] blobHash = new int[idsPerChunk];
		int treeCnt = 0;
		int blobCnt = 0;

		RevObject o;
		while ((o = walk.nextObject()) != null) {
			switch (o.getType()) {
			case OBJ_TREE:
				treeId[treeCnt] = o;
				treeHash[treeCnt] = walk.getPathHashCode();
				if (++treeCnt == idsPerChunk) {
					putChunk(treeId, treeHash, treeCnt, listInfo.trees);
					treeCnt = 0;
				}
				pm.update(1);
				break;
			case OBJ_BLOB:
				blobId[blobCnt] = o;
				blobHash[blobCnt] = walk.getPathHashCode();
				if (++blobCnt == idsPerChunk) {
					putChunk(blobId, blobHash, blobCnt, listInfo.blobs);
					blobCnt = 0;
				}
				pm.update(1);
				break;
			}
		}
		if (0 < treeCnt)
			putChunk(treeId, treeHash, treeCnt, listInfo.trees);
		if (0 < blobCnt)
			putChunk(blobId, blobHash, blobCnt, listInfo.blobs);
	}

	private void putChunk(RevObject[] ids, int[] hashes, int cnt,
			ObjectListInfo.Segment segment) throws DhtException {
		int cid = segment.chunkStart + segment.chunkCount;
		ObjectListChunkKey key = listInfo.getChunkKey(cid);
		ObjectListChunk list = ObjectListChunk.create(key, ids, hashes, cnt);

		segment.chunkCount++;
		segment.objectCount += cnt;

		listInfo.chunkCount++;
		listInfo.objectCount += cnt;
		listInfo.listSizeInBytes += list.getByteSize();

		db.objectList().put(list, dbBuffer);
	}
}
