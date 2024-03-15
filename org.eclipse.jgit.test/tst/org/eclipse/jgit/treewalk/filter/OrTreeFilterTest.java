package org.eclipse.jgit.treewalk.filter;

import org.junit.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class OrTreeFilterTest {
	@Test
	public void testGetPathsBestEffortWithMultiplePathFilter() {
		PathFilter p1 = PathFilter.create("a");
		PathFilter p2 = PathFilter.create("b");
		PathFilter p3 = PathFilter.create("c");

		TreeFilter[] tf = new TreeFilter[] { p1, p2, p3 };

		Set<byte[]> expected = Arrays.stream(tf)
				.flatMap(f -> f.getPathsBestEffort().get().stream())
				.collect(Collectors.toSet());

		TreeFilter otf = OrTreeFilter.create(tf);
		assertTrue(otf.getPathsBestEffort().isPresent());
		Set<byte[]> actual = otf.getPathsBestEffort().get();
		assertEquals(3, actual.size());
		for (byte[] actualPath : actual) {
			boolean findMatch = false;
			for (byte[] expectedPath : expected) {
				if (Arrays.equals(actualPath, expectedPath)) {
					findMatch = true;
				}
			}
			assertTrue(findMatch);
		}
	}

	@Test
	public void testGetPathsBestEffortWithNoPathFilter() {
		TreeFilter[] tf = new TreeFilter[] { TreeFilter.ANY_DIFF,
				TreeFilter.ANY_DIFF };

		TreeFilter otf = OrTreeFilter.create(tf);
		assertTrue(otf.getPathsBestEffort().isEmpty());
	}

	@Test
	public void testGetPathsBestEffortWithSinglePathFilter() {
		PathFilter p1 = PathFilter.create("a");
		Set<byte[]> expected = p1.getPathsBestEffort().get();

		TreeFilter[] tf = new TreeFilter[] { p1, TreeFilter.ANY_DIFF };

		TreeFilter otf = OrTreeFilter.create(tf);
		assertTrue(otf.getPathsBestEffort().isPresent());
		Set<byte[]> actual = otf.getPathsBestEffort().get();
		assertEquals(1, actual.size());
		for (byte[] actualPath : actual) {
			boolean findMatch = false;
			for (byte[] expectedPath : expected) {
				if (Arrays.equals(actualPath, expectedPath)) {
					findMatch = true;
				}
			}
			assertTrue(findMatch);
		}
	}
}
