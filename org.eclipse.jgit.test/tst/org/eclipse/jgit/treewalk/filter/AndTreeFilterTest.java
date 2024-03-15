package org.eclipse.jgit.treewalk.filter;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class AndTreeFilterTest {
	@Test
	public void testGetPathsBestEffortWithMultiplePathFilter() {
		TreeFilter[] tf = new TreeFilter[] { PathFilter.create("a/b"),
				PathFilter.create("a") };
		assertThrows(UnsupportedOperationException.class,
				() -> AndTreeFilter.create(tf).getPathsBestEffort());
	}

	@Test
	public void testGetPathsBestEffortWithNoPathFilter() {
		TreeFilter[] tf = new TreeFilter[] { TreeFilter.ANY_DIFF,
				TreeFilter.ALL };
		assertTrue(AndTreeFilter.create(tf).getPathsBestEffort().isEmpty());
	}

	@Test
	public void testGetPathsBestEffortWithSinglePathFilter() {
		PathFilter p = PathFilter.create("a");
		byte[] expected = p.getPathsBestEffort().get().iterator().next();
		TreeFilter[] tf = new TreeFilter[] { p, TreeFilter.ANY_DIFF };
		TreeFilter atf = AndTreeFilter.create(tf);

		assertTrue(atf.getPathsBestEffort().isPresent());
		byte[] actual = atf.getPathsBestEffort().get().iterator().next();
		assertEquals(1, actual.length);
		Arrays.equals(expected, actual);
	}
}
