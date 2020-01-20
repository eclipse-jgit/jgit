package org.eclipse.jgit.util;

/**
 * Encodes and decodes to and from hexadecimal notation.
 */
public final class Hex {

	private static final char[] HEX = "0123456789ABCDEF".toCharArray();

	/** Defeats instantiation. */
	private Hex() {
		// Suppress empty block warning.
	}

	/**
	 * Decodes a hexadecimal string to a byte array.
	 *
	 * Note this method performs no validation on input content.
	 *
	 * @param s hexadecimal string
	 * @return decoded array
	 */
	public static byte[] decode(String s) {
		int len = s.length();
		byte[] b = new byte[len / 2];

		for (int i = 0; i < len; i += 2) {
			b[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) | Character.digit(s.charAt(i + 1), 16));
		}
		return b;
	}

	/**
	 * Encodes a byte array to a hexadecimal string.
	 *
	 * @param b byte array
	 * @return hexadecimal string
	 */
	public static String toHexString(byte[] b) {
		char[] c = new char[b.length * 2];

		for (int i = 0; i < b.length; i++) {
			int v = b[i] & 0xFF;

			c[i * 2] = HEX[v >>> 4];
			c[i * 2 + 1] = HEX[v & 0x0F];
		}

		return new String(c);
	}
}
