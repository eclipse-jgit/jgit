/*
 * Copyright (C) 2010, Google Inc.
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

package org.eclipse.jgit.util;

/**
 * Utility class for character functions on raw bytes
 * <p>
 * Characters are assumed to be 8-bit US-ASCII.
 */
public class RawCharUtil {
	private static final boolean[] WHITESPACE = new boolean[256];

	static {
		WHITESPACE['\r'] = true;
		WHITESPACE['\n'] = true;
		WHITESPACE['\t'] = true;
		WHITESPACE[' '] = true;
	}

	/**
	 * Determine if an 8-bit US-ASCII encoded character is represents whitespace
	 *
	 * @param c
	 *            the 8-bit US-ASCII encoded character
	 * @return true if c represents a whitespace character in 8-bit US-ASCII
	 */
	public static boolean isWhitespace(byte c) {
		return WHITESPACE[c & 0xff];
	}

	/**
	 * Returns the new end point for the byte array passed in after trimming any
	 * trailing whitespace characters, as determined by the isWhitespace()
	 * function. start and end are assumed to be within the bounds of raw.
	 *
	 * @param raw
	 *            the byte array containing the portion to trim whitespace for
	 * @param start
	 *            the start of the section of bytes
	 * @param end
	 *            the end of the section of bytes
	 * @return the new end point
	 */
	public static int trimTrailingWhitespace(byte[] raw, int start, int end) {
		int ptr = end - 1;
		while (start <= ptr && isWhitespace(raw[ptr]))
			ptr--;

		return ptr + 1;
	}

	/**
	 * Returns the new start point for the byte array passed in after trimming
	 * any leading whitespace characters, as determined by the isWhitespace()
	 * function. start and end are assumed to be within the bounds of raw.
	 *
	 * @param raw
	 *            the byte array containing the portion to trim whitespace for
	 * @param start
	 *            the start of the section of bytes
	 * @param end
	 *            the end of the section of bytes
	 * @return the new start point
	 */
	public static int trimLeadingWhitespace(byte[] raw, int start, int end) {
		while (start < end && isWhitespace(raw[start]))
			start++;

		return start;
	}

	private RawCharUtil() {
		// This will never be called
	}
}
