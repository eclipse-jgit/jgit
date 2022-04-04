/*
 * Copyright (C) 2008-2009, Google Inc.
 * Copyright (C) 2009, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.dircache;

import java.io.IOException;

import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;

/**
 * Iterate and update a {@link org.eclipse.jgit.dircache.DirCache} as part of a
 * <code>TreeWalk</code>.
 * <p>
 * Like {@link org.eclipse.jgit.dircache.DirCacheIterator} this iterator allows
 * a DirCache to be used in parallel with other sorts of iterators in a
 * TreeWalk. Unless {@link #copyOnSkip} is off, any entry which appears in the source DirCache and which is
 * skipped by the TreeFilter is automatically copied into
 * {@link org.eclipse.jgit.dircache.DirCacheBuilder}, thus retaining it in the
 * newly updated index.
 * <p>
 * This iterator is suitable for update processes, or even a simple delete
 * algorithm. For example deleting a path:
 *
 * <pre>
 * final DirCache dirc = db.lockDirCache();
 * final DirCacheBuilder edit = dirc.builder();
 *
 * final TreeWalk walk = new TreeWalk(db);
 * walk.reset();
 * walk.setRecursive(true);
 * walk.setFilter(PathFilter.create(&quot;name/to/remove&quot;));
 * walk.addTree(new DirCacheBuildIterator(edit));
 *
 * while (walk.next())
 * 	; // do nothing on a match as we want to remove matches
 * edit.commit();
 * </pre>
 */
public class DirCacheBuildIterator extends DirCacheIterator {
	private final DirCacheBuilder builder;

	/**
	 * Whether the iterator should copy all seen entries to {@link #builder}.
	 */
	private final boolean copyOnSkip;

	/**
	 * Create a new iterator for an already loaded DirCache instance.
	 * <p>
	 * The iterator implementation may copy part of the cache's data during
	 * construction, so the cache must be read in prior to creating the
	 * iterator.
	 *
	 * <p>When walked by {@link org.eclipse.jgit.treewalk.TreeWalk}, the created iterator will copy entries, matched by {@link org.eclipse.jgit.treewalk.filter.TreeFilter} to the {@link org.eclipse.jgit.dircache.DirCacheBuilder}
	 *
	 * @param dcb
	 *            the cache builder for the cache to walk. The cache must be
	 *            already loaded into memory.
	 */
	public DirCacheBuildIterator(DirCacheBuilder dcb) {
		this(dcb, true);
	}

	/**
	 * Create a new iterator for an already loaded DirCache instance.
	 * <p> With {@code copyOnSkip} false, this constructor can be used to create an iterator on {@link DirCacheBuilder}
	 * that was walked before and thus contains all interesting entries already.
	 *
	 * @param dcb
	 *            the cache builder for the cache to walk. The cache must be
	 *            already loaded into memory.
	 * @param copyOnSkip whether the iterator should copy the matched entries to {@link #builder}
	 */
	public DirCacheBuildIterator(DirCacheBuilder dcb, boolean copyOnSkip) {
		super(dcb.getDirCache());
		builder = dcb;
		this.copyOnSkip = copyOnSkip;
	}

	DirCacheBuildIterator(final DirCacheBuildIterator p,
			final DirCacheTree dct, boolean copyOnSkip) {
		super(p, dct);
		builder = p.builder;
		this.copyOnSkip = copyOnSkip;
	}

	/** {@inheritDoc} */
	@Override
	public AbstractTreeIterator createSubtreeIterator(ObjectReader reader)
			throws IncorrectObjectTypeException, IOException {
		if (currentSubtree == null)
			throw new IncorrectObjectTypeException(getEntryObjectId(),
					Constants.TYPE_TREE);
		return new DirCacheBuildIterator(this, currentSubtree, this.copyOnSkip);
	}

	/** {@inheritDoc} */
	@Override
	public void skip() throws CorruptObjectException {
		if(copyOnSkip) {
			if (currentSubtree != null)
				builder.keep(ptr, currentSubtree.getEntrySpan());
			else
				builder.keep(ptr, 1);
		}
		next(1);
	}

	/** {@inheritDoc} */
	@Override
	public void stopWalk() {
		final int cur = ptr;
		final int cnt = cache.getEntryCount();
		if (cur < cnt)
			builder.keep(cur, cnt - cur);
	}

	/** {@inheritDoc} */
	@Override
	protected boolean needsStopWalk() {
		return ptr < cache.getEntryCount();
	}
}
