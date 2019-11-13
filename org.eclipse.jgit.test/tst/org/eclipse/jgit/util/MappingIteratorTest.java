/*
 * Copyright (C) 2019, Google LLC
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

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import org.junit.Test;

public class MappingIteratorTest {

	@Test
	public void transformation() {
		List<String> source = Arrays.asList("a", "bb", "ccc", "dddd");
		MappingIterator<String, Integer> it = new MappingIterator<>(
				source, str -> str.length());
		assertThat(it.next(), is(1));
		assertThat(it.next(), is(2));
		assertThat(it.next(), is(3));
		assertThat(it.next(), is(4));
		assertFalse(it.hasNext());
	}

	@Test
	public void failedTransformation() {
		List<String> source = Arrays.asList("a", "bb", "ccc", "dddd");
		MappingIterator<String, Integer> it = new MappingIterator<>(source,
				str -> null);
		assertFalse(it.hasNext());
	}

	@Test
	public void failedSomeTransformation() {
		List<String> source = Arrays.asList("a", "bb", "ccc", "dddd");
		MappingIterator<String, Integer> it = new MappingIterator<>(source,
				new FailAfter(2));
		assertTrue(it.hasNext());
		assertThat(it.next(), is(1));
		assertTrue(it.hasNext());
		assertThat(it.next(), is(2));
		assertFalse(it.hasNext());
	}

	@Test
	public void hasNext() {
		List<String> source = Arrays.asList("a", "bb", "ccc", "dddd");
		MappingIterator<String, Integer> it = new MappingIterator<>(
				source, str -> str.length());
		int expectedLength = 1;
		while (it.hasNext()) {
			assertThat(it.next(), is(expectedLength));
			expectedLength += 1;
		}
	}

	@Test
	public void isLazy() {
		Counter transformAndCount = new Counter();
		List<String> source = Arrays.asList("aaaa", "bbbb", "cccc");
		MappingIterator<String, Integer> it = new MappingIterator<>(
				source, transformAndCount);
		assertThat(it.next(), is(4));
		// The iterator is one calculation ahead
		assertThat(transformAndCount.getInvocationCounter(), is(2));
	}

	private static class Counter implements Function<String, Integer> {

		private int invocationCounter = 0;

		@Override
		public Integer apply(String t) {
			invocationCounter += 1;
			return t.length();
		}

		public int getInvocationCounter() {
			return invocationCounter;
		}
	}

	private static class FailAfter implements Function<String, Integer> {

		private int failAfter;

		FailAfter(int n) {
			this.failAfter = n;
		}

		@Override
		public Integer apply(String t) {
			this.failAfter -= 1;
			if (failAfter < 0) {
				return null;
			}

			return t.length();
		}
	}

}