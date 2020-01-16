/*
 * Copyright (C) 2018, Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.treewalk;

import static org.junit.Assert.fail;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.junit.Test;

/**
 * Simple performance test for git add / FileTreeIterator.
 */
public class FileTreeIteratorPerformanceTest extends RepositoryTestCase {

	private static int N_OF_FILES = 501;

	@Test
	public void testPerformance() throws Exception {
		try (Git git = new Git(db)) {
			long times[] = new long[N_OF_FILES];
			long sum = 0;
			String lastName = null;
			for (int i = 0; i < N_OF_FILES; i++) {
				lastName = "a" + (i + 100000) + ".txt";
				writeTrashFile(lastName, "");
				long start = System.nanoTime();
				git.add().addFilepattern(lastName).call();
				long elapsed = System.nanoTime() - start;
				times[i] = elapsed;
				sum += elapsed;
			}
			System.out.println("Total (µs) " + sum / 1000.0);
			for (int i = 0; i < times.length; i++) {
				System.out
						.println("Time " + i + " (µs) = " + times[i] / 1000.0);
			}
			FileTreeIterator iter = new FileTreeIterator(db);
			try (TreeWalk walk = new TreeWalk(db)) {
				walk.setFilter(PathFilter.create(lastName));
				walk.addTree(iter);
				long start = System.nanoTime();
				if (walk.next()) {
					System.out.println("Walk time (µs) = "
							+ (System.nanoTime() - start) / 1000.0);
				} else {
					fail("File not found");
				}
			}
		}
	}
}
