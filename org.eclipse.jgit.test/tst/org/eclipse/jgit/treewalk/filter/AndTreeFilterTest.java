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
	public void testCheckPath_NonPathFilter_PathFilter_Only_PathFilter_Matter() {
		String[] path = { "path1" };
		Set<ByteBuffer> b = Arrays.stream(path)
				.map(s -> ByteBuffer.wrap(Constants.encode(s)))
				.collect(Collectors.toSet());
		ChangedPathFilter cpfWithPath = ChangedPathFilter.fromPaths(b);

		String[] wrongPath = { "path3", "path4" };
		Set<ByteBuffer> b2 = Arrays.stream(wrongPath)
				.map(s -> ByteBuffer.wrap(Constants.encode(s)))
				.collect(Collectors.toSet());
		ChangedPathFilter cpfWithoutPath = ChangedPathFilter.fromPaths(b2);

		TreeFilter apf = AndTreeFilter.create(TreeFilter.ANY_DIFF,
				PathFilter.create("path1"));
		assertEquals(apf.checkPath(cpfWithPath),
				TreeFilter.ChangedPathFilterResponse.POSITIVE);
		assertEquals(apf.checkPath(cpfWithoutPath),
				TreeFilter.ChangedPathFilterResponse.NEGATIVE);
	}

	@Test
	public void testCheckPath_PathFilter_PathFilter_Always_Negative() {
		String[] path = { "path1" };
		Set<ByteBuffer> b = Arrays.stream(path)
				.map(s -> ByteBuffer.wrap(Constants.encode(s)))
				.collect(Collectors.toSet());
		ChangedPathFilter cpfWithPath = ChangedPathFilter.fromPaths(b);

		TreeFilter apf = AndTreeFilter.create(PathFilter.create("path1"),
				PathFilter.create("path2"));
		assertEquals(apf.checkPath(cpfWithPath),
				TreeFilter.ChangedPathFilterResponse.NEGATIVE);
	}

	@Test
	public void testCheckPath_NonPathFilter_NonPathFilter_Always_No_Path() {
		String[] path = { "path1" };
		Set<ByteBuffer> b = Arrays.stream(path)
				.map(s -> ByteBuffer.wrap(Constants.encode(s)))
				.collect(Collectors.toSet());
		ChangedPathFilter cpfWithPath = ChangedPathFilter.fromPaths(b);

		TreeFilter apf = AndTreeFilter.create(TreeFilter.ANY_DIFF,
				TreeFilter.ANY_DIFF);
		assertEquals(apf.checkPath(cpfWithPath),
				TreeFilter.ChangedPathFilterResponse.NO_PATH);
	}

	@Test
	public void testCheckPath_NonPathFilter_PathFilter_NonPathFilter_Only_PathFilter_Matter() {
		String[] path = { "path1" };
		Set<ByteBuffer> b = Arrays.stream(path)
				.map(s -> ByteBuffer.wrap(Constants.encode(s)))
				.collect(Collectors.toSet());
		ChangedPathFilter cpfWithPath = ChangedPathFilter.fromPaths(b);

		String[] wrongPath = { "path3", "path4" };
		Set<ByteBuffer> b2 = Arrays.stream(wrongPath)
				.map(s -> ByteBuffer.wrap(Constants.encode(s)))
				.collect(Collectors.toSet());
		ChangedPathFilter cpfWithoutPath = ChangedPathFilter.fromPaths(b2);

		TreeFilter apf = AndTreeFilter
				.create(new TreeFilter[] { TreeFilter.ANY_DIFF,
						PathFilter.create("path1"), TreeFilter.ANY_DIFF });
		assertEquals(apf.checkPath(cpfWithPath),
				TreeFilter.ChangedPathFilterResponse.POSITIVE);
		assertEquals(apf.checkPath(cpfWithoutPath),
				TreeFilter.ChangedPathFilterResponse.NEGATIVE);
	}

	@Test
	public void testCheckPath_PathFilter_NonPathFilter_PathFilter_Always_Negative() {
		String[] path = { "path1", "path2" };
		Set<ByteBuffer> b = Arrays.stream(path)
				.map(s -> ByteBuffer.wrap(Constants.encode(s)))
				.collect(Collectors.toSet());
		ChangedPathFilter cpfWithPath = ChangedPathFilter.fromPaths(b);

		String[] wrongPath = { "path3", "path4" };
		Set<ByteBuffer> b2 = Arrays.stream(wrongPath)
				.map(s -> ByteBuffer.wrap(Constants.encode(s)))
				.collect(Collectors.toSet());
		ChangedPathFilter cpfWithoutPath = ChangedPathFilter.fromPaths(b2);

		TreeFilter apf = AndTreeFilter
				.create(new TreeFilter[] { PathFilter.create("path1"),
						TreeFilter.ANY_DIFF, PathFilter.create("path2") });
		assertEquals(apf.checkPath(cpfWithPath),
				TreeFilter.ChangedPathFilterResponse.NEGATIVE);
		assertEquals(apf.checkPath(cpfWithoutPath),
				TreeFilter.ChangedPathFilterResponse.NEGATIVE);
	}

	@Test
	public void testCheckPath_PathFilter_PathFilter_PathFilter_Always_Negative() {
		String[] path = { "path1", "path2", "path3" };
		Set<ByteBuffer> b = Arrays.stream(path)
				.map(s -> ByteBuffer.wrap(Constants.encode(s)))
				.collect(Collectors.toSet());
		ChangedPathFilter cpfWithPath = ChangedPathFilter.fromPaths(b);

		String[] wrongPath = { "path4" };
		Set<ByteBuffer> b2 = Arrays.stream(wrongPath)
				.map(s -> ByteBuffer.wrap(Constants.encode(s)))
				.collect(Collectors.toSet());
		ChangedPathFilter cpfWithoutPath = ChangedPathFilter.fromPaths(b2);

		TreeFilter apf = AndTreeFilter.create(new TreeFilter[] {
				PathFilter.create("path1"), PathFilter.create("path2"),
				PathFilter.create("path3") });
		assertEquals(apf.checkPath(cpfWithPath),
				TreeFilter.ChangedPathFilterResponse.NEGATIVE);
		assertEquals(apf.checkPath(cpfWithoutPath),
				TreeFilter.ChangedPathFilterResponse.NEGATIVE);
	}

	@Test
	public void testCheckPath_NonPathFilter_NonPathFilter_NonPathFilter_Always_No_Path() {
		String[] path = { "path1" };
		Set<ByteBuffer> b = Arrays.stream(path)
				.map(s -> ByteBuffer.wrap(Constants.encode(s)))
				.collect(Collectors.toSet());
		ChangedPathFilter cpfWithPath = ChangedPathFilter.fromPaths(b);

		TreeFilter apf = AndTreeFilter
				.create(new TreeFilter[] { TreeFilter.ANY_DIFF,
						TreeFilter.ANY_DIFF, TreeFilter.ANY_DIFF });
		assertEquals(apf.checkPath(cpfWithPath),
				TreeFilter.ChangedPathFilterResponse.NO_PATH);
	}
}
