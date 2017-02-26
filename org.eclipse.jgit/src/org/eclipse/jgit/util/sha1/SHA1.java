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

import static java.lang.Integer.lowestOneBit;
import static java.lang.Integer.numberOfTrailingZeros;
import static java.lang.Integer.rotateLeft;
import static java.lang.Integer.rotateRight;

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
 * <p>
 * When detectCollision is true and safeHash is false (the defaults), this
 * implementation throws {@link Sha1CollisionException} from any digest method
 * if a potential collision was detected.
 * <p>
 * When safeHash is true, this implementation will instead return a hardened
 * SHA-1 by running additional rounds on specific message blocks, which produces
 * a different hash result than standard SHA-1 would produce for the same file.
 * This may cause Git objects to be given a different name.
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
		SAFE_HASH = v != null ? Boolean.parseBoolean(v) : false;
	}

	/** @return a new context to compute a SHA-1 hash of data. */
	public static SHA1 newInstance() {
		return new SHA1();
	}

	private final State h = new State();
	private final int[] w = new int[80];

	/** Buffer to accumulate partial blocks to 64 byte alignment. */
	private final byte[] buffer = new byte[64];

	/** Total number of bytes in the message. */
	private long length;

	private boolean detectCollision = DETECT_COLLISIONS;
	private boolean safeHash = SAFE_HASH;
	private boolean foundCollision;

	private final int[] w2 = new int[80];
	private final State state58 = new State();
	private final State state65 = new State();
	private final State hIn = new State();
	private final State hTmp = new State();

	private SHA1() {
		h.init();
	}

	/**
	 * Enable likely collision detection.
	 * <p>
	 * Default is {@code true}.
	 * <p>
	 * May also be set by system property:
	 * {@code -Dorg.eclipse.jgit.util.sha1.detectCollision=true}.
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
	 * <p>
	 * Default is {@code false}.
	 * <p>
	 * May also be set by system property:
	 * {@code -Dorg.eclipse.jgit.util.sha1.safeHash=true}.
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

		while (ubcDvMask != 0) {
			int b = numberOfTrailingZeros(lowestOneBit(ubcDvMask));
			UbcCheck.DvInfo dv = UbcCheck.DV[b];
			for (int i = 0; i < 80; i++) {
				w2[i] = w[i] ^ dv.dm[i];
			}
			recompress(dv.testt);
			if (eq(hTmp, h)) {
				foundCollision = true;
				if (safeHash) {
					compress();
					compress();
				}
				break;
			}
			ubcDvMask &= ~(1 << b);
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
		int a = h.a, b = h.b, c = h.c, d = h.d, e = h.e;

		// @formatter:off
		 e += s1(a, b, c, d, 0);  b = rotateLeft(b, 30);
		 d += s1(e, a, b, c, 1);  a = rotateLeft(a, 30);
		 c += s1(d, e, a, b, 2);  e = rotateLeft(e, 30);
		 b += s1(c, d, e, a, 3);  d = rotateLeft(d, 30);
		 a += s1(b, c, d, e, 4);  c = rotateLeft(c, 30);
		 e += s1(a, b, c, d, 5);  b = rotateLeft(b, 30);
		 d += s1(e, a, b, c, 6);  a = rotateLeft(a, 30);
		 c += s1(d, e, a, b, 7);  e = rotateLeft(e, 30);
		 b += s1(c, d, e, a, 8);  d = rotateLeft(d, 30);
		 a += s1(b, c, d, e, 9);  c = rotateLeft(c, 30);
		 e += s1(a, b, c, d, 10);  b = rotateLeft(b, 30);
		 d += s1(e, a, b, c, 11);  a = rotateLeft(a, 30);
		 c += s1(d, e, a, b, 12);  e = rotateLeft(e, 30);
		 b += s1(c, d, e, a, 13);  d = rotateLeft(d, 30);
		 a += s1(b, c, d, e, 14);  c = rotateLeft(c, 30);
		 e += s1(a, b, c, d, 15);  b = rotateLeft(b, 30);
		 d += s1(e, a, b, c, 16);  a = rotateLeft(a, 30);
		 c += s1(d, e, a, b, 17);  e = rotateLeft(e, 30);
		 b += s1(c, d, e, a, 18);  d = rotateLeft(d, 30);
		 a += s1(b, c, d, e, 19);  c = rotateLeft(c, 30);

		 e += s2(a, b, c, d, 20);  b = rotateLeft(b, 30);
		 d += s2(e, a, b, c, 21);  a = rotateLeft(a, 30);
		 c += s2(d, e, a, b, 22);  e = rotateLeft(e, 30);
		 b += s2(c, d, e, a, 23);  d = rotateLeft(d, 30);
		 a += s2(b, c, d, e, 24);  c = rotateLeft(c, 30);
		 e += s2(a, b, c, d, 25);  b = rotateLeft(b, 30);
		 d += s2(e, a, b, c, 26);  a = rotateLeft(a, 30);
		 c += s2(d, e, a, b, 27);  e = rotateLeft(e, 30);
		 b += s2(c, d, e, a, 28);  d = rotateLeft(d, 30);
		 a += s2(b, c, d, e, 29);  c = rotateLeft(c, 30);
		 e += s2(a, b, c, d, 30);  b = rotateLeft(b, 30);
		 d += s2(e, a, b, c, 31);  a = rotateLeft(a, 30);
		 c += s2(d, e, a, b, 32);  e = rotateLeft(e, 30);
		 b += s2(c, d, e, a, 33);  d = rotateLeft(d, 30);
		 a += s2(b, c, d, e, 34);  c = rotateLeft(c, 30);
		 e += s2(a, b, c, d, 35);  b = rotateLeft(b, 30);
		 d += s2(e, a, b, c, 36);  a = rotateLeft(a, 30);
		 c += s2(d, e, a, b, 37);  e = rotateLeft(e, 30);
		 b += s2(c, d, e, a, 38);  d = rotateLeft(d, 30);
		 a += s2(b, c, d, e, 39);  c = rotateLeft(c, 30);

		 e += s3(a, b, c, d, 40);  b = rotateLeft(b, 30);
		 d += s3(e, a, b, c, 41);  a = rotateLeft(a, 30);
		 c += s3(d, e, a, b, 42);  e = rotateLeft(e, 30);
		 b += s3(c, d, e, a, 43);  d = rotateLeft(d, 30);
		 a += s3(b, c, d, e, 44);  c = rotateLeft(c, 30);
		 e += s3(a, b, c, d, 45);  b = rotateLeft(b, 30);
		 d += s3(e, a, b, c, 46);  a = rotateLeft(a, 30);
		 c += s3(d, e, a, b, 47);  e = rotateLeft(e, 30);
		 b += s3(c, d, e, a, 48);  d = rotateLeft(d, 30);
		 a += s3(b, c, d, e, 49);  c = rotateLeft(c, 30);
		 e += s3(a, b, c, d, 50);  b = rotateLeft(b, 30);
		 d += s3(e, a, b, c, 51);  a = rotateLeft(a, 30);
		 c += s3(d, e, a, b, 52);  e = rotateLeft(e, 30);
		 b += s3(c, d, e, a, 53);  d = rotateLeft(d, 30);
		 a += s3(b, c, d, e, 54);  c = rotateLeft(c, 30);
		 e += s3(a, b, c, d, 55);  b = rotateLeft(b, 30);
		 d += s3(e, a, b, c, 56);  a = rotateLeft(a, 30);
		 c += s3(d, e, a, b, 57);  e = rotateLeft(e, 30);
		state58.save(a, b, c, d, e);
		 b += s3(c, d, e, a, 58);  d = rotateLeft(d, 30);
		 a += s3(b, c, d, e, 59);  c = rotateLeft(c, 30);

		 e += s4(a, b, c, d, 60);  b = rotateLeft(b, 30);
		 d += s4(e, a, b, c, 61);  a = rotateLeft(a, 30);
		 c += s4(d, e, a, b, 62);  e = rotateLeft(e, 30);
		 b += s4(c, d, e, a, 63);  d = rotateLeft(d, 30);
		 a += s4(b, c, d, e, 64);  c = rotateLeft(c, 30);
		state65.save(a, b, c, d, e);
		 e += s4(a, b, c, d, 65);  b = rotateLeft(b, 30);
		 d += s4(e, a, b, c, 66);  a = rotateLeft(a, 30);
		 c += s4(d, e, a, b, 67);  e = rotateLeft(e, 30);
		 b += s4(c, d, e, a, 68);  d = rotateLeft(d, 30);
		 a += s4(b, c, d, e, 69);  c = rotateLeft(c, 30);
		 e += s4(a, b, c, d, 70);  b = rotateLeft(b, 30);
		 d += s4(e, a, b, c, 71);  a = rotateLeft(a, 30);
		 c += s4(d, e, a, b, 72);  e = rotateLeft(e, 30);
		 b += s4(c, d, e, a, 73);  d = rotateLeft(d, 30);
		 a += s4(b, c, d, e, 74);  c = rotateLeft(c, 30);
		 e += s4(a, b, c, d, 75);  b = rotateLeft(b, 30);
		 d += s4(e, a, b, c, 76);  a = rotateLeft(a, 30);
		 c += s4(d, e, a, b, 77);  e = rotateLeft(e, 30);
		 b += s4(c, d, e, a, 78);  d = rotateLeft(d, 30);
		 a += s4(b, c, d, e, 79);  c = rotateLeft(c, 30);

		// @formatter:on
		h.save(h.a + a, h.b + b, h.c + c, h.d + d, h.e + e);
	}

	private void recompress(int t) {
		State s;
		if (t == 58) {
			s = state58;
		} else if (t == 65) {
			s = state65;
		} else {
			throw new IllegalStateException();
		}
		int a = s.a, b = s.b, c = s.c, d = s.d, e = s.e;

		// @formatter:off
	  if (t == 65) {
		{ c = rotateRight(c, 30);  a -= r4(b, c, d, e, 64);}
		{ d = rotateRight(d, 30);  b -= r4(c, d, e, a, 63);}
		{ e = rotateRight(e, 30);  c -= r4(d, e, a, b, 62);}
		{ a = rotateRight(a, 30);  d -= r4(e, a, b, c, 61);}
		{ b = rotateRight(b, 30);  e -= r4(a, b, c, d, 60);}

		{ c = rotateRight(c, 30);  a -= r3(b, c, d, e, 59);}
		{ d = rotateRight(d, 30);  b -= r3(c, d, e, a, 58);}
	  }
		{ e = rotateRight(e, 30);  c -= r3(d, e, a, b, 57);}
		{ a = rotateRight(a, 30);  d -= r3(e, a, b, c, 56);}
		{ b = rotateRight(b, 30);  e -= r3(a, b, c, d, 55);}
		{ c = rotateRight(c, 30);  a -= r3(b, c, d, e, 54);}
		{ d = rotateRight(d, 30);  b -= r3(c, d, e, a, 53);}
		{ e = rotateRight(e, 30);  c -= r3(d, e, a, b, 52);}
		{ a = rotateRight(a, 30);  d -= r3(e, a, b, c, 51);}
		{ b = rotateRight(b, 30);  e -= r3(a, b, c, d, 50);}
		{ c = rotateRight(c, 30);  a -= r3(b, c, d, e, 49);}
		{ d = rotateRight(d, 30);  b -= r3(c, d, e, a, 48);}
		{ e = rotateRight(e, 30);  c -= r3(d, e, a, b, 47);}
		{ a = rotateRight(a, 30);  d -= r3(e, a, b, c, 46);}
		{ b = rotateRight(b, 30);  e -= r3(a, b, c, d, 45);}
		{ c = rotateRight(c, 30);  a -= r3(b, c, d, e, 44);}
		{ d = rotateRight(d, 30);  b -= r3(c, d, e, a, 43);}
		{ e = rotateRight(e, 30);  c -= r3(d, e, a, b, 42);}
		{ a = rotateRight(a, 30);  d -= r3(e, a, b, c, 41);}
		{ b = rotateRight(b, 30);  e -= r3(a, b, c, d, 40);}

		{ c = rotateRight(c, 30);  a -= r2(b, c, d, e, 39);}
		{ d = rotateRight(d, 30);  b -= r2(c, d, e, a, 38);}
		{ e = rotateRight(e, 30);  c -= r2(d, e, a, b, 37);}
		{ a = rotateRight(a, 30);  d -= r2(e, a, b, c, 36);}
		{ b = rotateRight(b, 30);  e -= r2(a, b, c, d, 35);}
		{ c = rotateRight(c, 30);  a -= r2(b, c, d, e, 34);}
		{ d = rotateRight(d, 30);  b -= r2(c, d, e, a, 33);}
		{ e = rotateRight(e, 30);  c -= r2(d, e, a, b, 32);}
		{ a = rotateRight(a, 30);  d -= r2(e, a, b, c, 31);}
		{ b = rotateRight(b, 30);  e -= r2(a, b, c, d, 30);}
		{ c = rotateRight(c, 30);  a -= r2(b, c, d, e, 29);}
		{ d = rotateRight(d, 30);  b -= r2(c, d, e, a, 28);}
		{ e = rotateRight(e, 30);  c -= r2(d, e, a, b, 27);}
		{ a = rotateRight(a, 30);  d -= r2(e, a, b, c, 26);}
		{ b = rotateRight(b, 30);  e -= r2(a, b, c, d, 25);}
		{ c = rotateRight(c, 30);  a -= r2(b, c, d, e, 24);}
		{ d = rotateRight(d, 30);  b -= r2(c, d, e, a, 23);}
		{ e = rotateRight(e, 30);  c -= r2(d, e, a, b, 22);}
		{ a = rotateRight(a, 30);  d -= r2(e, a, b, c, 21);}
		{ b = rotateRight(b, 30);  e -= r2(a, b, c, d, 20);}

		{ c = rotateRight(c, 30);  a -= r1(b, c, d, e, 19);}
		{ d = rotateRight(d, 30);  b -= r1(c, d, e, a, 18);}
		{ e = rotateRight(e, 30);  c -= r1(d, e, a, b, 17);}
		{ a = rotateRight(a, 30);  d -= r1(e, a, b, c, 16);}
		{ b = rotateRight(b, 30);  e -= r1(a, b, c, d, 15);}
		{ c = rotateRight(c, 30);  a -= r1(b, c, d, e, 14);}
		{ d = rotateRight(d, 30);  b -= r1(c, d, e, a, 13);}
		{ e = rotateRight(e, 30);  c -= r1(d, e, a, b, 12);}
		{ a = rotateRight(a, 30);  d -= r1(e, a, b, c, 11);}
		{ b = rotateRight(b, 30);  e -= r1(a, b, c, d, 10);}
		{ c = rotateRight(c, 30);  a -= r1(b, c, d, e, 9);}
		{ d = rotateRight(d, 30);  b -= r1(c, d, e, a, 8);}
		{ e = rotateRight(e, 30);  c -= r1(d, e, a, b, 7);}
		{ a = rotateRight(a, 30);  d -= r1(e, a, b, c, 6);}
		{ b = rotateRight(b, 30);  e -= r1(a, b, c, d, 5);}
		{ c = rotateRight(c, 30);  a -= r1(b, c, d, e, 4);}
		{ d = rotateRight(d, 30);  b -= r1(c, d, e, a, 3);}
		{ e = rotateRight(e, 30);  c -= r1(d, e, a, b, 2);}
		{ a = rotateRight(a, 30);  d -= r1(e, a, b, c, 1);}
		{ b = rotateRight(b, 30);  e -= r1(a, b, c, d, 0);}

		hIn.save(a, b, c, d, e);
		a = s.a; b = s.b; c = s.c; d = s.d; e = s.e;

	  if (t == 58) {
		{ b += sr3(c, d, e, a, 58);  d = rotateLeft(d, 30);}
		{ a += sr3(b, c, d, e, 59);  c = rotateLeft(c, 30);}

		{ e += sr4(a, b, c, d, 60);  b = rotateLeft(b, 30);}
		{ d += sr4(e, a, b, c, 61);  a = rotateLeft(a, 30);}
		{ c += sr4(d, e, a, b, 62);  e = rotateLeft(e, 30);}
		{ b += sr4(c, d, e, a, 63);  d = rotateLeft(d, 30);}
		{ a += sr4(b, c, d, e, 64);  c = rotateLeft(c, 30);}
	  }
		{ e += sr4(a, b, c, d, 65);  b = rotateLeft(b, 30);}
		{ d += sr4(e, a, b, c, 66);  a = rotateLeft(a, 30);}
		{ c += sr4(d, e, a, b, 67);  e = rotateLeft(e, 30);}
		{ b += sr4(c, d, e, a, 68);  d = rotateLeft(d, 30);}
		{ a += sr4(b, c, d, e, 69);  c = rotateLeft(c, 30);}
		{ e += sr4(a, b, c, d, 70);  b = rotateLeft(b, 30);}
		{ d += sr4(e, a, b, c, 71);  a = rotateLeft(a, 30);}
		{ c += sr4(d, e, a, b, 72);  e = rotateLeft(e, 30);}
		{ b += sr4(c, d, e, a, 73);  d = rotateLeft(d, 30);}
		{ a += sr4(b, c, d, e, 74);  c = rotateLeft(c, 30);}
		{ e += sr4(a, b, c, d, 75);  b = rotateLeft(b, 30);}
		{ d += sr4(e, a, b, c, 76);  a = rotateLeft(a, 30);}
		{ c += sr4(d, e, a, b, 77);  e = rotateLeft(e, 30);}
		{ b += sr4(c, d, e, a, 78);  d = rotateLeft(d, 30);}
		{ a += sr4(b, c, d, e, 79);  c = rotateLeft(c, 30);}

		// @formatter:on
		hTmp.save(hIn.a + a, hIn.b + b, hIn.c + c, hIn.d + d, hIn.e + e);
	}

	private int s1(int a, int b, int c, int d, int t) {
		return rotateLeft(a, 5) + f1(b, c, d) + 0x5A827999 + w[t];
	}

	private int s2(int a, int b, int c, int d, int t) {
		return rotateLeft(a, 5) + f2(b, c, d) + 0x6ED9EBA1 + w[t];
	}

	private int s3(int a, int b, int c, int d, int t) {
		return rotateLeft(a, 5) + f3(b, c, d) + 0x8F1BBCDC + w[t];
	}

	private int sr3(int a, int b, int c, int d, int t) {
		return rotateLeft(a, 5) + f3(b, c, d) + 0x8F1BBCDC + w2[t];
	}

	private int s4(int a, int b, int c, int d, int t) {
		return rotateLeft(a, 5) + f2(b, c, d) + 0xCA62C1D6 + w[t];
	}

	private int sr4(int a, int b, int c, int d, int t) {
		return rotateLeft(a, 5) + f2(b, c, d) + 0xCA62C1D6 + w2[t];
	}

	private int r1(int a, int b, int c, int d, int t) {
		return rotateLeft(a, 5) + f1(b, c, d) + 0x5A827999 + w2[t];
	}

	private int r2(int a, int b, int c, int d, int t) {
		return rotateLeft(a, 5) + f2(b, c, d) + 0x6ED9EBA1 + w2[t];
	}

	private int r3(int a, int b, int c, int d, int t) {
		return rotateLeft(a, 5) + f3(b, c, d) + 0x8F1BBCDC + w2[t];
	}

	private int r4(int a, int b, int c, int d, int t) {
		return rotateLeft(a, 5) + f2(b, c, d) + 0xCA62C1D6 + w2[t];
	}

	private static int f1(int b, int c, int d) {
		// f: 0 <= t <= 19
		return (b & c) | ((~b) & d);
	}

	private static int f2(int b, int c, int d) {
		// f: 20 <= t <= 39
		// f: 60 <= t <= 79
		return b ^ c ^ d;
	}

	private static int f3(int b, int c, int d) {
		// f: 40 <= t <= 59
		return (b & c) | (b & d) | (c & d);
	}

	private static boolean eq(State q, State r) {
		return q.a == r.a
				&& q.b == r.b
				&& q.c == r.c
				&& q.d == r.d
				&& q.e == r.e;
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

		// SHA-1 appends the length of the message in bits after the
		// padding block (above). Here length is in bytes. Multiply by
		// 8 by shifting by 3 as part of storing the 64 bit byte length
		// into the two words expected in the trailer.
		NB.encodeInt32(buffer, 56, (int) (length >>> (32 - 3)));
		NB.encodeInt32(buffer, 60, (int) (length << 3));
		compress(buffer, 0);

		if (foundCollision) {
			ObjectId id = h.toObjectId();
			LOG.warn("possible SHA-1 collision; safeHash " //$NON-NLS-1$
					+ (safeHash ? "is" : "disabled,") //$NON-NLS-1$ //$NON-NLS-2$
					+ ' ' + id.name());
			if (!safeHash) {
				throw new Sha1CollisionException(id);
			}
		}
	}

	/**
	 * Finish the digest and return the resulting hash.
	 * <p>
	 * Once {@code digest()} is called, this instance should be discarded.
	 *
	 * @return the bytes for the resulting hash.
	 * @throws Sha1CollisionException
	 *             if a collision was detected and safeHash is false.
	 */
	public byte[] digest() throws Sha1CollisionException {
		finish();

		byte[] b = new byte[20];
		NB.encodeInt32(b, 0, h.a);
		NB.encodeInt32(b, 4, h.b);
		NB.encodeInt32(b, 8, h.c);
		NB.encodeInt32(b, 12, h.d);
		NB.encodeInt32(b, 16, h.e);
		return b;
	}

	/**
	 * Finish the digest and return the resulting hash.
	 * <p>
	 * Once {@code digest()} is called, this instance should be discarded.
	 *
	 * @return the ObjectId for the resulting hash.
	 * @throws Sha1CollisionException
	 *             if a collision was detected and safeHash is false.
	 */
	public ObjectId toObjectId() throws Sha1CollisionException {
		finish();
		return h.toObjectId();
	}

	/**
	 * Finish the digest and return the resulting hash.
	 * <p>
	 * Once {@code digest()} is called, this instance should be discarded.
	 *
	 * @param id
	 *            destination to copy the digest to.
	 * @throws Sha1CollisionException
	 *             if a collision was detected and safeHash is false.
	 */
	public void digest(MutableObjectId id) throws Sha1CollisionException {
		finish();
		id.set(h.a, h.b, h.c, h.d, h.e);
	}

	/**
	 * Check if a collision was detected.
	 *
	 * <p>
	 * This method only returns an accurate result after the digest was obtained
	 * through {@link #digest()}, {@link #digest(MutableObjectId)} or
	 * {@link #toObjectId()}, as the hashing function must finish processing to
	 * know the final state.
	 *
	 * @return {@code true} if a likely collision was detected.
	 */
	public boolean hasCollision() {
		return foundCollision;
	}

	/**
	 * Reset this instance to compute another hash.
	 *
	 * @return {@code this}.
	 */
	public SHA1 reset() {
		h.init();
		length = 0;
		foundCollision = false;
		return this;
	}

	private static final class State {
		int a;
		int b;
		int c;
		int d;
		int e;

		final void init() {
			// Magic initialization constants defined by FIPS180.
			save(0x67452301, 0xEFCDAB89, 0x98BADCFE, 0x10325476, 0xC3D2E1F0);
		}

		final void save(int a1, int b1, int c1, int d1, int e1) {
			a = a1;
			b = b1;
			c = c1;
			d = d1;
			e = e1;
		}

		ObjectId toObjectId() {
			return new ObjectId(a, b, c, d, e);
		}
	}
}
