/*
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

package org.eclipse.jgit.revwalk;

import java.io.IOException;
import java.text.MessageFormat;

import org.eclipse.jgit.JGitText;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;

/**
 * Specialized subclass of RevWalk to include trees, blobs and tags.
 * <p>
 * Unlike RevWalk this subclass is able to remember starting roots that include
 * annotated tags, or arbitrary trees or blobs. Once commit generation is
 * complete and all commits have been popped by the application, individual
 * annotated tag, tree and blob objects can be popped through the additional
 * method {@link #nextObject()}.
 * <p>
 * Tree and blob objects reachable from interesting commits are automatically
 * scheduled for inclusion in the results of {@link #nextObject()}, returning
 * each object exactly once. Objects are sorted and returned according to the
 * the commits that reference them and the order they appear within a tree.
 * Ordering can be affected by changing the {@link RevSort} used to order the
 * commits that are returned first.
 */
public class ObjectWalk extends RevWalk {
	/**
	 * Indicates a non-RevCommit is in {@link #pendingObjects}.
	 * <p>
	 * We can safely reuse {@link RevWalk#REWRITE} here for the same value as it
	 * is only set on RevCommit and {@link #pendingObjects} never has RevCommit
	 * instances inserted into it.
	 */
	private static final int IN_PENDING = RevWalk.REWRITE;

	private CanonicalTreeParser treeWalk;

	private BlockObjQueue pendingObjects;

	private RevTree currentTree;

	private RevObject last;

	private RevCommit firstCommit;

	private RevCommit lastCommit;

	/**
	 * Create a new revision and object walker for a given repository.
	 *
	 * @param repo
	 *            the repository the walker will obtain data from.
	 */
	public ObjectWalk(final Repository repo) {
		this(repo.newObjectReader());
	}

	/**
	 * Create a new revision and object walker for a given repository.
	 *
	 * @param or
	 *            the reader the walker will obtain data from. The reader should
	 *            be released by the caller when the walker is no longer
	 *            required.
	 */
	public ObjectWalk(ObjectReader or) {
		super(or);
		pendingObjects = new BlockObjQueue();
		treeWalk = new CanonicalTreeParser();
	}

	/**
	 * Mark an object or commit to start graph traversal from.
	 * <p>
	 * Callers are encouraged to use {@link RevWalk#parseAny(AnyObjectId)}
	 * instead of {@link RevWalk#lookupAny(AnyObjectId, int)}, as this method
	 * requires the object to be parsed before it can be added as a root for the
	 * traversal.
	 * <p>
	 * The method will automatically parse an unparsed object, but error
	 * handling may be more difficult for the application to explain why a
	 * RevObject is not actually valid. The object pool of this walker would
	 * also be 'poisoned' by the invalid RevObject.
	 * <p>
	 * This method will automatically call {@link RevWalk#markStart(RevCommit)}
	 * if passed RevCommit instance, or a RevTag that directly (or indirectly)
	 * references a RevCommit.
	 *
	 * @param o
	 *            the object to start traversing from. The object passed must be
	 *            from this same revision walker.
	 * @throws MissingObjectException
	 *             the object supplied is not available from the object
	 *             database. This usually indicates the supplied object is
	 *             invalid, but the reference was constructed during an earlier
	 *             invocation to {@link RevWalk#lookupAny(AnyObjectId, int)}.
	 * @throws IncorrectObjectTypeException
	 *             the object was not parsed yet and it was discovered during
	 *             parsing that it is not actually the type of the instance
	 *             passed in. This usually indicates the caller used the wrong
	 *             type in a {@link RevWalk#lookupAny(AnyObjectId, int)} call.
	 * @throws IOException
	 *             a pack file or loose object could not be read.
	 */
	public void markStart(RevObject o) throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		while (o instanceof RevTag) {
			addObject(o);
			o = ((RevTag) o).getObject();
			parseHeaders(o);
		}

		if (o instanceof RevCommit)
			super.markStart((RevCommit) o);
		else
			addObject(o);
	}

	/**
	 * Mark an object to not produce in the output.
	 * <p>
	 * Uninteresting objects denote not just themselves but also their entire
	 * reachable chain, back until the merge base of an uninteresting commit and
	 * an otherwise interesting commit.
	 * <p>
	 * Callers are encouraged to use {@link RevWalk#parseAny(AnyObjectId)}
	 * instead of {@link RevWalk#lookupAny(AnyObjectId, int)}, as this method
	 * requires the object to be parsed before it can be added as a root for the
	 * traversal.
	 * <p>
	 * The method will automatically parse an unparsed object, but error
	 * handling may be more difficult for the application to explain why a
	 * RevObject is not actually valid. The object pool of this walker would
	 * also be 'poisoned' by the invalid RevObject.
	 * <p>
	 * This method will automatically call {@link RevWalk#markStart(RevCommit)}
	 * if passed RevCommit instance, or a RevTag that directly (or indirectly)
	 * references a RevCommit.
	 *
	 * @param o
	 *            the object to start traversing from. The object passed must be
	 * @throws MissingObjectException
	 *             the object supplied is not available from the object
	 *             database. This usually indicates the supplied object is
	 *             invalid, but the reference was constructed during an earlier
	 *             invocation to {@link RevWalk#lookupAny(AnyObjectId, int)}.
	 * @throws IncorrectObjectTypeException
	 *             the object was not parsed yet and it was discovered during
	 *             parsing that it is not actually the type of the instance
	 *             passed in. This usually indicates the caller used the wrong
	 *             type in a {@link RevWalk#lookupAny(AnyObjectId, int)} call.
	 * @throws IOException
	 *             a pack file or loose object could not be read.
	 */
	public void markUninteresting(RevObject o) throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		while (o instanceof RevTag) {
			o.flags |= UNINTERESTING;
			if (hasRevSort(RevSort.BOUNDARY))
				addObject(o);
			o = ((RevTag) o).getObject();
			parseHeaders(o);
		}

		if (o instanceof RevCommit)
			super.markUninteresting((RevCommit) o);
		else if (o instanceof RevTree)
			markTreeUninteresting((RevTree) o);
		else
			o.flags |= UNINTERESTING;

		if (o.getType() != Constants.OBJ_COMMIT && hasRevSort(RevSort.BOUNDARY)) {
			addObject(o);
		}
	}

	@Override
	public RevCommit next() throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		for (;;) {
			final RevCommit r = super.next();
			if (r == null)
				return null;
			if ((r.flags & UNINTERESTING) != 0) {
				markTreeUninteresting(r.getTree());
				if (hasRevSort(RevSort.BOUNDARY)) {
					pendingObjects.add(r.getTree());
					return r;
				}
				continue;
			}
			if (firstCommit == null)
				firstCommit = r;
			lastCommit = r;
			pendingObjects.add(r.getTree());
			return r;
		}
	}

	/**
	 * Pop the next most recent object.
	 *
	 * @return next most recent object; null if traversal is over.
	 * @throws MissingObjectException
	 *             one or or more of the next objects are not available from the
	 *             object database, but were thought to be candidates for
	 *             traversal. This usually indicates a broken link.
	 * @throws IncorrectObjectTypeException
	 *             one or or more of the objects in a tree do not match the type
	 *             indicated.
	 * @throws IOException
	 *             a pack file or loose object could not be read.
	 */
	public RevObject nextObject() throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		if (last != null)
			treeWalk = last instanceof RevTree ? enter(last) : treeWalk.next();

		while (!treeWalk.eof()) {
			final FileMode mode = treeWalk.getEntryFileMode();
			switch (mode.getObjectType()) {
			case Constants.OBJ_BLOB: {
				treeWalk.getEntryObjectId(idBuffer);
				final RevBlob o = lookupBlob(idBuffer);
				if ((o.flags & SEEN) != 0)
					break;
				o.flags |= SEEN;
				if (shouldSkipObject(o))
					break;
				last = o;
				return o;
			}
			case Constants.OBJ_TREE: {
				treeWalk.getEntryObjectId(idBuffer);
				final RevTree o = lookupTree(idBuffer);
				if ((o.flags & SEEN) != 0)
					break;
				o.flags |= SEEN;
				if (shouldSkipObject(o))
					break;
				last = o;
				return o;
			}
			default:
				if (FileMode.GITLINK.equals(mode))
					break;
				treeWalk.getEntryObjectId(idBuffer);
				throw new CorruptObjectException(MessageFormat.format(JGitText.get().corruptObjectInvalidMode3
						, mode , idBuffer.name() , treeWalk.getEntryPathString() , currentTree.name()));
			}

			treeWalk = treeWalk.next();
		}

		if (firstCommit != null) {
			reader.walkAdviceBeginTrees(this, firstCommit, lastCommit);
			firstCommit = null;
			lastCommit = null;
		}

		last = null;
		for (;;) {
			final RevObject o = pendingObjects.next();
			if (o == null) {
				reader.walkAdviceEnd();
				return null;
			}
			if ((o.flags & SEEN) != 0)
				continue;
			o.flags |= SEEN;
			if (shouldSkipObject(o))
				continue;
			if (o instanceof RevTree) {
				currentTree = (RevTree) o;
				treeWalk = treeWalk.resetRoot(reader, currentTree);
			}
			return o;
		}
	}

	private CanonicalTreeParser enter(RevObject tree) throws IOException {
		CanonicalTreeParser p = treeWalk.createSubtreeIterator0(reader, tree);
		if (p.eof()) {
			// We can't tolerate the subtree being an empty tree, as
			// that will break us out early before we visit all names.
			// If it is, advance to the parent's next record.
			//
			return treeWalk.next();
		}
		return p;
	}

	private final boolean shouldSkipObject(final RevObject o) {
		return (o.flags & UNINTERESTING) != 0 && !hasRevSort(RevSort.BOUNDARY);
	}

	/**
	 * Verify all interesting objects are available, and reachable.
	 * <p>
	 * Callers should populate starting points and ending points with
	 * {@link #markStart(RevObject)} and {@link #markUninteresting(RevObject)}
	 * and then use this method to verify all objects between those two points
	 * exist in the repository and are readable.
	 * <p>
	 * This method returns successfully if everything is connected; it throws an
	 * exception if there is a connectivity problem. The exception message
	 * provides some detail about the connectivity failure.
	 *
	 * @throws MissingObjectException
	 *             one or or more of the next objects are not available from the
	 *             object database, but were thought to be candidates for
	 *             traversal. This usually indicates a broken link.
	 * @throws IncorrectObjectTypeException
	 *             one or or more of the objects in a tree do not match the type
	 *             indicated.
	 * @throws IOException
	 *             a pack file or loose object could not be read.
	 */
	public void checkConnectivity() throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		for (;;) {
			final RevCommit c = next();
			if (c == null)
				break;
		}
		for (;;) {
			final RevObject o = nextObject();
			if (o == null)
				break;
			if (o instanceof RevBlob && !reader.has(o))
				throw new MissingObjectException(o, Constants.TYPE_BLOB);
		}
	}

	/**
	 * Get the current object's complete path.
	 * <p>
	 * This method is not very efficient and is primarily meant for debugging
	 * and final output generation. Applications should try to avoid calling it,
	 * and if invoked do so only once per interesting entry, where the name is
	 * absolutely required for correct function.
	 *
	 * @return complete path of the current entry, from the root of the
	 *         repository. If the current entry is in a subtree there will be at
	 *         least one '/' in the returned string. Null if the current entry
	 *         has no path, such as for annotated tags or root level trees.
	 */
	public String getPathString() {
		return last != null ? treeWalk.getEntryPathString() : null;
	}

	/**
	 * Get the current object's path hash code.
	 * <p>
	 * This method computes a hash code on the fly for this path, the hash is
	 * suitable to cluster objects that may have similar paths together.
	 *
	 * @return path hash code; any integer may be returned.
	 */
	public int getPathHashCode() {
		return last != null ? treeWalk.getEntryPathHashCode() : 0;
	}

	@Override
	public void dispose() {
		super.dispose();
		pendingObjects = new BlockObjQueue();
		treeWalk = new CanonicalTreeParser();
		currentTree = null;
		last = null;
		firstCommit = null;
		lastCommit = null;
	}

	@Override
	protected void reset(final int retainFlags) {
		super.reset(retainFlags);
		pendingObjects = new BlockObjQueue();
		treeWalk = new CanonicalTreeParser();
		currentTree = null;
		last = null;
		firstCommit = null;
		lastCommit = null;
	}

	private void addObject(final RevObject o) {
		if ((o.flags & IN_PENDING) == 0) {
			o.flags |= IN_PENDING;
			pendingObjects.add(o);
		}
	}

	private void markTreeUninteresting(final RevTree tree)
			throws MissingObjectException, IncorrectObjectTypeException,
			IOException {
		if ((tree.flags & UNINTERESTING) != 0)
			return;
		tree.flags |= UNINTERESTING;

		treeWalk = treeWalk.resetRoot(reader, tree);
		while (!treeWalk.eof()) {
			final FileMode mode = treeWalk.getEntryFileMode();
			final int sType = mode.getObjectType();

			switch (sType) {
			case Constants.OBJ_BLOB: {
				treeWalk.getEntryObjectId(idBuffer);
				lookupBlob(idBuffer).flags |= UNINTERESTING;
				break;
			}
			case Constants.OBJ_TREE: {
				treeWalk.getEntryObjectId(idBuffer);
				final RevTree t = lookupTree(idBuffer);
				if ((t.flags & UNINTERESTING) == 0) {
					t.flags |= UNINTERESTING;
					treeWalk = treeWalk.createSubtreeIterator0(reader, t);
					continue;
				}
				break;
			}
			default:
				if (FileMode.GITLINK.equals(mode))
					break;
				treeWalk.getEntryObjectId(idBuffer);
				throw new CorruptObjectException(MessageFormat.format(JGitText.get().corruptObjectInvalidMode3
						, mode , idBuffer.name() , treeWalk.getEntryPathString() , tree));
			}

			treeWalk = treeWalk.next();
		}
	}
}
