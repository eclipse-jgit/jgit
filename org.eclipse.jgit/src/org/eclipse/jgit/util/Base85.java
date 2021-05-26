/*
 * Copyright (C) 2021 Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.util;

import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Arrays;

import org.eclipse.jgit.internal.JGitText;

/**
 * Base-85 encoder/decoder.
 *
 * @since 5.12
 */
public final class Base85 {

	private static final byte[] ENCODE = ("0123456789" //$NON-NLS-1$
			+ "ABCDEFGHIJKLMNOPQRSTUVWXYZ" //$NON-NLS-1$
			+ "abcdefghijklmnopqrstuvwxyz" //$NON-NLS-1$
			+ "!#$%&()*+-;<=>?@^_`{|}~") //$NON-NLS-1$
					.getBytes(StandardCharsets.US_ASCII);

	private static final int[] DECODE = new int[256];

	static {
		Arrays.fill(DECODE, -1);
		for (int i = 0; i < ENCODE.length; i++) {
			DECODE[ENCODE[i]] = i;
		}
	}

	private Base85() {
		// No instantiation
	}

	/**
	 * Determines the length of the base-85 encoding for {@code rawLength}
	 * bytes.
	 *
	 * @param rawLength
	 *            number of bytes to encode
	 * @return number of bytes needed for the base-85 encoding of
	 *         {@code rawLength} bytes
	 */
	public static int encodedLength(int rawLength) {
		return (rawLength + 3) / 4 * 5;
	}

	/**
	 * Encodes the given {@code data} in Base-85.
	 *
	 * @param data
	 *            to encode
	 * @return encoded data
	 */
	public static byte[] encode(byte[] data) {
		return encode(data, 0, data.length);
	}

	/**
	 * Encodes {@code length} bytes of {@code data} in Base-85, beginning at the
	 * {@code start} index.
	 *
	 * @param data
	 *            to encode
	 * @param start
	 *            index of the first byte to encode
	 * @param length
	 *            number of bytes to encode
	 * @return encoded data
	 */
	public static byte[] encode(byte[] data, int start, int length) {
		byte[] result = new byte[encodedLength(length)];
		int end = start + length;
		int in = start;
		int out = 0;
		while (in < end) {
			// Accumulate remaining bytes MSB first as a 32bit value
			long accumulator = ((long) (data[in++] & 0xFF)) << 24;
			if (in < end) {
				accumulator |= (data[in++] & 0xFF) << 16;
				if (in < end) {
					accumulator |= (data[in++] & 0xFF) << 8;
					if (in < end) {
						accumulator |= (data[in++] & 0xFF);
					}
				}
			}
			// Write the 32bit value in base-85 encoding, also MSB first
			for (int i = 4; i >= 0; i--) {
				result[out + i] = ENCODE[(int) (accumulator % 85)];
				accumulator /= 85;
			}
			out += 5;
		}
		return result;
	}

	/**
	 * Decodes the Base-85 {@code encoded} data into a byte array of
	 * {@code expectedSize} bytes.
	 *
	 * @param encoded
	 *            Base-85 encoded data
	 * @param expectedSize
	 *            of the result
	 * @return the decoded bytes
	 * @throws IllegalArgumentException
	 *             if expectedSize doesn't match, the encoded data has a length
	 *             that is not a multiple of 5, or there are invalid characters
	 *             in the encoded data
	 */
	public static byte[] decode(byte[] encoded, int expectedSize) {
		return decode(encoded, 0, encoded.length, expectedSize);
	}

	/**
	 * Decodes {@code length} bytes of Base-85 {@code encoded} data, beginning
	 * at the {@code start} index, into a byte array of {@code expectedSize}
	 * bytes.
	 *
	 * @param encoded
	 *            Base-85 encoded data
	 * @param start
	 *            index at which the data to decode starts in {@code encoded}
	 * @param length
	 *            of the Base-85 encoded data
	 * @param expectedSize
	 *            of the result
	 * @return the decoded bytes
	 * @throws IllegalArgumentException
	 *             if expectedSize doesn't match, {@code length} is not a
	 *             multiple of 5, or there are invalid characters in the encoded
	 *             data
	 */
	public static byte[] decode(byte[] encoded, int start, int length,
			int expectedSize) {
		if (length % 5 != 0) {
			throw new IllegalArgumentException(JGitText.get().base85length);
		}
		byte[] result = new byte[expectedSize];
		int end = start + length;
		int in = start;
		int out = 0;
		while (in < end && out < expectedSize) {
			// Accumulate 5 bytes, "MSB" first
			long accumulator = 0;
			for (int i = 4; i >= 0; i--) {
				int val = DECODE[encoded[in++] & 0xFF];
				if (val < 0) {
					throw new IllegalArgumentException(MessageFormat.format(
							JGitText.get().base85invalidChar,
							Integer.toHexString(encoded[in - 1] & 0xFF)));
				}
				accumulator = accumulator * 85 + val;
			}
			if (accumulator > 0xFFFF_FFFFL) {
				throw new IllegalArgumentException(
						MessageFormat.format(JGitText.get().base85overflow,
								Long.toHexString(accumulator)));
			}
			// Write remaining bytes, MSB first
			result[out++] = (byte) (accumulator >>> 24);
			if (out < expectedSize) {
				result[out++] = (byte) (accumulator >>> 16);
				if (out < expectedSize) {
					result[out++] = (byte) (accumulator >>> 8);
					if (out < expectedSize) {
						result[out++] = (byte) accumulator;
					}
				}
			}
		}
		// Should have exhausted 'in' and filled 'out' completely
		if (in < end) {
			throw new IllegalArgumentException(
					MessageFormat.format(JGitText.get().base85tooLong,
							Integer.valueOf(expectedSize)));
		}
		if (out < expectedSize) {
			throw new IllegalArgumentException(
					MessageFormat.format(JGitText.get().base85tooShort,
							Integer.valueOf(expectedSize)));
		}
		return result;
	}
}
