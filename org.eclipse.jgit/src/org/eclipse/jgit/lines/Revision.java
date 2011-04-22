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

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;

/**
 * Class containing all the lines present at a file revision.
 *
 * @author Kevin Sawicki (kevin@github.com)
 */
public class Revision implements Iterable<Line> {

	private int number;

	private int size;

	private RevCommit commit;

	private ObjectId blob;

	private List<Line> lines;

	/**
	 * Create revision
	 */
	public Revision() {
		this.lines = new ArrayList<Line>();
	}

	/**
	 * @see java.lang.Object#hashCode()
	 */
	public int hashCode() {
		return toString().hashCode();
	}

	/**
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	public boolean equals(Object obj) {
		if (obj == this)
			return true;
		else if (obj instanceof Revision) {
			Revision other = (Revision) obj;
			return this.number == other.number
					&& this.lines.equals(other.lines);
		} else
			return false;
	}

	/**
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		return "Revision: " + this.number + " Commit: " + this.commit.name()
				+ " Blob: " + this.blob.name();
	}

	/**
	 * @return commit
	 */
	public RevCommit getCommit() {
		return this.commit;
	}

	/**
	 * @param commit
	 * @return this revision
	 */
	public Revision setCommit(RevCommit commit) {
		this.commit = commit;
		return this;
	}

	/**
	 * @param blob
	 * @return this revision
	 */
	public Revision setBlob(ObjectId blob) {
		this.blob = blob;
		return this;
	}

	/**
	 * Get blob
	 *
	 * @return blob id
	 */
	public ObjectId getBlob() {
		return this.blob;
	}

	/**
	 * Set revision number
	 *
	 * @param number
	 * @return revision
	 */
	public Revision setNumber(int number) {
		this.number = number;
		return this;
	}

	/**
	 * Get revision number
	 *
	 * @return number
	 */
	public int getNumber() {
		return this.number;
	}

	/**
	 * Add line
	 *
	 * @param line
	 * @return this revision
	 */
	public Revision addLine(Line line) {
		if (line != null) {
			lines.add(line.setNumber(lines.size()));
			this.size += line.getContent().length();
		}
		return this;
	}

	/**
	 * Get number of lines in revison
	 *
	 * @return line count
	 */
	public int getLineCount() {
		return this.lines.size();
	}

	/**
	 * Get line at number
	 *
	 * @param lineNumber
	 * @return line
	 */
	public Line getLine(int lineNumber) {
		return lineNumber >= 0 && lineNumber < this.lines.size() ? this.lines
				.get(lineNumber) : null;
	}

	/**
	 * Merge line into this revision
	 *
	 * @param line
	 * @param lineNumber
	 * @return this revision
	 */
	public Revision merge(Line line, int lineNumber) {
		line.setEnd(this.number);
		this.size += line.getContent().length();
		Line removed = this.lines.set(lineNumber, line);
		if (removed != null)
			this.size -= removed.getContent().length();
		return this;
	}

	/**
	 * Merge line into revision
	 *
	 * @param line
	 * @return this revision
	 */
	public Revision merge(Line line) {
		return merge(line, line.getNumber());
	}

	/**
	 * Get lines
	 *
	 * @return list of lines
	 */
	public List<Line> getLines() {
		return this.lines;
	}

	/**
	 * @see java.lang.Iterable#iterator()
	 */
	public Iterator<Line> iterator() {
		return this.lines.iterator();
	}

	/**
	 * Get revision as byte array
	 *
	 * @return byte array
	 */
	public byte[] getBytes() {
		byte[] bytes = new byte[this.size + getLineCount()];
		int index = 0;
		for (Line line : this) {
			byte[] copy = line.getContent().getBytes();
			System.arraycopy(copy, 0, bytes, index, copy.length);
			index += copy.length;
			bytes[index] = '\n';
			index++;
		}
		return bytes;
	}
}
