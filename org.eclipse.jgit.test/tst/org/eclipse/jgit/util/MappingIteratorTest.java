/*
 * Copyright (c) 2019, Google LLC and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
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
		assertThat(transformAndCount.getInvocationCounter(), is(1));
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