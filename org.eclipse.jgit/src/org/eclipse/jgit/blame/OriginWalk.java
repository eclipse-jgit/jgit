/*
 * Copyright (C) 2010, Benjamin Muskalla <bmuskalla@eclipsesource.com>
 * Copyright (C) 2008, Manuel Woelker <manuel.woelker@gmail.com>
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
package org.eclipse.jgit.blame;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.Tree;
import org.eclipse.jgit.lib.TreeEntry;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * Class for walking origins
 *
 */
public class OriginWalk implements Iterable<Origin>, Iterator<Origin> {

	private static final Origin[] NO_ORIGINS = new Origin[0];

	final Origin initalOrigin;

	final Repository repository;

	HashMap<RevCommit, HashSet<Origin>> commitMap = new HashMap<RevCommit, HashSet<Origin>>();

	// Origins which have not been visited yet
	LinkedList<Origin> pendingOrigins = new LinkedList<Origin>();

	private RevWalk revWalk;

	private IOriginSearchStrategy[] originSearchStrategies = new IOriginSearchStrategy[] {
			new SameNameOriginSearchStrategy(),
			new RenameModifiedSearchStrategy(),
//			new CopyModifiedSearchStrategy(),
			};

	private Origin[] parentOrigins = NO_ORIGINS;

	private Origin currentOrigin;

	private Origin[] ancestorOrigins;

	private final boolean skipFirst;

	private HashSet<Origin> seenOrigins = new HashSet<Origin>();

	/**
	 * Standard constructor
	 *
	 * @param initalOrigin
	 * @param skipFirst skip the first origin if it contains no changes from the second to last origin
	 */
	public OriginWalk(Origin initalOrigin, boolean skipFirst)

			 {
		super();
		try {
			this.initalOrigin = initalOrigin;
			this.skipFirst = skipFirst;
			this.repository = initalOrigin.getRepository();
			revWalk = new RevWalk(repository);
			revWalk.sort(RevSort.TOPO);
			revWalk.markStart(initalOrigin.getCommit());
			RevCommit startCommit = initalOrigin.getCommit();

			if(skipFirst) {
				startCommit = findLastSameCommit(initalOrigin);
			}

			Origin firstOrigin = new Origin(repository, startCommit,
					initalOrigin.filename);
			queueOrigin(firstOrigin);
		} catch (Exception e) {
			throw new RuntimeException("Unable to create Origin walk", e);
		}
	}

	private void queueOrigin(Origin origin) {
		if(!seenOrigins.contains(origin)) {
			pendingOrigins.add(origin);
			seenOrigins.add(origin);
		}
	}

	/**
	 * Copy constructor
	 *
	 * @param other
	 * @throws IOException
	 * @throws IncorrectObjectTypeException
	 * @throws MissingObjectException
	 */
	public OriginWalk(OriginWalk other) throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		this(other.initalOrigin, other.skipFirst);
	}

	/** Constructor for log use
	 * @param initalOrigin
	 */
	public OriginWalk(Origin initalOrigin) {
		this(initalOrigin, true);
	}

	public Iterator<Origin> iterator() {
		try {
			return new OriginWalk(this);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public boolean hasNext() {
		return !pendingOrigins.isEmpty();
	}

	public Origin next() {
		try {
			if(pendingOrigins.isEmpty()) {
				return null;
			}
			currentOrigin = pendingOrigins.remove();
			parentOrigins = NO_ORIGINS;
			HashSet<Origin> pOrigins = new HashSet<Origin>();
			for (IOriginSearchStrategy strategy : originSearchStrategies) {
				parentOrigins = strategy.findOrigins(currentOrigin);
				pOrigins.addAll(Arrays.asList(parentOrigins));
				if (pOrigins.size() > 1) {
					break;
				}
			}
			parentOrigins = pOrigins.toArray(new Origin[0]);
			ancestorOrigins = new Origin[parentOrigins.length];
			for (int i = 0; i < ancestorOrigins.length; i++) {
				Origin parentOrigin = parentOrigins[i];
				RevCommit ancestorCommit = findLastSameCommit(parentOrigin);
				Origin ancestorOrigin = new Origin(repository, ancestorCommit,
						parentOrigin.filename);
				ancestorOrigins[i] = ancestorOrigin;
				queueOrigin(ancestorOrigin);
			}
			return currentOrigin;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private RevCommit findLastSameCommit(Origin origin)
			throws MissingObjectException, IncorrectObjectTypeException,
			IOException {
		RevWalk rw = new RevWalk(repository);
		rw.markStart(origin.commit);
		rw.sort(RevSort.TOPO);
		RevCommit lastFoundCommit = origin.commit;
		while (true) {
			RevCommit newLastFoundCommit = null;
			for (RevCommit parent : lastFoundCommit.getParents()) {
				parent = rw.parseCommit(parent);
				Tree tree = repository.mapTree(parent.getTree());
				TreeEntry blobMember = tree.findBlobMember(origin.filename);
				if (blobMember != null
						&& blobMember.getId().equals(origin.getObjectId())) {
					newLastFoundCommit = parent;
					break;
				}
			}
			if (newLastFoundCommit != null) {
				lastFoundCommit = newLastFoundCommit;
			} else {
				break;
			}
		}
		return lastFoundCommit;
	}

	/**
	 * @return parent origins for the current origin
	 */
	public Origin[] getParentOrigins() {
		return parentOrigins;
	}

	/**
	 * Non-trivial ancestor origins, non-trivial means that these ancestor
	 * origins differ from the current origin
	 *
	 * @return non-trivial ancestors of current origin
	 */
	public Origin[] getAncestorOrigins() {
		return ancestorOrigins;
	}

	public void remove() {
		throw new UnsupportedOperationException();
	}

}
