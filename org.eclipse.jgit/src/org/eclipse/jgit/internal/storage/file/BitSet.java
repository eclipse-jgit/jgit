/*
 * Copyright (C) 2012, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.file;

import com.googlecode.javaewah.EWAHCompressedBitmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

/**
 * A random access BitSet to supports efficient conversions to
 * EWAHCompressedBitmap.
 */
final class BitSet {
  private static final Logger LOG = LoggerFactory.getLogger(BitSet.class);
  private final int initialCapacity;
  private long[] words;

	BitSet(int initialCapacity) {
      this.initialCapacity = initialCapacity;
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
      try {
        words[block] |= mask(position);
      } catch (ArrayIndexOutOfBoundsException e) {
        LOG.error(
                String.format(
                        "TROUBLESHOOTING|Error setting bitmap: INITIAL_CAPACITY: %s, CUR_SIZE: %s, BLOCK: %s, POSITION: %s",
                        initialCapacity, words.length, block, position),
                e);
        throw e;
      }
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
				compressed.addWord(lastNonEmptyWord);

			if (runningEmptyWords > 0) {
				compressed.addStreamOfEmptyWords(false, runningEmptyWords);
				runningEmptyWords = 0;
			}

			lastNonEmptyWord = word;
		}
		int bitsThatMatter = 64 - Long.numberOfLeadingZeros(lastNonEmptyWord);
		if (bitsThatMatter > 0)
			compressed.addWord(lastNonEmptyWord, bitsThatMatter);
		return compressed;
	}

	private static final int block(int position) {
		return position >> 6;
	}

	private static final long mask(int position) {
		return 1L << position;
	}
}
