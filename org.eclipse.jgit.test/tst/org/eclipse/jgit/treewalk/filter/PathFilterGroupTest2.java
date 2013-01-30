/*
 * Copyright (C) 2013, Robin Rosenberg
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
package org.eclipse.jgit.treewalk.filter;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.Assert.assertEquals;

import java.io.IOException;

import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.Test;

/**
 * Performance test suite for PathFilterGroup
 */
public class PathFilterGroupTest2 {

	private DirCache dc;

	private String[] paths = new String[100000];

	private TreeFilter pf;

	private String data;

	private long tInit;

	public void setup(int pathsize, int filtersize, String[] filters) {
		long t1 = System.nanoTime();
		String[] seed = { "abc", "def", "ghi", "jkl", "mno", "pqr", "stu",
				"vwx", "xyz", "\u00e5\u00e4\u00f6" };
		dc = DirCache.newInCore();
		DirCacheEditor ed = dc.editor();
		int pi = 0;
		B: for (String a : seed) {
			for (String b : seed) {
				for (String c : seed) {
					for (String d : seed) {
						for (String e : seed) {
							if (pi >= pathsize)
								break B;
							String p1 = a + "/" + b + "/" + c + "/" + d + "/"
									+ e;
							paths[pi] = p1;
							ed.add(new DirCacheEditor.PathEdit(p1) {

								@Override
								public void apply(DirCacheEntry ent) {
									ent.setFileMode(FileMode.REGULAR_FILE);
								}
							});
							++pi;
						}
					}
				}
			}
		}
		ed.finish();
		long t2 = System.nanoTime();
		if (filters != null)
			pf = PathFilterGroup.createFromStrings(filters);
		else {
			// System.out.println(dc.getEntryCount());
			String[] filterPaths = new String[filtersize];
			System.arraycopy(paths, pathsize - filtersize, filterPaths, 0,
					filtersize);
			pf = PathFilterGroup.createFromStrings(filterPaths);
		}
		long t3 = System.nanoTime();
		data = "PX\t" + (t2 - t1) / 1E9 + "\t" + (t3 - t2) / 1E9 + "\t";
		tInit = t2-t1;
	}

	public void test(int pathSize, int filterSize)
			throws MissingObjectException, IncorrectObjectTypeException,
			IOException {
		setup(pathSize, filterSize, null);
		data += pathSize + "\t" + filterSize + "\t";
		long t1 = System.nanoTime();
		TreeWalk tw = new TreeWalk((ObjectReader) null);
		tw.reset();
		tw.addTree(new DirCacheIterator(dc));
		tw.setFilter(pf);
		tw.setRecursive(true);
		int n = 0;
		while (tw.next())
			n++;
		long t2 = System.nanoTime();
		data += (t2 - t1) / 1E9;
		assertEquals(filterSize, n);
	}

	@SuppressWarnings("boxing")
	public void test(int pathSize, int expectedWalkCount, String... filters)
			throws MissingObjectException, IncorrectObjectTypeException,
			IOException {
		setup(pathSize, -1, filters);
		data += pathSize + "\t" + filters.length + "\t";
		long t1 = System.nanoTime();
		TreeWalk tw = new TreeWalk((ObjectReader) null);
		tw.reset();
		tw.addTree(new DirCacheIterator(dc));
		tw.setFilter(pf);
		tw.setRecursive(true);
		int n = 0;
		while (tw.next())
			n++;
		long t2 = System.nanoTime();
		data += (t2 - t1) / 1E9;
		assertEquals(expectedWalkCount, n);

		// Walk time should be (much) faster then setup time
		assertThat(t2 - t1, lessThan(tInit / 2));
	}

	@Test
	public void test1_1() throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		test(1, 1);
	}

	@Test
	public void test2_2() throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		test(2, 2);
	}

	@Test
	public void test10_10() throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		test(10, 10);
	}

	@Test
	public void test100_100() throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		test(100, 100);
	}

	@Test
	public void test1000_1000() throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		test(1000, 1000);
	}

	@Test
	public void test10000_10000() throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		test(10000, 10000);
	}

	@Test
	public void test100000_100000() throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		test(100000, 100000);
	}

	@Test
	public void test100000_10000() throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		test(100000, 10000);
	}

	@Test
	public void test100000_1000() throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		test(100000, 1000);
	}

	@Test
	public void test100000_100() throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		test(100000, 100);
	}

	@Test
	public void test100000_10() throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		test(100000, 10);
	}

	@Test
	public void test100000_1() throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		test(100000, 1);
	}

	@Test
	public void test10000_1F() throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		test(100000, 10000, "abc");
	}

	@Test
	public void test10000_L2() throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		test(100000, 20000, "abc", "def");
	}

	@Test
	public void test10000_L10() throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		test(100000, 10000, "\u00e5\u00e4\u00f6");
	}
}
