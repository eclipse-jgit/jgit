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

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * A continuous sequence of lines that are common between two files
 */
public class CommonChunk {
	final int astart;

	final int bstart;

	final int length;

	/**
	 * Standard constructor
	 *
	 * @param astart
	 *            start of chunk in file A (0-based line index)
	 * @param bstart
	 *            start of chunk in file B (0-based line index)
	 * @param length
	 *            length of the chunk (in lines)
	 */
	public CommonChunk(int astart, int bstart, int length) {
		super();
		this.astart = astart;
		this.bstart = bstart;
		this.length = length;
	}

	@Override
	public String toString() {
		return String.format("Common <%d:%d  %d>", Integer.valueOf(astart),
				Integer.valueOf(bstart), Integer.valueOf(length));
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + astart;
		result = prime * result + bstart;
		result = prime * result + length;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		CommonChunk other = (CommonChunk) obj;
		if (astart != other.astart)
			return false;
		if (bstart != other.bstart)
			return false;
		if (length != other.length)
			return false;
		return true;
	}

	/**
	 * @return start of chunk in B (0-based)
	 */
	public int getAstart() {
		return astart;
	}

	/**
	 * @return start of chunk in B (0-based)
	 */
	public int getBstart() {
		return bstart;
	}

	/**
	 * @return length of common chunk
	 */
	public int getLength() {
		return length;
	}

	/**
	 * Compute common chunks (i.e. LCSs) from a list of differences between to
	 * lists a and b
	 * 
	 * @param differences
	 *            the calculated differences for a and b
	 * @param lengthA
	 *            the total length of A
	 * @param lengthB
	 *            the total length of B
	 * @return list the list of common subsequences
	 */
	public static List<CommonChunk> computeCommonChunks(
			List<? extends IDifference> differences, int lengthA, int lengthB) {
		List<CommonChunk> result = new LinkedList<CommonChunk>();
		// check no differences -> all in common
		if (differences.isEmpty()) {
			result.add(new CommonChunk(0, 0, lengthA));
			return result;
		}
		IDifference firstDifference = differences.get(0);
		// common prefix
		int commonLengthA = firstDifference.getStartA();
		int commonLengthB = firstDifference.getStartB();
		if (commonLengthA != commonLengthB) {
			throw new RuntimeException("lengths not equal: " + commonLengthA
					+ "!=" + commonLengthB);
		}
		int commonPrefixLength = commonLengthA;
		if (commonPrefixLength > 0) {
			result.add(new CommonChunk(0, 0, commonPrefixLength));
		}
		Iterator<? extends IDifference> it = differences.iterator();
		IDifference previousDifference = it.next();

		for (; it.hasNext();) {
			IDifference nextDifference = it.next();
			int firstCommonLineA = previousDifference.getStartA()
					+ previousDifference.getLengthA();
			int firstCommonLineB = previousDifference.getStartB()
					+ previousDifference.getLengthB();
			commonLengthA = nextDifference.getStartA() - firstCommonLineA;
			commonLengthB = nextDifference.getStartB() - firstCommonLineB;
			if (commonLengthA != commonLengthB) {
				throw new RuntimeException("lengths not equal: "
						+ commonLengthA + "!=" + commonLengthB);
			}
			result.add(new CommonChunk(firstCommonLineA, firstCommonLineB,
					commonLengthA));
			previousDifference = nextDifference;
		}

		// common suffix
		IDifference lastDifference = differences.get(differences.size() - 1);
		int firstCommonLineA = lastDifference.getStartA()
				+ lastDifference.getLengthA();
		int firstCommonLineB = lastDifference.getStartB()
				+ lastDifference.getLengthB();
		commonLengthA = lengthA - firstCommonLineA;
		commonLengthB = lengthB - firstCommonLineB;
		if (commonLengthA != commonLengthB) {
			throw new RuntimeException("lengths not equal: " + commonLengthA
					+ "!=" + commonLengthB);
		}
		int commonSuffixLength = commonLengthA;

		if (commonSuffixLength > 0) {
			result.add(new CommonChunk(firstCommonLineA, firstCommonLineB,
					commonSuffixLength));
		}

		return result;
	}


}