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
	public void testCheckPath_NonPathFilter_PathFilter_Always_Positive() {
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

		TreeFilter opf = OrTreeFilter.create(TreeFilter.ANY_DIFF,
				PathFilter.create("path1"));
		assertEquals(opf.checkPath(cpfWithPath),
				TreeFilter.ChangedPathFilterResponse.POSITIVE);
		assertEquals(opf.checkPath(cpfWithoutPath),
				TreeFilter.ChangedPathFilterResponse.POSITIVE);
	}

	@Test
	public void testCheckPath_PathFilter_PathFilter_Either_Match() {
		String[] path = { "path1" };
		Set<ByteBuffer> b = Arrays.stream(path)
				.map(s -> ByteBuffer.wrap(Constants.encode(s)))
				.collect(Collectors.toSet());
		ChangedPathFilter cpfWithPath = ChangedPathFilter.fromPaths(b);

		String[] path2 = { "path2" };
		Set<ByteBuffer> b2 = Arrays.stream(path2)
				.map(s -> ByteBuffer.wrap(Constants.encode(s)))
				.collect(Collectors.toSet());
		ChangedPathFilter cpfWithPath2 = ChangedPathFilter.fromPaths(b2);

		String[] path3 = { "path4" };
		Set<ByteBuffer> b3 = Arrays.stream(path3)
				.map(s -> ByteBuffer.wrap(Constants.encode(s)))
				.collect(Collectors.toSet());
		ChangedPathFilter cpfWithPath3 = ChangedPathFilter.fromPaths(b3);

		TreeFilter opf = OrTreeFilter.create(PathFilter.create("path1"),
				PathFilter.create("path2"));
		assertEquals(opf.checkPath(cpfWithPath),
				TreeFilter.ChangedPathFilterResponse.POSITIVE);
		assertEquals(opf.checkPath(cpfWithPath2),
				TreeFilter.ChangedPathFilterResponse.POSITIVE);
		assertEquals(opf.checkPath(cpfWithPath3),
				TreeFilter.ChangedPathFilterResponse.NEGATIVE);
	}

	@Test
	public void testCheckPath_NonPathFilter_NonPathFilter_Always_No_Path() {
		String[] path = { "path1" };
		Set<ByteBuffer> b = Arrays.stream(path)
				.map(s -> ByteBuffer.wrap(Constants.encode(s)))
				.collect(Collectors.toSet());
		ChangedPathFilter cpfWithPath = ChangedPathFilter.fromPaths(b);

		TreeFilter opf = OrTreeFilter.create(TreeFilter.ANY_DIFF,
				TreeFilter.ANY_DIFF);
		assertEquals(opf.checkPath(cpfWithPath),
				TreeFilter.ChangedPathFilterResponse.NO_PATH);
	}

	@Test
	public void testCheckPath_NonPathFilter_PathFilter_NonPathFilter_Always_Positive() {
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

		TreeFilter opf = OrTreeFilter
				.create(new TreeFilter[] { TreeFilter.ANY_DIFF,
						PathFilter.create("path1"), TreeFilter.ANY_DIFF });
		assertEquals(opf.checkPath(cpfWithPath),
				TreeFilter.ChangedPathFilterResponse.POSITIVE);
		assertEquals(opf.checkPath(cpfWithoutPath),
				TreeFilter.ChangedPathFilterResponse.POSITIVE);
	}

	@Test
	public void testCheckPath_PathFilter_NonPathFilter_PathFilter_Always_Positive() {
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

		TreeFilter opf = OrTreeFilter
				.create(new TreeFilter[] { PathFilter.create("path1"),
						TreeFilter.ANY_DIFF, PathFilter.create("path2") });
		assertEquals(opf.checkPath(cpfWithPath),
				TreeFilter.ChangedPathFilterResponse.POSITIVE);
		assertEquals(opf.checkPath(cpfWithoutPath),
				TreeFilter.ChangedPathFilterResponse.POSITIVE);
	}

	@Test
	public void testCheckPath_PathFilter_PathFilter_PathFilter_Either_Match() {

		String[] path = { "path1" };
		Set<ByteBuffer> b = Arrays.stream(path)
				.map(s -> ByteBuffer.wrap(Constants.encode(s)))
				.collect(Collectors.toSet());
		ChangedPathFilter cpfWithPath = ChangedPathFilter.fromPaths(b);

		String[] path2 = { "path2" };
		Set<ByteBuffer> b2 = Arrays.stream(path2)
				.map(s -> ByteBuffer.wrap(Constants.encode(s)))
				.collect(Collectors.toSet());
		ChangedPathFilter cpfWithPath2 = ChangedPathFilter.fromPaths(b2);

		String[] path3 = { "path3" };
		Set<ByteBuffer> b3 = Arrays.stream(path3)
				.map(s -> ByteBuffer.wrap(Constants.encode(s)))
				.collect(Collectors.toSet());
		ChangedPathFilter cpfWithPath3 = ChangedPathFilter.fromPaths(b3);

		String[] path4 = { "path4" };
		Set<ByteBuffer> b4 = Arrays.stream(path4)
				.map(s -> ByteBuffer.wrap(Constants.encode(s)))
				.collect(Collectors.toSet());
		ChangedPathFilter cpfWithoutPath = ChangedPathFilter.fromPaths(b4);

		TreeFilter opf = OrTreeFilter.create(new TreeFilter[] {
				PathFilter.create("path1"), PathFilter.create("path2"),
				PathFilter.create("path3") });
		assertEquals(opf.checkPath(cpfWithPath),
				TreeFilter.ChangedPathFilterResponse.POSITIVE);
		assertEquals(opf.checkPath(cpfWithPath2),
				TreeFilter.ChangedPathFilterResponse.POSITIVE);
		assertEquals(opf.checkPath(cpfWithPath3),
				TreeFilter.ChangedPathFilterResponse.POSITIVE);
		assertEquals(opf.checkPath(cpfWithoutPath),
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
