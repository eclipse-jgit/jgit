/*******************************************************************************
 *  Copyright (c) 2011 GitHub Inc.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *    Kevin Sawicki (GitHub Inc.) - initial API and implementation
 *******************************************************************************/
package org.eclipse.jgit.lines;

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
