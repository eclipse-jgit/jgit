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

public class AndTreeFilterTest {

	@Test
	public void testApplyPath_NonPathFilter_And_PathFilter_Match() {
		String[] path = { "path1" };
		ChangedPathFilter cpfContainModified = cpf(path);

		TreeFilter apf = AndTreeFilter.create(TreeFilter.ANY_DIFF,
				PathFilter.create("path1"));
		assertEquals(apf.applyPath(cpfContainModified),
				TreeFilter.ApplyPathResult.TRUE);
	}

	@Test
	public void testApplyPath_NonPathFilter_And_PathFilter_NoMatch() {
		String[] wrongPath = { "path3", "path4" };
		ChangedPathFilter cpfWithoutPath = cpf(wrongPath);

		TreeFilter apf = AndTreeFilter.create(TreeFilter.ANY_DIFF,
				PathFilter.create("path1"));
		assertEquals(apf.applyPath(cpfWithoutPath),
				TreeFilter.ApplyPathResult.FALSE);
	}

	@Test
	public void testApplyPath_PathFilter_And_PathFilter_Always_NoMatch() {
		String[] path = { "path1" };
		ChangedPathFilter cpfContainModified = cpf(path);

		TreeFilter apf = AndTreeFilter.create(PathFilter.create("path1"),
				PathFilter.create("path2"));
		assertEquals(apf.applyPath(cpfContainModified),
				TreeFilter.ApplyPathResult.FALSE);
	}

	@Test
	public void testApplyPath_NonPathFilter_And_NonPathFilter_Always_NotApplicable() {
		String[] path = { "path1" };
		ChangedPathFilter cpfContainModified = cpf(path);

		TreeFilter apf = AndTreeFilter.create(TreeFilter.ANY_DIFF,
				TreeFilter.ANY_DIFF);
		assertEquals(apf.applyPath(cpfContainModified),
				TreeFilter.ApplyPathResult.NOT_APPLICABLE);
	}

	@Test
	public void testApplyPath_NonPathFilter_And_PathFilter_And_NonPathFilter_Match() {
		String[] path = { "path1" };
		ChangedPathFilter cpfContainModified = cpf(path);

		TreeFilter apf = AndTreeFilter
				.create(new TreeFilter[] { TreeFilter.ANY_DIFF,
						PathFilter.create("path1"), TreeFilter.ANY_DIFF });
		assertEquals(apf.applyPath(cpfContainModified),
				TreeFilter.ApplyPathResult.TRUE);
	}

	@Test
	public void testApplyPath_NonPathFilter_And_PathFilter_And_NonPathFilter_NoMatch() {
		String[] wrongPath = { "path3", "path4" };
		ChangedPathFilter cpfWithoutPath = cpf(wrongPath);

		TreeFilter apf = AndTreeFilter
				.create(new TreeFilter[] { TreeFilter.ANY_DIFF,
						PathFilter.create("path1"), TreeFilter.ANY_DIFF });
		assertEquals(apf.applyPath(cpfWithoutPath),
				TreeFilter.ApplyPathResult.FALSE);
	}

	@Test
	public void testApplyPath_PathFilter_And_NonPathFilter_And_PathFilter_NoMatch() {
		String[] path = { "path1", "path2" };
		ChangedPathFilter cpfContainModified = cpf(path);

		String[] wrongPath = { "path3", "path4" };
		ChangedPathFilter cpfWithoutPath = cpf(wrongPath);

		TreeFilter apf = AndTreeFilter
				.create(new TreeFilter[] { PathFilter.create("path1"),
						TreeFilter.ANY_DIFF, PathFilter.create("path2") });
		assertEquals(apf.applyPath(cpfContainModified),
				TreeFilter.ApplyPathResult.FALSE);
		assertEquals(apf.applyPath(cpfWithoutPath),
				TreeFilter.ApplyPathResult.FALSE);
	}

	@Test
	public void testApplyPath_PathFilter_And_PathFilter_And_PathFilter_NoMatch() {
		String[] path = { "path1", "path2", "path3" };
		ChangedPathFilter cpfContainModified = cpf(path);

		String[] wrongPath = { "path4" };
		ChangedPathFilter cpfWithoutPath = cpf(wrongPath);

		TreeFilter apf = AndTreeFilter.create(new TreeFilter[] {
				PathFilter.create("path1"), PathFilter.create("path2"),
				PathFilter.create("path3") });
		assertEquals(apf.applyPath(cpfContainModified),
				TreeFilter.ApplyPathResult.FALSE);
		assertEquals(apf.applyPath(cpfWithoutPath),
				TreeFilter.ApplyPathResult.FALSE);
	}

	@Test
	public void testApplyPath_NonPathFilter_And_NonPathFilter_And_NonPathFilter_NotApplicable() {
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
