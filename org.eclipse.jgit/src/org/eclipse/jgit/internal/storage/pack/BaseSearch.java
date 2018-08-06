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

package org.eclipse.jgit.internal.storage.pack;

import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.eclipse.jgit.lib.Constants.OBJ_TREE;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.MutableObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdOwnerMap;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;

class BaseSearch {
	private static final int M_BLOB = FileMode.REGULAR_FILE.getBits();

	private static final int M_TREE = FileMode.TREE.getBits();

	private final ProgressMonitor progress;

	private final ObjectReader reader;

	private final ObjectId[] baseTrees;

	private final ObjectIdOwnerMap<ObjectToPack> objectsMap;

	private final List<ObjectToPack> edgeObjects;

	private final IntSet alreadyProcessed;

	private final ObjectIdOwnerMap<TreeWithData> treeCache;

	private final CanonicalTreeParser parser;

	private final MutableObjectId idBuf;

	BaseSearch(ProgressMonitor countingMonitor, Set<RevTree> bases,
			ObjectIdOwnerMap<ObjectToPack> objects,
			List<ObjectToPack> edges, ObjectReader or) {
		progress = countingMonitor;
		reader = or;
		baseTrees = bases.toArray(new ObjectId[0]);
		objectsMap = objects;
		edgeObjects = edges;

		alreadyProcessed = new IntSet();
		treeCache = new ObjectIdOwnerMap<>();
		parser = new CanonicalTreeParser();
		idBuf = new MutableObjectId();
	}

	void addBase(int objectType, byte[] pathBuf, int pathLen, int pathHash)
			throws IOException {
		final int tailMode = modeForType(objectType);
		if (tailMode == 0)
			return;

		if (!alreadyProcessed.add(pathHash))
			return;

		if (pathLen == 0) {
			for (ObjectId root : baseTrees)
				add(root, OBJ_TREE, pathHash);
			return;
		}

		final int firstSlash = nextSlash(pathBuf, 0, pathLen);

		CHECK_BASE: for (ObjectId root : baseTrees) {
			int ptr = 0;
			int end = firstSlash;
			int mode = end != pathLen ? M_TREE : tailMode;

			parser.reset(readTree(root));
			while (!parser.eof()) {
				int cmp = parser.pathCompare(pathBuf, ptr, end, mode);

				if (cmp < 0) {
					parser.next();
					continue;
				}

				if (cmp > 0)
					continue CHECK_BASE;

				if (end == pathLen) {
					if (parser.getEntryFileMode().getObjectType() == objectType) {
						idBuf.fromRaw(parser.idBuffer(), parser.idOffset());
						add(idBuf, objectType, pathHash);
					}
					continue CHECK_BASE;
				}

				if (!FileMode.TREE.equals(parser.getEntryRawMode()))
					continue CHECK_BASE;

				ptr = end + 1;
				end = nextSlash(pathBuf, ptr, pathLen);
				mode = end != pathLen ? M_TREE : tailMode;

				idBuf.fromRaw(parser.idBuffer(), parser.idOffset());
				parser.reset(readTree(idBuf));
			}
		}
	}

	private static int modeForType(int typeCode) {
		switch (typeCode) {
		case OBJ_TREE:
			return M_TREE;

		case OBJ_BLOB:
			return M_BLOB;

		default:
			return 0;
		}
	}

	private static int nextSlash(byte[] pathBuf, int ptr, int end) {
		while (ptr < end && pathBuf[ptr] != '/')
			ptr++;
		return ptr;
	}

	private void add(AnyObjectId id, int objectType, int pathHash) {
		ObjectToPack obj = new ObjectToPack(id, objectType);
		obj.setEdge();
		obj.setPathHash(pathHash);

		if (objectsMap.addIfAbsent(obj) == obj) {
			edgeObjects.add(obj);
			progress.update(1);
		}
	}

	private byte[] readTree(AnyObjectId id) throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		TreeWithData tree = treeCache.get(id);
		if (tree != null)
			return tree.buf;

		ObjectLoader ldr = reader.open(id, OBJ_TREE);
		byte[] buf = ldr.getCachedBytes(Integer.MAX_VALUE);
		treeCache.add(new TreeWithData(id, buf));
		return buf;
	}

	private static class TreeWithData extends ObjectIdOwnerMap.Entry {
		final byte[] buf;

		TreeWithData(AnyObjectId id, byte[] buf) {
			super(id);
			this.buf = buf;
		}
	}
}
