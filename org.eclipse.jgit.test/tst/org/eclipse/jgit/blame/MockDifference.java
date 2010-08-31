/*
 * Copyright (C) 2010, Benjamin Muskalla <bmuskalla@eclipsesource.com>
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

class MockDifference implements IDifference {

	final int startA;

	final int endA;

	final int startB;

	final int endB;

	/**
	 * Standard constructor
	 *
	 * @param startA
	 *            start in file A (0-based line index)
	 * @param endA
	 *            end in file A (0-based line index)
	 * @param startB
	 *            start in file B (0-based line index)
	 * @param endB
	 *            end in file B (0-based line index)
	 */
	public MockDifference(int startA, int endA, int startB, int endB) {
		this.startA = startA;
		this.endA = endA;
		this.startB = startB;
		this.endB = endB;
	}

	public int getStartA() {
		return startA;
	}

	public int getStartB() {
		return startB;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + endA;
		result = prime * result + endB;
		result = prime * result + startA;
		result = prime * result + startB;
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
		MockDifference other = (MockDifference) obj;
		if (endA != other.endA)
			return false;
		if (endB != other.endB)
			return false;
		if (startA != other.startA)
			return false;
		if (startB != other.startB)
			return false;
		return true;
	}

	public int getLengthA() {
		return endA < 0 ? 0 : endA - startA + 1;
	}

	public int getLengthB() {
		return endB < 0 ? 0 : endB - startB + 1;
	}

	MockDifference inverted() {
		return new MockDifference(getStartB(), getStartB() + getLengthB() - 1,
				getStartA(), getStartA() + getLengthA() - 1);
	}

}