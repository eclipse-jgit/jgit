/*
 * Copyright (C) 2015, Sven Selberg <sven.selberg@sonymobile.com>
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
package org.eclipse.jgit.revwalk;

import java.util.Collections;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;

/**
 * A simple fifo cache to lessen IO operations in sequential pushes of the same
 * project in server applications.
 *
 */
class ObjectModeCache {
	private static final int CACHE_INITIAL_SIZE = 100003;

	/**
	 * A no operation {@link ObjectModeCache}.
	 */
	public static final ObjectModeCache NOOP_CACHE = new ObjectModeCache() {
		private final ChildList noopList = new ChildList() {

			@Override
			public Iterator<CachedObject> iterator() {
				return Collections.emptyIterator();
			}

			@Override
			public void add(CachedObject child) {
				// NOOP
			}

			@Override
			public CachedObject[] toArray() {
				return null;
			}

			@Override
			public int size() {
				return 0;
			}

		};

		private final CachedObject noopObject = new CachedObject(
				ObjectId.zeroId(), 0) {

			@Override
			public CachedObject[] getChildren() {
				return null;
			}

			@Override
			public boolean hasSetChildren() {
				return false;
			}
		};

		@Override
		void prune() {
			// NOOP
		}

		@Override
		CachedObject get(AnyObjectId id, int mode) {
			return noopObject;
		}

		@Override
		void addRoot(RevTree tree) {
			// NOOP
		}

		@Override
		void setChildren(CachedObject parent, ChildList children) {
			// NOOP
		}

		@Override
		public ChildList getChildList() {
			return noopList;
		}
	};

	/**
	 * An {@link ObjectId} that keeps track of its mode, children and number of
	 * parents.
	 */
	public class CachedObject extends ObjectId {

		public final int mode;

		private int parentCount;

		private CachedObject[] children;

		private CachedObject(AnyObjectId objectId, int mode) {
			super(objectId);
			this.mode = mode;
			parentCount = 0;
		}

		/**
		 * Is not guaranteed to not return null. hasSetChildren() should be
		 * called first.
		 *
		 * @return A {@link CachedObject}[] or null, if there is no child.
		 */
		public CachedObject[] getChildren() {
			return this.children;
		}

		public boolean hasSetChildren() {
			return children != null;
		}
	}

	/**
	 * A list that holds the children of a {@link CachedObject}.
	 */
	interface ChildList extends Iterable<CachedObject> {
		void add(CachedObject child);

		CachedObject[] toArray();

		int size();

		/**
		 * LinkedList implementation of the {@link ChildList interface}.
		 *
		 */
		public static class Impl implements ChildList {
			List<CachedObject> children = new LinkedList<CachedObject>();

			@Override
			public Iterator<CachedObject> iterator() {
				return children.iterator();
			}

			@Override
			public void add(CachedObject child) {
				children.add(child);

			}

			@Override
			public CachedObject[] toArray() {
				return children.toArray(new CachedObject[size()]);
			}

			@Override
			public int size() {
				return children.size();
			}
		}
	}

	// The maximum size of this cache.
	private int maxSize;

	// Queue to get the oldest roots when attempting a prune
	private LinkedList<RevTree> rootsQueue;

	// To prevent copies in the rootsQueue.
	private Set<RevTree> rootsSet;

	// The HashTable that holds the cached objects.
	private Hashtable<ObjectId, CachedObject> cachedObjects;

	/**
	 * Constructor for the Noop cache.
	 */
	ObjectModeCache() {
		// Noop ctor
	}

	/**
	 * Creates an ObjectModeCache with max size maxNumberOfObjects.
	 *
	 * @param maxNbrOfObjects
	 *            When the cache reaches this limit it starts removing the
	 *            oldest trees.
	 */
	public ObjectModeCache(int maxNbrOfObjects) {
		rootsQueue = new LinkedList<RevTree>();
		rootsSet = new HashSet<RevTree>();
		cachedObjects = new Hashtable<ObjectId, CachedObject>(
				CACHE_INITIAL_SIZE);
		maxSize = maxNbrOfObjects;
	}

	/**
	 * Guaranteed to always return a cached object. If the object is not yet
	 * cached a new object is created and inserted in the cache.
	 *
	 * @param objectId
	 *            The {@link ObjectId} of this CachedObject.
	 * @param mode
	 *            The mode of this object.
	 * @return A cached {@link CachedObject}.
	 */
	CachedObject get(AnyObjectId objectId, int mode) {
		CachedObject obj = cachedObjects.get(objectId);
		if (obj == null) {
			obj = new CachedObject(objectId, mode);
			cachedObjects.put(obj, obj);
		}
		return obj;
	}

	/**
	 * Removes the oldest tree and all its children, that are not reachable from
	 * other nodes, from the cache.
	 */
	void prune() {
		if (cachedObjects.size() > maxSize && !rootsQueue.isEmpty()) {
			Iterator<RevTree> itr = rootsQueue.iterator();
			while (itr.hasNext()) {
				RevTree oldestTree = itr.next();
				itr.remove();
				rootsSet.remove(oldestTree);
				CachedObject root = cachedObjects.get(oldestTree);
				// If the tree is not reachable from any other node.
				if (root.parentCount == 0) {
					removeRec(root);
					return;
				}
			}
		}
	}

	/**
	 * Adds tree to the root queue.
	 *
	 * @param tree
	 *            {@link RevTree}
	 */
	void addRoot(RevTree tree) {
		prune();
		if (!rootsSet.contains(tree)) {
			this.rootsQueue.add(tree);
			this.rootsSet.add(tree);
		}
	}

	/**
	 * Sets the children for the parent {@link CachedObject}.
	 *
	 * @param parent
	 * @param children
	 */
	void setChildren(CachedObject parent, ChildList children) {
		for (CachedObject child : children) {
			child.parentCount++;
		}
		parent.children = children.toArray();
	}

	/*
	 * Removes node and all its children not reachable from other nodes
	 * recursively.
	 */
	private void removeRec(CachedObject node) {
		cachedObjects.remove(node);
		if (node.hasSetChildren()) {
			for (CachedObject child : node.getChildren()) {
				child.parentCount--;
				if (child.parentCount == 0) {
					removeRec(child);
				}
			}
		}
	}

	/**
	 * @return a {@link ChildList} to hold the children of a
	 *         {@link CachedObject}.
	 */
	public ChildList getChildList() {
		return new ChildList.Impl();
	}
}
