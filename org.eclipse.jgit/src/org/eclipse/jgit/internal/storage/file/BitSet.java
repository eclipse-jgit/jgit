/*
 * Copyright (C) 2012, Google Inc.
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

package org.eclipse.jgit.internal.storage.file;

import java.util.Arrays;

import com.googlecode.javaewah.EWAHCompressedBitmap;

/**
 * A random access BitSet to supports efficient conversions to
 * EWAHCompressedBitmap.
 */
final class BitSet {

	private long[] words;

	BitSet(int initialCapacity) {
		words = new long[block(initialCapacity) + 1];
	}

	final void clear() {
		Arrays.fill(words, 0);
	}

	final void set(int position) {
		int block = block(position);
		if (block >= words.length) {
			long[] buf = new long[2 * block(position)];
			System.arraycopy(words, 0, buf, 0, words.length);
			words = buf;
		}
		words[block] |= mask(position);
	}

	final void clear(int position) {
		int block = block(position);
		if (block < words.length)
			words[block] &= ~mask(position);
	}

	final boolean get(int position) {
		int block = block(position);
		return block < words.length && (words[block] & mask(position)) != 0;
	}

	final EWAHCompressedBitmap toEWAHCompressedBitmap() {
		EWAHCompressedBitmap compressed = new EWAHCompressedBitmap(
				words.length);
		int runningEmptyWords = 0;
		long lastNonEmptyWord = 0;
		for (long word : words) {
			if (word == 0) {
				runningEmptyWords++;
				continue;
			}

			if (lastNonEmptyWord != 0)
				compressed.add(lastNonEmptyWord);

			if (runningEmptyWords > 0) {
				compressed.addStreamOfEmptyWords(false, runningEmptyWords);
				runningEmptyWords = 0;
			}

			lastNonEmptyWord = word;
		}
		int bitsThatMatter = 64 - Long.numberOfLeadingZeros(lastNonEmptyWord);
		if (bitsThatMatter > 0)
			compressed.add(lastNonEmptyWord, bitsThatMatter);
		return compressed;
	}

	private static final int block(int position) {
		return position >> 6;
	}

	private static final long mask(int position) {
		return 1L << position;
	}
}
