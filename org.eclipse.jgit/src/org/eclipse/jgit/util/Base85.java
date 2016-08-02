/*
 * Copyright (C) 2016, Nadav Cohen <nadavcoh@gmail.com>
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
 * Utility class for decoding base85 strings
 */
public class Base85 {

	static char enc85[] = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', // 00-09
			'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', // 10-19
			'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', // 20-29
			'U', 'V', 'W', 'X', 'Y', 'Z', 'a', 'b', 'c', 'd', // 30-39
			'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', // 40-49
			'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', // 50-59
			'y', 'z', '!', '#', '$', '%', '&', '(', ')', '*', // 60-69
			'+', '-', ';', '<', '=', '>', '?', '@', '^', '_', // 70-79
			'`', '{', '|', '}', '~' // 80-84
	};

	static int dec85[] = new int[256];

	static {
		// initialize decode table
		for (int i = 0; i < dec85.length; i++) {
			dec85[i] = -1;
		}
		// and populate with valid decode keys
		for (int i = 0; i < enc85.length; i++) {
			char c = enc85[i];
			dec85[c] = i;
		}
	}

	/**
	 * Encodes a binary array into its base85 notation. Resulting length is
	 * expected to be in multiples of 5
	 *
	 * @param buffer
	 *            source array
	 * @param start
	 *            index to encode from
	 * @param length
	 *            number of bytes to encode
	 * @return encoded byte array
	 */
	public static byte[] encode85(byte[] buffer, int start, int length) {
		int srcPtr = start;
		int tgtPtr = 0;

		byte[] result = new byte[(int) Math.ceil(length / 4D) * 5];
		while (length > 0) {
			long acc = 0;
			for (int bitsToShift = 24; bitsToShift >= 0; bitsToShift -= 8) {
				byte ch = buffer[srcPtr++];
				acc |= ((ch & 0xff) << bitsToShift);
				if (--length == 0)
					break;
			}

			acc = acc & 0xFFFFFFFFL;
			for (int i = 4; i >= 0; i--) {
				int val = (int) (acc % 85);
				acc /= 85;
				result[tgtPtr + i] = (byte) enc85[val];
			}
			tgtPtr += 5;
		}

		return result;
	}

	/**
	 * Decodes a subset of the byte array into Base85 notation. Length is
	 * expected to be a multiple of 5.
	 *
	 * @param buffer
	 *            the data to decode
	 * @param start
	 *            start index to decode from
	 * @param length
	 *            number of bytes to decode
	 * @param expectedDecodedSize expected size of decoded array
	 * @return decoded base85 representation of buffer
	 */
	public static byte[] decode85(byte[] buffer, int start, int length,
			int expectedDecodedSize) {
		if (length % 5 != 0) {
			throw new IllegalArgumentException(
					"Length must be in multiples of 5"); //$NON-NLS-1$
		}

		byte[] result = new byte[expectedDecodedSize];
		int resultPtr = 0;

		int pos = start;
		while (resultPtr < expectedDecodedSize) {
			long acc = 0;

			// convert 5 bytes to 32 bit representation
			for (int i = 0; i < 5; i++) {
				int c = buffer[pos++];
				int decoded = dec85[c];
				if (decoded == -1) {
					throw new IllegalArgumentException(
							"Invalid base85 character detected"); //$NON-NLS-1$
				}
				acc = acc * 85 + decoded;
			}

			// output 4 bytes from most significant to least significant
			for (int i = 24; i >= 0
					&& resultPtr < expectedDecodedSize; i -= 8) {
				result[resultPtr++] = (byte) ((acc >>> i) & 0xFF);
			}
		}

		return result;
	}

}
