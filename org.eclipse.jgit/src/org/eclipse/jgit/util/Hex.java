/*
 * Copyright (C) 2020, Michael Dardis. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.util;

/**
 * Encodes and decodes to and from hexadecimal notation.
 *
 * @since 5.7
 */
public final class Hex {

	private static final char[] HEX = "0123456789abcdef".toCharArray(); //$NON-NLS-1$

	/** Defeats instantiation. */
	private Hex() {
		// empty
	}

	/**
	 * Decode a hexadecimal string to a byte array.
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
	 * Encode a byte array to a hexadecimal string.
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
