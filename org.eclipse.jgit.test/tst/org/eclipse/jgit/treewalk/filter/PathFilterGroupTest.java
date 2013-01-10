package org.eclipse.jgit.treewalk.filter;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;

import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.StopWalkException;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.Before;
import org.junit.Test;

public class PathFilterGroupTest {

	private TreeFilter filter;

	@Before
	public void setup() {
		// @formatter:off
		String[] paths = new String[] {
				"/a", // never match
				"/a/b", // never match
				"a",
				"b/c",
				"c/d/e",
				"c/d/f",
				"d/e/f/g"
				};
		// @formatter:on
		filter = PathFilterGroup.createFromStrings(paths);
	}

	@Test
	public void testExact() throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		assertTrue(filter.include(fakeWalk("a")));
		assertTrue(filter.include(fakeWalk("b/c")));
		assertTrue(filter.include(fakeWalk("c/d/e")));
		assertTrue(filter.include(fakeWalk("c/d/f")));
		assertTrue(filter.include(fakeWalk("d/e/f/g")));
	}

	@Test
	public void testNoMatchButClose() throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		assertFalse(filter.include(fakeWalk("a+")));
		assertFalse(filter.include(fakeWalk("b+/c")));
		assertFalse(filter.include(fakeWalk("c+/d/e")));
		assertFalse(filter.include(fakeWalk("c+/d/f")));
		assertFalse(filter.include(fakeWalk("c/d.a")));
		assertFalse(filter.include(fakeWalk("d+/e/f/g")));
	}

	@Test
	public void testJustCommonPrefixIsNotMatch() throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		assertFalse(filter.include(fakeWalk("b/a")));
		assertFalse(filter.include(fakeWalk("b/d")));
		assertFalse(filter.include(fakeWalk("c/d/a")));
		assertFalse(filter.include(fakeWalk("d/e/e")));
	}

	@Test
	public void testKeyIsPrefixOfFilter() throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		assertTrue(filter.include(fakeWalk("b")));
		assertTrue(filter.include(fakeWalk("c/d")));
		assertTrue(filter.include(fakeWalk("c/d")));
		assertTrue(filter.include(fakeWalk("c")));
		assertTrue(filter.include(fakeWalk("d/e/f")));
		assertTrue(filter.include(fakeWalk("d/e")));
		assertTrue(filter.include(fakeWalk("d")));
	}

	@Test
	public void testFilterIsPrefixOfKey() throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		assertTrue(filter.include(fakeWalk("a/b")));
		assertTrue(filter.include(fakeWalk("b/c/d")));
		assertTrue(filter.include(fakeWalk("c/d/e/f")));
		assertTrue(filter.include(fakeWalk("c/d/f/g")));
		assertTrue(filter.include(fakeWalk("d/e/f/g/h")));
	}

	@Test
	public void testStopWalk() throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		// Obvious
		filter.include(fakeWalk("d/e/f/f"));

		// Obvious
		try {
			filter.include(fakeWalk("de"));
			fail("StopWalkException expected");
		} catch (StopWalkException e) {
			// good
		}

		// less obvious due to git sorting order
		filter.include(fakeWalk("d."));

		// less obvious due to git sorting order
		try {
			filter.include(fakeWalk("d0"));
			fail("StopWalkException expected");
		} catch (StopWalkException e) {
			// good
		}

		// non-ascii
		try {
			filter.include(fakeWalk("\u00C0"));
			fail("StopWalkException expected");
		} catch (StopWalkException e) {
			// good
		}
	}

	TreeWalk fakeWalk(final String path) throws IOException {
		TreeWalk ret = new TreeWalk((ObjectReader) null);
		ret.reset();
		ret.setRecursive(true);
		DirCache dc = DirCache.newInCore();
		DirCacheEditor dce = dc.editor();
		dce.add(new DirCacheEditor.PathEdit(path) {

			public void apply(DirCacheEntry ent) {
				ent.setFileMode(FileMode.REGULAR_FILE);
			}
		});
		dce.finish();
		ret.addTree(new DirCacheIterator(dc));
		ret.next();
		return ret;
	}

}
