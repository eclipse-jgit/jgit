package org.eclipse.jgit.util;

/**
 * Utility class for character functions on raw bytes
 * <p>
 * Characters are assumed to be 8-bit US-ASCII.
 *
 * @author jeffschu@google.com (Jeff Schumacher)
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
		return WHITESPACE[c];
	}
}
