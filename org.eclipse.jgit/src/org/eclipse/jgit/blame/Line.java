/*
 * Copyright (C) 2011, GitHub Inc.
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

/**
 * Line class that spans one or more revisions.
 */
public class Line {

	private int startRevision;

	private int endRevision;

	private int[] numbers = new int[0];

	/**
	 * Create line
	 *
	 * @param start
	 */
	public Line(int start) {
		this(start, start);
	}

	/**
	 * Create line
	 *
	 * @param start
	 * @param end
	 */
	public Line(int start, int end) {
		this.startRevision = start;
		this.endRevision = end;
	}

	/**
	 * Get number of line in latest revision it was present in
	 *
	 * @return number
	 */
	public int getNumber() {
		return getNumber(getEnd());
	}

	/**
	 * Get line number at revision
	 *
	 * @param revision
	 * @return line number
	 */
	public int getNumber(int revision) {
		if (revision < this.startRevision || revision > this.endRevision)
			return -1;
		revision -= this.startRevision;
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
		if (startRevision > line.startRevision)
			return startRevision <= line.endRevision;
		else if (startRevision < line.startRevision)
			return endRevision >= line.startRevision;
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
		int[] newNumbers = new int[numbers.length + 1];
		System.arraycopy(numbers, 0, newNumbers, 0, numbers.length);
		newNumbers[newNumbers.length - 1] = number;
		numbers = newNumbers;
		return this;
	}

	/**
	 * Set end revision number
	 *
	 * @param end
	 * @return this line
	 */
	public Line setEnd(int end) {
		this.endRevision = end;
		return this;
	}

	/**
	 * Get end revision number
	 *
	 * @return number
	 */
	public int getEnd() {
		return this.endRevision;
	}

	/**
	 * Set start revision number
	 *
	 * @param start
	 * @return this line
	 */
	public Line setStart(int start) {
		this.startRevision = start;
		return this;
	}

	/**
	 * Get start revision number
	 *
	 * @return number
	 */
	public int getStart() {
		return this.startRevision;
	}

	/**
	 * Get number of revisions this line occurs in
	 *
	 * @return revision count
	 */
	public int getAge() {
		return (this.endRevision - this.startRevision) + 1;
	}

	@Override
	public String toString() {
		return this.startRevision + "-" + this.endRevision + ": " + getNumber();
	}

	@Override
	public int hashCode() {
		return (((this.startRevision * 31) + this.endRevision) * 31)
				+ getNumber();
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this)
			return true;
		else if (obj instanceof Line) {
			Line other = (Line) obj;
			return this.startRevision == other.startRevision
					&& this.endRevision == other.endRevision
					&& getNumber() == other.getNumber();
		}
		return false;
	}
}
