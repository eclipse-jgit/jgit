/*
 * Copyright (C) 2010, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
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
