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

/**
 * Line class that spans one or more revisions.
 *
 * @author Kevin Sawicki (kevin@github.com)
 */
public class Line {

	private String content;

	private int start;

	private int end;

	private int number;

	private int[] numbers;

	/**
	 * Create empty line
	 */
	public Line() {
		this.numbers = new int[0];
	}

	/**
	 * Create line
	 *
	 * @param start
	 * @param content
	 */
	public Line(int start, String content) {
		this(start, start, content);
	}

	/**
	 * Create line
	 *
	 * @param start
	 * @param end
	 * @param content
	 */
	public Line(int start, int end, String content) {
		this();
		this.start = start;
		this.end = end;
		this.content = content;
	}

	/**
	 * Get number of line
	 *
	 * @return number
	 */
	public int getNumber() {
		return this.number;
	}

	/**
	 * Get line number at revision
	 *
	 * @param revision
	 * @return line number
	 */
	public int getNumber(int revision) {
		if (revision < this.start || revision > this.end)
			return -1;
		revision -= this.start;
		return numbers[revision];
	}

	/**
	 * Does this line appear in at least one common revision with the specified
	 * line?
	 *
	 * @param line
	 * @return true if overlaps at least one revision, false otherwise
	 */
	public boolean overlaps(Line line) {
		if (start > line.start)
			return start <= line.end;
		else if (start < line.start)
			return end >= line.start;
		else
			return true;
	}

	/**
	 * Set number of line
	 *
	 * @param number
	 * @return this line
	 */
	public Line setNumber(int number) {
		this.number = number;
		int[] newNumbers = new int[numbers.length + 1];
		System.arraycopy(numbers, 0, newNumbers, 0, numbers.length);
		newNumbers[newNumbers.length - 1] = number;
		numbers = newNumbers;
		return this;
	}

	/**
	 * Set content of line
	 *
	 * @param content
	 * @return this line
	 */
	public Line setContent(String content) {
		this.content = content;
		return this;
	}

	/**
	 * Get line content
	 *
	 * @return content
	 */
	public String getContent() {
		return this.content;
	}

	/**
	 * Set end revision number
	 *
	 * @param end
	 * @return this line
	 */
	public Line setEnd(int end) {
		this.end = end;
		return this;
	}

	/**
	 * Get end revision number
	 *
	 * @return number
	 */
	public int getEnd() {
		return this.end;
	}

	/**
	 * Set start revision number
	 *
	 * @param start
	 * @return this line
	 */
	public Line setStart(int start) {
		this.start = start;
		return this;
	}

	/**
	 * Get start revision number
	 *
	 * @return number
	 */
	public int getStart() {
		return this.start;
	}

	/**
	 * Get number of revisions this line occurs in
	 *
	 * @return revision count
	 */
	public int getAge() {
		return (this.end - this.start) + 1;
	}

	/**
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		return this.start + "-" + this.end + ": " + this.content;
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
		else if (obj instanceof Line) {
			Line other = (Line) obj;
			return this.start == other.start && this.end == other.end
					&& this.number == other.number;
		}
		return false;
	}
}
