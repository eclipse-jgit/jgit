/*
 * Copyright (C) 2011, Kevin Sawicki <kevin@github.com>
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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Container class for the line history of all revisions of a file.
 *
 * @author Kevin Sawicki (kevin@github.com)
 */
public class RevisionContainer implements Iterable<Revision> {

	private List<Revision> revisions;

	/**
	 * Create file history
	 */
	public RevisionContainer() {
		this.revisions = new ArrayList<Revision>();
	}

	/**
	 * Add revision to history
	 *
	 * @param revision
	 * @return history
	 */
	public RevisionContainer addRevision(Revision revision) {
		if (revision != null)
			this.revisions.add(revision.setNumber(getRevisionCount() + 1));
		return this;
	}

	/**
	 * Get revision count
	 *
	 * @return count
	 */
	public int getRevisionCount() {
		return this.revisions.size();
	}

	/**
	 * Get revision by revision number
	 *
	 * @param number
	 * @return revision or null if none
	 */
	public Revision getRevision(int number) {
		number--;
		return number >= 0 && number < this.revisions.size() ? this.revisions
				.get(number) : null;
	}

	/**
	 * Get first revision
	 *
	 * @return revision
	 */
	public Revision getFirst() {
		return getRevision(1);
	}

	/**
	 * Get last revision
	 *
	 * @return revision
	 */
	public Revision getLast() {
		return getRevision(this.revisions.size());
	}

	/**
	 * Get previous revision
	 *
	 * @param revision
	 * @return previous
	 */
	public Revision getPrevious(Revision revision) {
		if (revision == null)
			return null;
		return getRevision(revision.getNumber() - 1);
	}

	/**
	 * Get next revision
	 *
	 * @param revision
	 * @return next
	 */
	public Revision getNext(Revision revision) {
		if (revision == null)
			return null;
		return getRevision(revision.getNumber() + 1);
	}

	/**
	 * @see java.lang.Iterable#iterator()
	 */
	public Iterator<Revision> iterator() {
		return this.revisions.iterator();
	}

	/**
	 * Get a sorted set of lines from every revision in this container. The
	 * returned set will be the comprehensive line history containing every line
	 * from every revision.
	 *
	 * @return non-null but possibly empty set of lines
	 */
	public Set<Line> getSortedLines() {
		Set<Line> lines = new TreeSet<Line>(new LineComparator());

		for (Revision rev : this)
			lines.addAll(rev.getLines());

		return lines;
	}

}
