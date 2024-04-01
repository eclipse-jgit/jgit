/*
 * Copyright (C) 2024 Google LLC.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.treewalk.filter;

import org.eclipse.jgit.internal.storage.commitgraph.ChangedPathFilter;
import org.eclipse.jgit.lib.Constants;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public class OrTreeFilterTest {

	@Test
	public void testApplyPath_NonPathFilter_Or_PathFilter_Match() {
		String[] path = { "path1" };
		ChangedPathFilter cpfContainModified = cpf(path);

		String[] wrongPath = { "path3", "path4" };
		ChangedPathFilter cpfWithoutPath = cpf(wrongPath);

		TreeFilter opf = OrTreeFilter.create(TreeFilter.ANY_DIFF,
				PathFilter.create("path1"));
		assertEquals(opf.applyPath(cpfContainModified),
				TreeFilter.ApplyPathResult.TRUE);
		assertEquals(opf.applyPath(cpfWithoutPath),
				TreeFilter.ApplyPathResult.TRUE);
	}

	@Test
	public void testApplyPath_PathFilter_Or_PathFilter_Match() {
		String[] path = { "path1" };
		ChangedPathFilter cpfContainModified = cpf(path);

		String[] path2 = { "path2" };
		ChangedPathFilter cpfContainModified2 = cpf(path2);

		TreeFilter opf = OrTreeFilter.create(PathFilter.create("path1"),
				PathFilter.create("path2"));
		assertEquals(opf.applyPath(cpfContainModified),
				TreeFilter.ApplyPathResult.TRUE);
		assertEquals(opf.applyPath(cpfContainModified2),
				TreeFilter.ApplyPathResult.TRUE);
	}

	@Test
	public void testApplyPath_PathFilter_Or_PathFilter_NoMatch() {
		String[] path3 = { "path4" };
		ChangedPathFilter cpfContainModified3 = cpf(path3);

		TreeFilter opf = OrTreeFilter.create(PathFilter.create("path1"),
				PathFilter.create("path2"));
		assertEquals(opf.applyPath(cpfContainModified3),
				TreeFilter.ApplyPathResult.FALSE);
	}

	@Test
	public void testApplyPath_NonPathFilter_Or_NonPathFilter_NotApplicable() {
		String[] path = { "path1" };
		ChangedPathFilter cpfContainModified = cpf(path);

		TreeFilter opf = OrTreeFilter.create(TreeFilter.ANY_DIFF,
				TreeFilter.ANY_DIFF);
		assertEquals(opf.applyPath(cpfContainModified),
				TreeFilter.ApplyPathResult.NOT_APPLICABLE);
	}

	@Test
	public void testApplyPath_NonPathFilter_Or_PathFilter_Or_NonPathFilter_Match() {
		String[] path = { "path1" };
		ChangedPathFilter cpfContainModified = cpf(path);

		String[] wrongPath = { "path3", "path4" };
		ChangedPathFilter cpfWithoutPath = cpf(wrongPath);

		TreeFilter opf = OrTreeFilter
				.create(new TreeFilter[] { TreeFilter.ANY_DIFF,
						PathFilter.create("path1"), TreeFilter.ANY_DIFF });
		assertEquals(opf.applyPath(cpfContainModified),
				TreeFilter.ApplyPathResult.TRUE);
		assertEquals(opf.applyPath(cpfWithoutPath),
				TreeFilter.ApplyPathResult.TRUE);
	}

	@Test
	public void testApplyPath_PathFilter_Or_NonPathFilter_Or_PathFilter_Match() {
		String[] path = { "path1", "path2" };
		ChangedPathFilter cpfContainModified = cpf(path);

		String[] wrongPath = { "path3", "path4" };
		ChangedPathFilter cpfWithoutPath = cpf(wrongPath);

		TreeFilter opf = OrTreeFilter
				.create(new TreeFilter[] { PathFilter.create("path1"),
						TreeFilter.ANY_DIFF, PathFilter.create("path2") });
		assertEquals(opf.applyPath(cpfContainModified),
				TreeFilter.ApplyPathResult.TRUE);
		assertEquals(opf.applyPath(cpfWithoutPath),
				TreeFilter.ApplyPathResult.TRUE);
	}

	@Test
	public void testApplyPath_PathFilter_Or_PathFilter_Or_PathFilter_Match() {

		String[] path = { "path1" };
		ChangedPathFilter cpfContainModified = cpf(path);

		String[] path2 = { "path2" };
		ChangedPathFilter cpfContainModified2 = cpf(path2);

		String[] path3 = { "path3" };
		ChangedPathFilter cpfContainModified3 = cpf(path3);

		TreeFilter opf = OrTreeFilter.create(new TreeFilter[] {
				PathFilter.create("path1"), PathFilter.create("path2"),
				PathFilter.create("path3") });
		assertEquals(opf.applyPath(cpfContainModified),
				TreeFilter.ApplyPathResult.TRUE);
		assertEquals(opf.applyPath(cpfContainModified2),
				TreeFilter.ApplyPathResult.TRUE);
		assertEquals(opf.applyPath(cpfContainModified3),
				TreeFilter.ApplyPathResult.TRUE);
	}

	@Test
	public void testApplyPath_PathFilter_Or_PathFilter_Or_PathFilter_NoMatch() {
		String[] path4 = { "path4" };
		ChangedPathFilter cpfWithoutPath = cpf(path4);

		TreeFilter opf = OrTreeFilter.create(new TreeFilter[] {
				PathFilter.create("path1"), PathFilter.create("path2"),
				PathFilter.create("path3") });
		assertEquals(opf.applyPath(cpfWithoutPath),
				TreeFilter.ApplyPathResult.FALSE);
	}

	@Test
	public void testApplyPath_NonPathFilter_Or_NonPathFilter_Or_NonPathFilter_NotApplicable() {
		String[] path = { "path1" };
		ChangedPathFilter cpfContainModified = cpf(path);

		TreeFilter apf = AndTreeFilter
				.create(new TreeFilter[] { TreeFilter.ANY_DIFF,
						TreeFilter.ANY_DIFF, TreeFilter.ANY_DIFF });
		assertEquals(apf.applyPath(cpfContainModified),
				TreeFilter.ApplyPathResult.NOT_APPLICABLE);
	}

	private static ChangedPathFilter cpf(String[] paths) {
		Set<ByteBuffer> b = Arrays.stream(paths)
				.map(s -> ByteBuffer.wrap(Constants.encode(s)))
				.collect(Collectors.toSet());
		return ChangedPathFilter.fromPaths(b);
	}
}
