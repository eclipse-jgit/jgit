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

import org.eclipse.jgit.lib.MutableObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.util.NB;
import org.eclipse.jgit.util.SystemReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pure Java implementation of SHA-1 from FIPS 180-1 / RFC 3174.
 *
 * <p>
 * See <a href="https://tools.ietf.org/html/rfc3174">RFC 3174</a>.
 * <p>
 * Unlike MessageDigest, this implementation includes the algorithm used by
 * {@code sha1dc} to detect cryptanalytic collision attacks against SHA-1, such
 * as the one used by <a href="https://shattered.it/">SHAttered</a>. See
 * <a href="https://github.com/cr-marcstevens/sha1collisiondetection">
 * sha1collisiondetection</a> for more information.
 *
 * @since 4.7
 */
public class SHA1 {
	private static Logger LOG = LoggerFactory.getLogger(SHA1.class);
	private static final boolean DETECT_COLLISIONS;
	private static final boolean SAFE_HASH;

	static {
		SystemReader sr = SystemReader.getInstance();
		String v = sr.getProperty("org.eclipse.jgit.util.sha1.detectCollision"); //$NON-NLS-1$
		DETECT_COLLISIONS = v != null ? Boolean.parseBoolean(v) : true;

		v = sr.getProperty("org.eclipse.jgit.util.sha1.safeHash"); //$NON-NLS-1$
		SAFE_HASH = v != null ? Boolean.parseBoolean(v) : true;
	}

	/** @return a new context to compute a SHA-1 hash of data. */
	public static SHA1 newInstance() {
		return new SHA1();
	}

	private final int[] h = new int[] {
			// Magic initialization constants defined by FIPS180.
			0x67452301, 0xEFCDAB89, 0x98BADCFE, 0x10325476, 0xC3D2E1F0 };

	private final int[] w = new int[80];

	/** Buffer to accumulate partial blocks to 64 byte alignment. */
	private final byte[] buffer = new byte[64];

	/** Total number of bytes in the message. */
	private long length;

	private boolean detectCollision = DETECT_COLLISIONS;
	private boolean safeHash = SAFE_HASH;
	private boolean foundCollision;

	private final int[] w2 = new int[80];
	private final int[] state58 = new int[5];
	private final int[] state65 = new int[5];
	private final int[] hIn = new int[5];
	private final int[] hTmp = new int[5];

	private SHA1() {
	}

	/**
	 * Enable likely collision detection.
	 *
	 * @param detect
	 * @return {@code this}
	 */
	public SHA1 setDetectCollision(boolean detect) {
		detectCollision = detect;
		return this;
	}

	/**
	 * Enable generation of hardened SHA-1 if a collision is likely.
	 *
	 * @param safe
	 * @return {@code this}
	 */
	public SHA1 setSafeHash(boolean safe) {
		safeHash = safe;
		return this;
	}

	/**
	 * Update the digest computation by adding a byte.
	 *
	 * @param b
	 */
	public void update(byte b) {
		int bufferLen = (int) (length & 63);
		length++;
		buffer[bufferLen] = b;
		if (bufferLen == 63) {
			compress(buffer, 0);
		}
	}

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
		initBlock(block, p);
		int ubcDvMask = detectCollision ? UbcCheck.check(w) : 0;
		compress();

		if (detectCollision) {
			for (UbcCheck.DvInfo dv : UbcCheck.DV) {
				if (((1 << dv.maskb) & ubcDvMask) != 0) {
					for (int i = 0; i < 80; i++) {
						w2[i] = w[i] ^ dv.dm[i];
					}
					recompress(dv.testt, hTmp);
					if (eq(hTmp, h)) {
						foundCollision = true;
						if (safeHash) {
							compress();
							compress();
						}
						break;
					}
				}
			}
		}
	}

	private void initBlock(byte[] block, int p) {
		for (int t = 0; t < 16; t++) {
			w[t] = NB.decodeInt32(block, p + (t << 2));
		}

		// RFC 3174 6.1.b, extend state vector to 80 words.
		for (int t = 16; t < 80; t++) {
			int x = w[t - 3] ^ w[t - 8] ^ w[t - 14] ^ w[t - 16];
			w[t] = (x << 1) | (x >>> 31); // S^1(...)
		}
	}

	private void compress() {
		// Method 1 from RFC 3174 section 6.1.
		// Method 2 (circular queue of 16 words) is slower.
		int a = h[0], b = h[1], c = h[2], d = h[3], e = h[4];

		 e += s1(a, b, c, d, 0);  b = s_30( b);
		 d += s1(e, a, b, c, 1);  a = s_30( a);
		 c += s1(d, e, a, b, 2);  e = s_30( e);
		 b += s1(c, d, e, a, 3);  d = s_30( d);
		 a += s1(b, c, d, e, 4);  c = s_30( c);
		 e += s1(a, b, c, d, 5);  b = s_30( b);
		 d += s1(e, a, b, c, 6);  a = s_30( a);
		 c += s1(d, e, a, b, 7);  e = s_30( e);
		 b += s1(c, d, e, a, 8);  d = s_30( d);
		 a += s1(b, c, d, e, 9);  c = s_30( c);
		 e += s1(a, b, c, d, 10);  b = s_30( b);
		 d += s1(e, a, b, c, 11);  a = s_30( a);
		 c += s1(d, e, a, b, 12);  e = s_30( e);
		 b += s1(c, d, e, a, 13);  d = s_30( d);
		 a += s1(b, c, d, e, 14);  c = s_30( c);
		 e += s1(a, b, c, d, 15);  b = s_30( b);
		 d += s1(e, a, b, c, 16);  a = s_30( a);
		 c += s1(d, e, a, b, 17);  e = s_30( e);
		 b += s1(c, d, e, a, 18);  d = s_30( d);
		 a += s1(b, c, d, e, 19);  c = s_30( c);

		 e += s2(a, b, c, d, 20);  b = s_30( b);
		 d += s2(e, a, b, c, 21);  a = s_30( a);
		 c += s2(d, e, a, b, 22);  e = s_30( e);
		 b += s2(c, d, e, a, 23);  d = s_30( d);
		 a += s2(b, c, d, e, 24);  c = s_30( c);
		 e += s2(a, b, c, d, 25);  b = s_30( b);
		 d += s2(e, a, b, c, 26);  a = s_30( a);
		 c += s2(d, e, a, b, 27);  e = s_30( e);
		 b += s2(c, d, e, a, 28);  d = s_30( d);
		 a += s2(b, c, d, e, 29);  c = s_30( c);
		 e += s2(a, b, c, d, 30);  b = s_30( b);
		 d += s2(e, a, b, c, 31);  a = s_30( a);
		 c += s2(d, e, a, b, 32);  e = s_30( e);
		 b += s2(c, d, e, a, 33);  d = s_30( d);
		 a += s2(b, c, d, e, 34);  c = s_30( c);
		 e += s2(a, b, c, d, 35);  b = s_30( b);
		 d += s2(e, a, b, c, 36);  a = s_30( a);
		 c += s2(d, e, a, b, 37);  e = s_30( e);
		 b += s2(c, d, e, a, 38);  d = s_30( d);
		 a += s2(b, c, d, e, 39);  c = s_30( c);

		 e += s3(a, b, c, d, 40);  b = s_30( b);
		 d += s3(e, a, b, c, 41);  a = s_30( a);
		 c += s3(d, e, a, b, 42);  e = s_30( e);
		 b += s3(c, d, e, a, 43);  d = s_30( d);
		 a += s3(b, c, d, e, 44);  c = s_30( c);
		 e += s3(a, b, c, d, 45);  b = s_30( b);
		 d += s3(e, a, b, c, 46);  a = s_30( a);
		 c += s3(d, e, a, b, 47);  e = s_30( e);
		 b += s3(c, d, e, a, 48);  d = s_30( d);
		 a += s3(b, c, d, e, 49);  c = s_30( c);
		 e += s3(a, b, c, d, 50);  b = s_30( b);
		 d += s3(e, a, b, c, 51);  a = s_30( a);
		 c += s3(d, e, a, b, 52);  e = s_30( e);
		 b += s3(c, d, e, a, 53);  d = s_30( d);
		 a += s3(b, c, d, e, 54);  c = s_30( c);
		 e += s3(a, b, c, d, 55);  b = s_30( b);
		 d += s3(e, a, b, c, 56);  a = s_30( a);
		 c += s3(d, e, a, b, 57);  e = s_30( e);
		save(state58, a, b, c, d, e);
		 b += s3(c, d, e, a, 58);  d = s_30( d);
		 a += s3(b, c, d, e, 59);  c = s_30( c);

		 e += s4(a, b, c, d, 60);  b = s_30( b);
		 d += s4(e, a, b, c, 61);  a = s_30( a);
		 c += s4(d, e, a, b, 62);  e = s_30( e);
		 b += s4(c, d, e, a, 63);  d = s_30( d);
		 a += s4(b, c, d, e, 64);  c = s_30( c);
		save(state65, a, b, c, d, e);
		 e += s4(a, b, c, d, 65);  b = s_30( b);
		 d += s4(e, a, b, c, 66);  a = s_30( a);
		 c += s4(d, e, a, b, 67);  e = s_30( e);
		 b += s4(c, d, e, a, 68);  d = s_30( d);
		 a += s4(b, c, d, e, 69);  c = s_30( c);
		 e += s4(a, b, c, d, 70);  b = s_30( b);
		 d += s4(e, a, b, c, 71);  a = s_30( a);
		 c += s4(d, e, a, b, 72);  e = s_30( e);
		 b += s4(c, d, e, a, 73);  d = s_30( d);
		 a += s4(b, c, d, e, 74);  c = s_30( c);
		 e += s4(a, b, c, d, 75);  b = s_30( b);
		 d += s4(e, a, b, c, 76);  a = s_30( a);
		 c += s4(d, e, a, b, 77);  e = s_30( e);
		 b += s4(c, d, e, a, 78);  d = s_30( d);
		 a += s4(b, c, d, e, 79);  c = s_30( c);

		save(h, h[0] + a, h[1] + b, h[2] + c, h[3] + d, h[4] + e);
	}

	@SuppressWarnings("fallthrough")
	private void recompress(int t, int[] hOut) {
		int[] s;
		if (t == 58) {
			s = state58;
		} else if (t == 65) {
			s = state65;
		} else {
			throw new IllegalStateException();
		}
		int a = s[0], b = s[1], c = s[2], d = s[3], e = s[4];

		switch (t - 1) {
		case 64: { c = r_30( c);  a -= r4(b, c, d, e, 64);}
		case 63: { d = r_30( d);  b -= r4(c, d, e, a, 63);}
		case 62: { e = r_30( e);  c -= r4(d, e, a, b, 62);}
		case 61: { a = r_30( a);  d -= r4(e, a, b, c, 61);}
		case 60: { b = r_30( b);  e -= r4(a, b, c, d, 60);}

		case 59: { c = r_30( c);  a -= r3(b, c, d, e, 59);}
		case 58: { d = r_30( d);  b -= r3(c, d, e, a, 58);}
		case 57: { e = r_30( e);  c -= r3(d, e, a, b, 57);}
		case 56: { a = r_30( a);  d -= r3(e, a, b, c, 56);}
		case 55: { b = r_30( b);  e -= r3(a, b, c, d, 55);}
		case 54: { c = r_30( c);  a -= r3(b, c, d, e, 54);}
		case 53: { d = r_30( d);  b -= r3(c, d, e, a, 53);}
		case 52: { e = r_30( e);  c -= r3(d, e, a, b, 52);}
		case 51: { a = r_30( a);  d -= r3(e, a, b, c, 51);}
		case 50: { b = r_30( b);  e -= r3(a, b, c, d, 50);}
		case 49: { c = r_30( c);  a -= r3(b, c, d, e, 49);}
		case 48: { d = r_30( d);  b -= r3(c, d, e, a, 48);}
		case 47: { e = r_30( e);  c -= r3(d, e, a, b, 47);}
		case 46: { a = r_30( a);  d -= r3(e, a, b, c, 46);}
		case 45: { b = r_30( b);  e -= r3(a, b, c, d, 45);}
		case 44: { c = r_30( c);  a -= r3(b, c, d, e, 44);}
		case 43: { d = r_30( d);  b -= r3(c, d, e, a, 43);}
		case 42: { e = r_30( e);  c -= r3(d, e, a, b, 42);}
		case 41: { a = r_30( a);  d -= r3(e, a, b, c, 41);}
		case 40: { b = r_30( b);  e -= r3(a, b, c, d, 40);}

		case 39: { c = r_30( c);  a -= r2(b, c, d, e, 39);}
		case 38: { d = r_30( d);  b -= r2(c, d, e, a, 38);}
		case 37: { e = r_30( e);  c -= r2(d, e, a, b, 37);}
		case 36: { a = r_30( a);  d -= r2(e, a, b, c, 36);}
		case 35: { b = r_30( b);  e -= r2(a, b, c, d, 35);}
		case 34: { c = r_30( c);  a -= r2(b, c, d, e, 34);}
		case 33: { d = r_30( d);  b -= r2(c, d, e, a, 33);}
		case 32: { e = r_30( e);  c -= r2(d, e, a, b, 32);}
		case 31: { a = r_30( a);  d -= r2(e, a, b, c, 31);}
		case 30: { b = r_30( b);  e -= r2(a, b, c, d, 30);}
		case 29: { c = r_30( c);  a -= r2(b, c, d, e, 29);}
		case 28: { d = r_30( d);  b -= r2(c, d, e, a, 28);}
		case 27: { e = r_30( e);  c -= r2(d, e, a, b, 27);}
		case 26: { a = r_30( a);  d -= r2(e, a, b, c, 26);}
		case 25: { b = r_30( b);  e -= r2(a, b, c, d, 25);}
		case 24: { c = r_30( c);  a -= r2(b, c, d, e, 24);}
		case 23: { d = r_30( d);  b -= r2(c, d, e, a, 23);}
		case 22: { e = r_30( e);  c -= r2(d, e, a, b, 22);}
		case 21: { a = r_30( a);  d -= r2(e, a, b, c, 21);}
		case 20: { b = r_30( b);  e -= r2(a, b, c, d, 20);}

		case 19: { c = r_30( c);  a -= r1(b, c, d, e, 19);}
		case 18: { d = r_30( d);  b -= r1(c, d, e, a, 18);}
		case 17: { e = r_30( e);  c -= r1(d, e, a, b, 17);}
		case 16: { a = r_30( a);  d -= r1(e, a, b, c, 16);}
		case 15: { b = r_30( b);  e -= r1(a, b, c, d, 15);}
		case 14: { c = r_30( c);  a -= r1(b, c, d, e, 14);}
		case 13: { d = r_30( d);  b -= r1(c, d, e, a, 13);}
		case 12: { e = r_30( e);  c -= r1(d, e, a, b, 12);}
		case 11: { a = r_30( a);  d -= r1(e, a, b, c, 11);}
		case 10: { b = r_30( b);  e -= r1(a, b, c, d, 10);}
		case 9: { c = r_30( c);  a -= r1(b, c, d, e, 9);}
		case 8: { d = r_30( d);  b -= r1(c, d, e, a, 8);}
		case 7: { e = r_30( e);  c -= r1(d, e, a, b, 7);}
		case 6: { a = r_30( a);  d -= r1(e, a, b, c, 6);}
		case 5: { b = r_30( b);  e -= r1(a, b, c, d, 5);}
		case 4: { c = r_30( c);  a -= r1(b, c, d, e, 4);}
		case 3: { d = r_30( d);  b -= r1(c, d, e, a, 3);}
		case 2: { e = r_30( e);  c -= r1(d, e, a, b, 2);}
		case 1: { a = r_30( a);  d -= r1(e, a, b, c, 1);}
		case 0: { b = r_30( b);  e -= r1(a, b, c, d, 0);}
		}

		save(hIn, a, b, c, d, e);
		a = s[0]; b = s[1]; c = s[2]; d = s[3]; e = s[4];

		if (t <= 58) { b += sr3(c, d, e, a, 58);  d = s_30( d);}
		if (t <= 59) { a += sr3(b, c, d, e, 59);  c = s_30( c);}

		if (t <= 60) { e += sr4(a, b, c, d, 60);  b = s_30( b);}
		if (t <= 61) { d += sr4(e, a, b, c, 61);  a = s_30( a);}
		if (t <= 62) { c += sr4(d, e, a, b, 62);  e = s_30( e);}
		if (t <= 63) { b += sr4(c, d, e, a, 63);  d = s_30( d);}
		if (t <= 64) { a += sr4(b, c, d, e, 64);  c = s_30( c);}
		if (t <= 65) { e += sr4(a, b, c, d, 65);  b = s_30( b);}
		if (t <= 66) { d += sr4(e, a, b, c, 66);  a = s_30( a);}
		if (t <= 67) { c += sr4(d, e, a, b, 67);  e = s_30( e);}
		if (t <= 68) { b += sr4(c, d, e, a, 68);  d = s_30( d);}
		if (t <= 69) { a += sr4(b, c, d, e, 69);  c = s_30( c);}
		if (t <= 70) { e += sr4(a, b, c, d, 70);  b = s_30( b);}
		if (t <= 71) { d += sr4(e, a, b, c, 71);  a = s_30( a);}
		if (t <= 72) { c += sr4(d, e, a, b, 72);  e = s_30( e);}
		if (t <= 73) { b += sr4(c, d, e, a, 73);  d = s_30( d);}
		if (t <= 74) { a += sr4(b, c, d, e, 74);  c = s_30( c);}
		if (t <= 75) { e += sr4(a, b, c, d, 75);  b = s_30( b);}
		if (t <= 76) { d += sr4(e, a, b, c, 76);  a = s_30( a);}
		if (t <= 77) { c += sr4(d, e, a, b, 77);  e = s_30( e);}
		if (t <= 78) { b += sr4(c, d, e, a, 78);  d = s_30( d);}
		if (t <= 79) { a += sr4(b, c, d, e, 79);  c = s_30( c);}

		save(hOut, hIn[0] + a, hIn[1] + b, hIn[2] + c, hIn[3] + d, hIn[4] + e);
	}

	private int s1(int a, int b, int c, int d, int t) {
		return ((a << 5) | (a >>> 27)) // S^5(A)
				+ ((b & c) | ((~b) & d)) + 0x5A827999 + w[t];
	}

	private int s2(int a, int b, int c, int d, int t) {
		return ((a << 5) | (a >>> 27)) // S^5(A)
				+ (b ^ c ^ d) + 0x6ED9EBA1 + w[t];
	}

	private int s3(int a, int b, int c, int d, int t) {
		return ((a << 5) | (a >>> 27)) // S^5(A)
				+ ((b & c) | (b & d) | (c & d)) + 0x8F1BBCDC + w[t];
	}

	private int sr3(int a, int b, int c, int d, int t) {
		return ((a << 5) | (a >>> 27)) // S^5(A)
				+ ((b & c) | (b & d) | (c & d)) + 0x8F1BBCDC + w2[t];
	}

	private int s4(int a, int b, int c, int d, int t) {
		return ((a << 5) | (a >>> 27)) // S^5(A)
				+ (b ^ c ^ d) + 0xCA62C1D6 + w[t];
	}

	private int sr4(int a, int b, int c, int d, int t) {
		return ((a << 5) | (a >>> 27)) // S^5(A)
				+ (b ^ c ^ d) + 0xCA62C1D6 + w2[t];
	}

	// circular left shift operation S^30(X)
	private static int s_30(int b) {
		return (b << 30) | (b >>> 2);
	}

	// circular right shift operation, inverse of s_30
	private static int r_30(int x) {
		return (x >>> 30) | (x << 2);
	}

	private int r1(int a, int b, int c, int d, int t) {
		return ((a << 5) | (a >>> 27)) // S^5(A)
				+ ((b & c) | ((~b) & d)) + 0x5A827999 + w2[t];
	}

	private int r2(int a, int b, int c, int d, int t) {
		return ((a << 5) | (a >>> 27)) // S^5(A)
				+ (b ^ c ^ d) + 0x6ED9EBA1 + w2[t];
	}

	private int r3(int a, int b, int c, int d, int t) {
		return ((a << 5) | (a >>> 27)) // S^5(A)
				+ ((b & c) | (b & d) | (c & d)) + 0x8F1BBCDC + w2[t];
	}

	private int r4(int a, int b, int c, int d, int t) {
		return ((a << 5) | (a >>> 27)) // S^5(A)
				+ (b ^ c ^ d) + 0xCA62C1D6 + w2[t];
	}

	private static void save(int[] h, int a, int b, int c, int d, int e) {
		h[0] = a;
		h[1] = b;
		h[2] = c;
		h[3] = d;
		h[4] = e;
	}

	private static boolean eq(int[] a, int[] b) {
		return a[0] == b[0]
				&& a[1] == b[1]
				&& a[2] == b[2]
				&& a[3] == b[3]
				&& a[4] == b[4];
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

		if (foundCollision) {
			String name = ObjectId.fromRaw(h).name();
			LOG.warn("possible SHA-1 collision; safeHash" //$NON-NLS-1$
					+ (safeHash ? "" : " disabled,") //$NON-NLS-1$ //$NON-NLS-2$
					+ ' ' + name);
		}
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
		NB.encodeInt32(b, 0, h[0]);
		NB.encodeInt32(b, 4, h[1]);
		NB.encodeInt32(b, 8, h[2]);
		NB.encodeInt32(b, 12, h[3]);
		NB.encodeInt32(b, 16, h[4]);
		return b;
	}

	/**
	 * Finish the digest and return the resulting hash.
	 * <p>
	 * Once {@code digest()} is called, this instance should be discarded.
	 *
	 * @return the ObjectId for the resulting hash.
	 */
	public ObjectId toObjectId() {
		finish();
		return ObjectId.fromRaw(h);
	}

	/**
	 * Finish the digest and return the resulting hash.
	 * <p>
	 * Once {@code digest()} is called, this instance should be discarded.
	 *
	 * @param id
	 *            destination to copy the digest to.
	 */
	public void digest(MutableObjectId id) {
		finish();
		id.fromRaw(h);
	}
}
