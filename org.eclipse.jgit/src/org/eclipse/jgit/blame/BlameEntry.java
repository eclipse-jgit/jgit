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


/**
 * Blame entry, associating line numbers in the blamed file with origins The
 * output of blame is a list of these, covering all lines
 *
 *
 */
// TODO improve API
public class BlameEntry {
	/**
	 * line range in the original file
	 */
	public Range originalRange;

	/**
	 * origin to blame for the lines in the original range
	 */
	public Origin suspect;

	/**
	 * start line number in the suspects version
	 */
	public int suspectStart;

	/**
	 * indicates if the suspect has been found guilty this is initially false -
	 * in dubio pro reo
	 */
	public boolean guilty = false;

	@Override
	public String toString() {

		return String.format("(%d -> %d,  %d)", Integer
				.valueOf(originalRange.start), Integer.valueOf(suspectStart),
				Integer.valueOf(originalRange.length));
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (guilty ? 1231 : 1237);
		result = prime * result
				+ ((originalRange == null) ? 0 : originalRange.hashCode());
		result = prime * result + ((suspect == null) ? 0 : suspect.hashCode());
		result = prime * result + suspectStart;
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
		BlameEntry other = (BlameEntry) obj;
		if (guilty != other.guilty)
			return false;
		if (originalRange == null) {
			if (other.originalRange != null)
				return false;
		} else if (!originalRange.equals(other.originalRange))
			return false;
		if (suspect == null) {
			if (other.suspect != null)
				return false;
		} else if (!suspect.equals(other.suspect))
			return false;
		if (suspectStart != other.suspectStart)
			return false;
		return true;
	}

}