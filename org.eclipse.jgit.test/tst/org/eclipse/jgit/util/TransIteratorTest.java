package org.eclipse.jgit.util;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import org.junit.Test;

public class TransIteratorTest {

	@Test
	public void transformation() {
		List<String> source = Arrays.asList("a", "bb", "ccc", "dddd");
		TransIterator<String, Integer> it = new TransIterator<>(
				source, str -> str.length());
		assertThat(it.next(), is(1));
		assertThat(it.next(), is(2));
		assertThat(it.next(), is(3));
		assertThat(it.next(), is(4));
		assertFalse(it.hasNext());
	}

	@Test
	public void hasNext() {
		List<String> source = Arrays.asList("a", "bb", "ccc", "dddd");
		TransIterator<String, Integer> it = new TransIterator<>(
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
		TransIterator<String, Integer> it = new TransIterator<>(
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
}