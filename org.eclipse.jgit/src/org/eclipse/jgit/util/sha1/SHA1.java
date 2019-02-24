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

import java.text.MessageFormat;
import java.util.Arrays;

import org.eclipse.jgit.internal.JGitText;
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
 * When detectCollision is true (default), this implementation throws
 * {@link org.eclipse.jgit.util.sha1.Sha1CollisionException} from any digest
 * method if a potential collision was detected.
 *
 * @since 4.7
 */
public class SHA1 {
	private static Logger LOG = LoggerFactory.getLogger(SHA1.class);
	private static final boolean DETECT_COLLISIONS;

	static {
		SystemReader sr = SystemReader.getInstance();
		String v = sr.getProperty("org.eclipse.jgit.util.sha1.detectCollision"); //$NON-NLS-1$
		DETECT_COLLISIONS = v != null ? Boolean.parseBoolean(v) : true;
	}

	/**
	 * Create a new context to compute a SHA-1 hash of data.
	 *
	 * @return a new context to compute a SHA-1 hash of data.
	 */
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
	 *            a boolean.
	 * @return {@code this}
	 */
	public SHA1 setDetectCollision(boolean detect) {
		detectCollision = detect;
		return this;
	}

	/**
	 * Update the digest computation by adding a byte.
	 *
	 * @param b a byte.
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
			w[t] = rotateLeft(x, 1); // S^1(...)
		}
	}

	private void compress() {
		// Method 1 from RFC 3174 section 6.1.
		// Method 2 (circular queue of 16 words) is slower.
		int a = h.a, b = h.b, c = h.c, d = h.d, e = h.e;

		// @formatter:off
		 e += s1(a, b, c, d,w[ 0]);  b = rotateLeft( b, 30);
		 d += s1(e, a, b, c,w[ 1]);  a = rotateLeft( a, 30);
		 c += s1(d, e, a, b,w[ 2]);  e = rotateLeft( e, 30);
		 b += s1(c, d, e, a,w[ 3]);  d = rotateLeft( d, 30);
		 a += s1(b, c, d, e,w[ 4]);  c = rotateLeft( c, 30);
		 e += s1(a, b, c, d,w[ 5]);  b = rotateLeft( b, 30);
		 d += s1(e, a, b, c,w[ 6]);  a = rotateLeft( a, 30);
		 c += s1(d, e, a, b,w[ 7]);  e = rotateLeft( e, 30);
		 b += s1(c, d, e, a,w[ 8]);  d = rotateLeft( d, 30);
		 a += s1(b, c, d, e,w[ 9]);  c = rotateLeft( c, 30);
		 e += s1(a, b, c, d,w[ 10]);  b = rotateLeft( b, 30);
		 d += s1(e, a, b, c,w[ 11]);  a = rotateLeft( a, 30);
		 c += s1(d, e, a, b,w[ 12]);  e = rotateLeft( e, 30);
		 b += s1(c, d, e, a,w[ 13]);  d = rotateLeft( d, 30);
		 a += s1(b, c, d, e,w[ 14]);  c = rotateLeft( c, 30);
		 e += s1(a, b, c, d,w[ 15]);  b = rotateLeft( b, 30);
		 d += s1(e, a, b, c,w[ 16]);  a = rotateLeft( a, 30);
		 c += s1(d, e, a, b,w[ 17]);  e = rotateLeft( e, 30);
		 b += s1(c, d, e, a,w[ 18]);  d = rotateLeft( d, 30);
		 a += s1(b, c, d, e,w[ 19]);  c = rotateLeft( c, 30);

		 e += s2(a, b, c, d,w[ 20]);  b = rotateLeft( b, 30);
		 d += s2(e, a, b, c,w[ 21]);  a = rotateLeft( a, 30);
		 c += s2(d, e, a, b,w[ 22]);  e = rotateLeft( e, 30);
		 b += s2(c, d, e, a,w[ 23]);  d = rotateLeft( d, 30);
		 a += s2(b, c, d, e,w[ 24]);  c = rotateLeft( c, 30);
		 e += s2(a, b, c, d,w[ 25]);  b = rotateLeft( b, 30);
		 d += s2(e, a, b, c,w[ 26]);  a = rotateLeft( a, 30);
		 c += s2(d, e, a, b,w[ 27]);  e = rotateLeft( e, 30);
		 b += s2(c, d, e, a,w[ 28]);  d = rotateLeft( d, 30);
		 a += s2(b, c, d, e,w[ 29]);  c = rotateLeft( c, 30);
		 e += s2(a, b, c, d,w[ 30]);  b = rotateLeft( b, 30);
		 d += s2(e, a, b, c,w[ 31]);  a = rotateLeft( a, 30);
		 c += s2(d, e, a, b,w[ 32]);  e = rotateLeft( e, 30);
		 b += s2(c, d, e, a,w[ 33]);  d = rotateLeft( d, 30);
		 a += s2(b, c, d, e,w[ 34]);  c = rotateLeft( c, 30);
		 e += s2(a, b, c, d,w[ 35]);  b = rotateLeft( b, 30);
		 d += s2(e, a, b, c,w[ 36]);  a = rotateLeft( a, 30);
		 c += s2(d, e, a, b,w[ 37]);  e = rotateLeft( e, 30);
		 b += s2(c, d, e, a,w[ 38]);  d = rotateLeft( d, 30);
		 a += s2(b, c, d, e,w[ 39]);  c = rotateLeft( c, 30);

		 e += s3(a, b, c, d,w[ 40]);  b = rotateLeft( b, 30);
		 d += s3(e, a, b, c,w[ 41]);  a = rotateLeft( a, 30);
		 c += s3(d, e, a, b,w[ 42]);  e = rotateLeft( e, 30);
		 b += s3(c, d, e, a,w[ 43]);  d = rotateLeft( d, 30);
		 a += s3(b, c, d, e,w[ 44]);  c = rotateLeft( c, 30);
		 e += s3(a, b, c, d,w[ 45]);  b = rotateLeft( b, 30);
		 d += s3(e, a, b, c,w[ 46]);  a = rotateLeft( a, 30);
		 c += s3(d, e, a, b,w[ 47]);  e = rotateLeft( e, 30);
		 b += s3(c, d, e, a,w[ 48]);  d = rotateLeft( d, 30);
		 a += s3(b, c, d, e,w[ 49]);  c = rotateLeft( c, 30);
		 e += s3(a, b, c, d,w[ 50]);  b = rotateLeft( b, 30);
		 d += s3(e, a, b, c,w[ 51]);  a = rotateLeft( a, 30);
		 c += s3(d, e, a, b,w[ 52]);  e = rotateLeft( e, 30);
		 b += s3(c, d, e, a,w[ 53]);  d = rotateLeft( d, 30);
		 a += s3(b, c, d, e,w[ 54]);  c = rotateLeft( c, 30);
		 e += s3(a, b, c, d,w[ 55]);  b = rotateLeft( b, 30);
		 d += s3(e, a, b, c,w[ 56]);  a = rotateLeft( a, 30);
		 c += s3(d, e, a, b,w[ 57]);  e = rotateLeft( e, 30);
		state58.save(a, b, c, d, e);
		 b += s3(c, d, e, a,w[ 58]);  d = rotateLeft( d, 30);
		 a += s3(b, c, d, e,w[ 59]);  c = rotateLeft( c, 30);

		 e += s4(a, b, c, d,w[ 60]);  b = rotateLeft( b, 30);
		 d += s4(e, a, b, c,w[ 61]);  a = rotateLeft( a, 30);
		 c += s4(d, e, a, b,w[ 62]);  e = rotateLeft( e, 30);
		 b += s4(c, d, e, a,w[ 63]);  d = rotateLeft( d, 30);
		 a += s4(b, c, d, e,w[ 64]);  c = rotateLeft( c, 30);
		state65.save(a, b, c, d, e);
		 e += s4(a, b, c, d,w[ 65]);  b = rotateLeft( b, 30);
		 d += s4(e, a, b, c,w[ 66]);  a = rotateLeft( a, 30);
		 c += s4(d, e, a, b,w[ 67]);  e = rotateLeft( e, 30);
		 b += s4(c, d, e, a,w[ 68]);  d = rotateLeft( d, 30);
		 a += s4(b, c, d, e,w[ 69]);  c = rotateLeft( c, 30);
		 e += s4(a, b, c, d,w[ 70]);  b = rotateLeft( b, 30);
		 d += s4(e, a, b, c,w[ 71]);  a = rotateLeft( a, 30);
		 c += s4(d, e, a, b,w[ 72]);  e = rotateLeft( e, 30);
		 b += s4(c, d, e, a,w[ 73]);  d = rotateLeft( d, 30);
		 a += s4(b, c, d, e,w[ 74]);  c = rotateLeft( c, 30);
		 e += s4(a, b, c, d,w[ 75]);  b = rotateLeft( b, 30);
		 d += s4(e, a, b, c,w[ 76]);  a = rotateLeft( a, 30);
		 c += s4(d, e, a, b,w[ 77]);  e = rotateLeft( e, 30);
		 b += s4(c, d, e, a,w[ 78]);  d = rotateLeft( d, 30);
		 a += s4(b, c, d, e,w[ 79]);  c = rotateLeft( c, 30);

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
		{ c = rotateRight( c, 30);  a -= s4(b, c, d, e,w2[ 64]);}
		{ d = rotateRight( d, 30);  b -= s4(c, d, e, a,w2[ 63]);}
		{ e = rotateRight( e, 30);  c -= s4(d, e, a, b,w2[ 62]);}
		{ a = rotateRight( a, 30);  d -= s4(e, a, b, c,w2[ 61]);}
		{ b = rotateRight( b, 30);  e -= s4(a, b, c, d,w2[ 60]);}

		{ c = rotateRight( c, 30);  a -= s3(b, c, d, e,w2[ 59]);}
		{ d = rotateRight( d, 30);  b -= s3(c, d, e, a,w2[ 58]);}
	  }
		{ e = rotateRight( e, 30);  c -= s3(d, e, a, b,w2[ 57]);}
		{ a = rotateRight( a, 30);  d -= s3(e, a, b, c,w2[ 56]);}
		{ b = rotateRight( b, 30);  e -= s3(a, b, c, d,w2[ 55]);}
		{ c = rotateRight( c, 30);  a -= s3(b, c, d, e,w2[ 54]);}
		{ d = rotateRight( d, 30);  b -= s3(c, d, e, a,w2[ 53]);}
		{ e = rotateRight( e, 30);  c -= s3(d, e, a, b,w2[ 52]);}
		{ a = rotateRight( a, 30);  d -= s3(e, a, b, c,w2[ 51]);}
		{ b = rotateRight( b, 30);  e -= s3(a, b, c, d,w2[ 50]);}
		{ c = rotateRight( c, 30);  a -= s3(b, c, d, e,w2[ 49]);}
		{ d = rotateRight( d, 30);  b -= s3(c, d, e, a,w2[ 48]);}
		{ e = rotateRight( e, 30);  c -= s3(d, e, a, b,w2[ 47]);}
		{ a = rotateRight( a, 30);  d -= s3(e, a, b, c,w2[ 46]);}
		{ b = rotateRight( b, 30);  e -= s3(a, b, c, d,w2[ 45]);}
		{ c = rotateRight( c, 30);  a -= s3(b, c, d, e,w2[ 44]);}
		{ d = rotateRight( d, 30);  b -= s3(c, d, e, a,w2[ 43]);}
		{ e = rotateRight( e, 30);  c -= s3(d, e, a, b,w2[ 42]);}
		{ a = rotateRight( a, 30);  d -= s3(e, a, b, c,w2[ 41]);}
		{ b = rotateRight( b, 30);  e -= s3(a, b, c, d,w2[ 40]);}

		{ c = rotateRight( c, 30);  a -= s2(b, c, d, e,w2[ 39]);}
		{ d = rotateRight( d, 30);  b -= s2(c, d, e, a,w2[ 38]);}
		{ e = rotateRight( e, 30);  c -= s2(d, e, a, b,w2[ 37]);}
		{ a = rotateRight( a, 30);  d -= s2(e, a, b, c,w2[ 36]);}
		{ b = rotateRight( b, 30);  e -= s2(a, b, c, d,w2[ 35]);}
		{ c = rotateRight( c, 30);  a -= s2(b, c, d, e,w2[ 34]);}
		{ d = rotateRight( d, 30);  b -= s2(c, d, e, a,w2[ 33]);}
		{ e = rotateRight( e, 30);  c -= s2(d, e, a, b,w2[ 32]);}
		{ a = rotateRight( a, 30);  d -= s2(e, a, b, c,w2[ 31]);}
		{ b = rotateRight( b, 30);  e -= s2(a, b, c, d,w2[ 30]);}
		{ c = rotateRight( c, 30);  a -= s2(b, c, d, e,w2[ 29]);}
		{ d = rotateRight( d, 30);  b -= s2(c, d, e, a,w2[ 28]);}
		{ e = rotateRight( e, 30);  c -= s2(d, e, a, b,w2[ 27]);}
		{ a = rotateRight( a, 30);  d -= s2(e, a, b, c,w2[ 26]);}
		{ b = rotateRight( b, 30);  e -= s2(a, b, c, d,w2[ 25]);}
		{ c = rotateRight( c, 30);  a -= s2(b, c, d, e,w2[ 24]);}
		{ d = rotateRight( d, 30);  b -= s2(c, d, e, a,w2[ 23]);}
		{ e = rotateRight( e, 30);  c -= s2(d, e, a, b,w2[ 22]);}
		{ a = rotateRight( a, 30);  d -= s2(e, a, b, c,w2[ 21]);}
		{ b = rotateRight( b, 30);  e -= s2(a, b, c, d,w2[ 20]);}

		{ c = rotateRight( c, 30);  a -= s1(b, c, d, e,w2[ 19]);}
		{ d = rotateRight( d, 30);  b -= s1(c, d, e, a,w2[ 18]);}
		{ e = rotateRight( e, 30);  c -= s1(d, e, a, b,w2[ 17]);}
		{ a = rotateRight( a, 30);  d -= s1(e, a, b, c,w2[ 16]);}
		{ b = rotateRight( b, 30);  e -= s1(a, b, c, d,w2[ 15]);}
		{ c = rotateRight( c, 30);  a -= s1(b, c, d, e,w2[ 14]);}
		{ d = rotateRight( d, 30);  b -= s1(c, d, e, a,w2[ 13]);}
		{ e = rotateRight( e, 30);  c -= s1(d, e, a, b,w2[ 12]);}
		{ a = rotateRight( a, 30);  d -= s1(e, a, b, c,w2[ 11]);}
		{ b = rotateRight( b, 30);  e -= s1(a, b, c, d,w2[ 10]);}
		{ c = rotateRight( c, 30);  a -= s1(b, c, d, e,w2[ 9]);}
		{ d = rotateRight( d, 30);  b -= s1(c, d, e, a,w2[ 8]);}
		{ e = rotateRight( e, 30);  c -= s1(d, e, a, b,w2[ 7]);}
		{ a = rotateRight( a, 30);  d -= s1(e, a, b, c,w2[ 6]);}
		{ b = rotateRight( b, 30);  e -= s1(a, b, c, d,w2[ 5]);}
		{ c = rotateRight( c, 30);  a -= s1(b, c, d, e,w2[ 4]);}
		{ d = rotateRight( d, 30);  b -= s1(c, d, e, a,w2[ 3]);}
		{ e = rotateRight( e, 30);  c -= s1(d, e, a, b,w2[ 2]);}
		{ a = rotateRight( a, 30);  d -= s1(e, a, b, c,w2[ 1]);}
		{ b = rotateRight( b, 30);  e -= s1(a, b, c, d,w2[ 0]);}

		hIn.save(a, b, c, d, e);
		a = s.a; b = s.b; c = s.c; d = s.d; e = s.e;

	  if (t == 58) {
		{ b += s3(c, d, e, a,w2[ 58]);  d = rotateLeft( d, 30);}
		{ a += s3(b, c, d, e,w2[ 59]);  c = rotateLeft( c, 30);}

		{ e += s4(a, b, c, d,w2[ 60]);  b = rotateLeft( b, 30);}
		{ d += s4(e, a, b, c,w2[ 61]);  a = rotateLeft( a, 30);}
		{ c += s4(d, e, a, b,w2[ 62]);  e = rotateLeft( e, 30);}
		{ b += s4(c, d, e, a,w2[ 63]);  d = rotateLeft( d, 30);}
		{ a += s4(b, c, d, e,w2[ 64]);  c = rotateLeft( c, 30);}
	  }
		{ e += s4(a, b, c, d,w2[ 65]);  b = rotateLeft( b, 30);}
		{ d += s4(e, a, b, c,w2[ 66]);  a = rotateLeft( a, 30);}
		{ c += s4(d, e, a, b,w2[ 67]);  e = rotateLeft( e, 30);}
		{ b += s4(c, d, e, a,w2[ 68]);  d = rotateLeft( d, 30);}
		{ a += s4(b, c, d, e,w2[ 69]);  c = rotateLeft( c, 30);}
		{ e += s4(a, b, c, d,w2[ 70]);  b = rotateLeft( b, 30);}
		{ d += s4(e, a, b, c,w2[ 71]);  a = rotateLeft( a, 30);}
		{ c += s4(d, e, a, b,w2[ 72]);  e = rotateLeft( e, 30);}
		{ b += s4(c, d, e, a,w2[ 73]);  d = rotateLeft( d, 30);}
		{ a += s4(b, c, d, e,w2[ 74]);  c = rotateLeft( c, 30);}
		{ e += s4(a, b, c, d,w2[ 75]);  b = rotateLeft( b, 30);}
		{ d += s4(e, a, b, c,w2[ 76]);  a = rotateLeft( a, 30);}
		{ c += s4(d, e, a, b,w2[ 77]);  e = rotateLeft( e, 30);}
		{ b += s4(c, d, e, a,w2[ 78]);  d = rotateLeft( d, 30);}
		{ a += s4(b, c, d, e,w2[ 79]);  c = rotateLeft( c, 30);}

		// @formatter:on
		hTmp.save(hIn.a + a, hIn.b + b, hIn.c + c, hIn.d + d, hIn.e + e);
	}

	private static int s1(int a, int b, int c, int d, int w_t) {
		return rotateLeft(a, 5)
				// f: 0 <= t <= 19
				+ ((b & c) | ((~b) & d))
				+ 0x5A827999 + w_t;
	}

	private static int s2(int a, int b, int c, int d, int w_t) {
		return rotateLeft(a, 5)
				// f: 20 <= t <= 39
				+ (b ^ c ^ d)
				+ 0x6ED9EBA1 + w_t;
	}

	private static int s3(int a, int b, int c, int d, int w_t) {
		return rotateLeft(a, 5)
				// f: 40 <= t <= 59
				+ ((b & c) | (b & d) | (c & d))
				+ 0x8F1BBCDC + w_t;
	}

	private static int s4(int a, int b, int c, int d, int w_t) {
		return rotateLeft(a, 5)
				// f: 60 <= t <= 79
				+ (b ^ c ^ d)
				+ 0xCA62C1D6 + w_t;
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
			LOG.warn(MessageFormat.format(JGitText.get().sha1CollisionDetected,
					id.name()));
			throw new Sha1CollisionException(id);
		}
	}

	/**
	 * Finish the digest and return the resulting hash.
	 * <p>
	 * Once {@code digest()} is called, this instance should be discarded.
	 *
	 * @return the bytes for the resulting hash.
	 * @throws org.eclipse.jgit.util.sha1.Sha1CollisionException
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
	 * @throws org.eclipse.jgit.util.sha1.Sha1CollisionException
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
	 * @throws org.eclipse.jgit.util.sha1.Sha1CollisionException
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
