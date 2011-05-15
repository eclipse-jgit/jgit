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

import java.util.Arrays;

import org.eclipse.jgit.revwalk.RevCommit;

/**
 * Line class that spans one or more continuous revisions.
 */
public class Line {

	private RevCommit startCommit;

	private int[] numbers = new int[0];

	/**
	 * Create line with starting commit
	 *
	 * @param start
	 */
	public Line(RevCommit start) {
		startCommit = start;
	}

	/**
	 * Get the number of this line in the latest revision it was present in
	 *
	 * @return number
	 */
	public int getNumber() {
		return getNumber(numbers.length - 1);
	}

	/**
	 * Get line number at index.
	 *
	 * @param index
	 * @return line number
	 */
	public int getNumber(int index) {
		return numbers[index];
	}

	/**
	 * Set number of line
	 *
	 * @param number
	 * @return this line
	 */
	Line setNumber(int number) {
		int[] newNumbers = new int[numbers.length + 1];
		newNumbers[0] = number;
		System.arraycopy(numbers, 0, newNumbers, 1, numbers.length);
		numbers = newNumbers;
		return this;
	}

	/**
	 * Set start commit
	 *
	 * @param start
	 * @return this line
	 */
	Line setStart(RevCommit start) {
		startCommit = start;
		return this;
	}

	/**
	 * Get the commit in which this line was introduced
	 *
	 * @return number
	 */
	public RevCommit getStart() {
		return startCommit;
	}

	/**
	 * Get number of consecutive revisions this line occurs in
	 *
	 * @return revision count
	 */
	public int getAge() {
		return numbers.length;
	}

	public String toString() {
		return "Added in: " + startCommit.abbreviate(7) + ", Revisions:"
				+ numbers.length;
	}

	public int hashCode() {
		return (((startCommit.hashCode() * 31) + numbers.length) * 31)
				+ getNumber();
	}

	public boolean equals(Object obj) {
		if (obj == this)
			return true;
		else if (obj instanceof Line) {
			Line other = (Line) obj;
			return startCommit.equals(other.startCommit)
					&& Arrays.equals(numbers, other.numbers);
		}
		return false;
	}
}
