/*
 * Copyright (C) 2017, Google Inc.
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

package org.eclipse.jgit.util.sha1;

import java.util.Arrays;

import org.eclipse.jgit.util.NB;

/**
 * Pure Java implementation of SHA-1 from FIPS 180-1 / RFC 3174.
 *
 * <p>
 * See <a href="https://tools.ietf.org/html/rfc3174">RFC 3174</a>.
 *
 * @since 4.7
 */
public class SHA1 {
	// Magic initialization constants defined by FIPS180.
	private int h0 = 0x67452301;
	private int h1 = 0xEFCDAB89;
	private int h2 = 0x98BADCFE;
	private int h3 = 0x10325476;
	private int h4 = 0xC3D2E1F0;
	private final int[] w = new int[80];

	/** Buffer to accumulate partial blocks to 64 byte alignment. */
	private final byte[] buffer = new byte[64];

	/** Total number of bytes in the message. */
	private long length;

	/**
	 * Update the digest computation by adding bytes to the message.
	 *
	 * @param in
	 *            input array of bytes.
	 */
	public void update(byte[] in) {
		update(in, 0, in.length);
	}

	/**
	 * Update the digest computation by adding bytes to the message.
	 *
	 * @param in
	 *            input array of bytes.
	 * @param p
	 *            offset to start at from {@code in}.
	 * @param len
	 *            number of bytes to hash.
	 */
	public void update(byte[] in, int p, int len) {
		// SHA-1 compress can only process whole 64 byte blocks.
		// Hold partial updates in buffer, whose length is the low bits.
		int bufferLen = (int) (length & 63);
		length += len;

		if (bufferLen > 0) {
			int n = Math.min(64 - bufferLen, len);
			System.arraycopy(in, p, buffer, bufferLen, n);
			p += n;
			len -= n;
			if (bufferLen + n < 64) {
				return;
			}
			compress(buffer, 0);
		}
		while (len >= 64) {
			compress(in, p);
			p += 64;
			len -= 64;
		}
		if (len > 0) {
			System.arraycopy(in, p, buffer, 0, len);
		}
	}

	private void compress(byte[] block, int p) {
		// Method 1 from RFC 3174 section 6.1.
		// Method 2 (circular queue of 16 words) is slower.
		int a = h0, b = h1, c = h2, d = h3, e = h4;

		// Round 1: 0 <= t <= 15 comes from the input block.
		for (int t = 0; t < 16; t++) {
			int temp = NB.decodeInt32(block, p + (t << 2));
			w[t] = temp;
			temp += ((a << 5) | (a >>> 27)) // S^5(A)
					+ (((c ^ d) & b) ^ d) // f: 0 <= t <= 19
					+ e + 0x5A827999;
			e = d;
			d = c;
			c = (b << 30) | (b >>> 2); // S^30(B)
			b = a;
			a = temp;
		}

		// RFC 3174 6.1.b, extend state vector to 80 words.
		for (int t = 16; t < 80; t++) {
			int x = w[t - 3] ^ w[t - 8] ^ w[t - 14] ^ w[t - 16];
			w[t] = (x << 1) | (x >>> 31); // S^1(...)
		}

		// Round 1: tail
		for (int t = 16; t < 20; t++) {
			int temp = ((a << 5) | (a >>> 27)) // S^5(A)
					+ (((c ^ d) & b) ^ d) // f: 0 <= t <= 19
					+ e + w[t] + 0x5A827999;
			e = d;
			d = c;
			c = (b << 30) | (b >>> 2); // S^30(B)
			b = a;
			a = temp;
		}

		// Round 2
		for (int t = 20; t < 40; t++) {
			int temp = ((a << 5) | (a >>> 27)) // S^5(A)
					+ (b ^ c ^ d) // f: 20 <= t <= 39
					+ e + w[t] + 0x6ED9EBA1;
			e = d;
			d = c;
			c = (b << 30) | (b >>> 2); // S^30(B)
			b = a;
			a = temp;
		}

		// Round 3
		for (int t = 40; t < 60; t++) {
			int temp = ((a << 5) | (a >>> 27)) // S^5(A)
					+ ((b & c) | (d & (b | c))) // f: 40 <= t <= 59
					+ e + w[t] + 0x8F1BBCDC;
			e = d;
			d = c;
			c = (b << 30) | (b >>> 2); // S^30(B)
			b = a;
			a = temp;
		}

		// Round 4
		for (int t = 60; t < 80; t++) {
			int temp = ((a << 5) | (a >>> 27)) // S^5(A)
					+ (b ^ c ^ d) // f: 60 <= t <= 79
					+ e + w[t] + 0xCA62C1D6;
			e = d;
			d = c;
			c = (b << 30) | (b >>> 2); // S^30(B)
			b = a;
			a = temp;
		}

		h0 += a;
		h1 += b;
		h2 += c;
		h3 += d;
		h4 += e;
	}

	private void finish() {
		int bufferLen = (int) (length & 63);
		if (bufferLen > 55) {
			// Last block is too small; pad, compress, pad another block.
			buffer[bufferLen++] = (byte) 0x80;
			Arrays.fill(buffer, bufferLen, 64, (byte) 0);
			compress(buffer, 0);
			Arrays.fill(buffer, 0, 56, (byte) 0);
		} else {
			// Last block can hold padding and length.
			buffer[bufferLen++] = (byte) 0x80;
			Arrays.fill(buffer, bufferLen, 56, (byte) 0);
		}
		NB.encodeInt32(buffer, 56, (int) (length >>> (32 - 3)));
		NB.encodeInt32(buffer, 60, (int) (length << 3));
		compress(buffer, 0);
	}

	/**
	 * Finish the digest and return the resulting hash.
	 * <p>
	 * Once {@code digest()} is called, this instance should be discarded.
	 *
	 * @return the bytes for the resulting hash.
	 */
	public byte[] digest() {
		finish();

		byte[] b = new byte[20];
		NB.encodeInt32(b, 0, h0);
		NB.encodeInt32(b, 4, h1);
		NB.encodeInt32(b, 8, h2);
		NB.encodeInt32(b, 12, h3);
		NB.encodeInt32(b, 16, h4);
		return b;
	}
}
