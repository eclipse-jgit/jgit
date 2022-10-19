/*
 * Copyright (C) 2013, CloudBees, Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.api;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.lib.Constants.OBJECT_ID_ABBREV_STRING_LENGTH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collection;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class DescribeCommandTest extends RepositoryTestCase {

	private Git git;
	public boolean useAnnotatedTags;
	public boolean describeUseAllTags;

	private static Collection<Boolean[]> getUseAnnotatedTagsValues() {
		return Arrays.asList(new Boolean[][]{{Boolean.TRUE, Boolean.FALSE},
				{Boolean.FALSE, Boolean.FALSE},
				{Boolean.TRUE, Boolean.TRUE},
				{Boolean.FALSE, Boolean.TRUE}});
	}

	@Override
	@BeforeEach
	public void setUp() throws Exception {
		super.setUp();
		git = new Git(db);
	}

	@MethodSource("getUseAnnotatedTagsValues")
	@ParameterizedTest(name = "git tag -a {0}?-a: with git describe {1}?--tags:")
	void noTargetSet(boolean useAnnotatedTags, boolean describeUseAllTags) throws Exception {
		initDescribeCommandTest(useAnnotatedTags, describeUseAllTags);
		assertThrows(RefNotFoundException.class, () -> {
			git.describe().call();
		});
	}

	@MethodSource("getUseAnnotatedTagsValues")
	@ParameterizedTest(name = "git tag -a {0}?-a: with git describe {1}?--tags:")
	void testDescribe(boolean useAnnotatedTags, boolean describeUseAllTags) throws Exception {
		initDescribeCommandTest(useAnnotatedTags, describeUseAllTags);
		ObjectId c1 = modify("aaa");

		ObjectId c2 = modify("bbb");
		tag("alice-t1");

		ObjectId c3 = modify("ccc");
		tag("bob-t2");

		ObjectId c4 = modify("ddd");
		assertNameStartsWith(c4, "3e563c5");

		assertNull(describe(c1));
		assertNull(describe(c1, true, false));
		assertNull(describe(c1, "a*", "b*", "c*"));
		assertNull(describe(c2, "bob*"));
		assertNull(describe(c2, "?ob*"));

		if (useAnnotatedTags || describeUseAllTags) {
			assertEquals("alice-t1", describe(c2));
			assertEquals("alice-t1", describe(c2, "alice*"));
			assertEquals("alice-t1", describe(c2, "a*", "b*", "c*"));

			assertEquals("bob-t2", describe(c3));
			assertEquals("bob-t2-0-g44579eb", describe(c3, true, false));
			assertEquals("alice-t1-1-g44579eb", describe(c3, "alice*"));
			assertEquals("alice-t1-1-g44579eb", describe(c3, "a??c?-t*"));
			assertEquals("bob-t2", describe(c3, "bob*"));
			assertEquals("bob-t2", describe(c3, "?ob*"));
			assertEquals("bob-t2", describe(c3, "a*", "b*", "c*"));

			// the value verified with git-describe(1)
			assertEquals("bob-t2-1-g3e563c5", describe(c4));
			assertEquals("bob-t2-1-g3e563c5", describe(c4, true, false));
			assertEquals("alice-t1-2-g3e563c5", describe(c4, "alice*"));
			assertEquals("bob-t2-1-g3e563c5", describe(c4, "bob*"));
			assertEquals("bob-t2-1-g3e563c5", describe(c4, "a*", "b*", "c*"));

			assertEquals("bob-t2", describe(c4, false, true, 0));
			assertEquals("bob-t2-1-g3e56", describe(c4, false, true, 1));
			assertEquals("bob-t2-1-g3e56", describe(c4, false, true, -10));
			assertEquals("bob-t2-1-g3e563c55927905f21e3bc7c00a3d83a31bf4ed3a",
					describe(c4, false, true, 50));
		} else {
			assertNull(describe(c2));
			assertNull(describe(c3));
			assertNull(describe(c4));

			assertEquals("3747db3", describe(c2, false, true));
			assertEquals("44579eb", describe(c3, false, true));
			assertEquals("3e563c5", describe(c4, false, true));

			assertEquals("3747db3267", describe(c2, false, true, 10));
			assertEquals("44579ebe7f", describe(c3, false, true, 10));
			assertEquals("3e563c5592", describe(c4, false, true, 10));

			assertEquals("3e56", describe(c4, false, true, -10));
			assertEquals("3e56", describe(c4, false, true, 0));
			assertEquals("3e56", describe(c4, false, true, 2));
			assertEquals("3e563c55927905f21e3bc7c00a3d83a31bf4ed3a",
					describe(c4, false, true, 40));
			assertEquals("3e563c55927905f21e3bc7c00a3d83a31bf4ed3a",
					describe(c4, false, true, 42));
		}

		// test default target
		if (useAnnotatedTags) {
			assertEquals("bob-t2-1-g3e563c5", git.describe().call());
			assertEquals("bob-t2-1-g3e563c5",
					git.describe().setTags(false).call());
			assertEquals("bob-t2-1-g3e563c5",
					git.describe().setTags(true).call());
		} else {
			assertNull(git.describe().call());
			assertNull(git.describe().setTags(false).call());
			assertEquals("bob-t2-1-g3e563c5",
					git.describe().setTags(true).call());
		}
	}

	@MethodSource("getUseAnnotatedTagsValues")
	@ParameterizedTest(name = "git tag -a {0}?-a: with git describe {1}?--tags:")
	void testDescribeMultiMatch(boolean useAnnotatedTags, boolean describeUseAllTags) throws Exception {
		initDescribeCommandTest(useAnnotatedTags, describeUseAllTags);
		ObjectId c1 = modify("aaa");
		tag("v1.0.0");
		tick();
		tag("v1.0.1");
		tick();
		tag("v1.1.0");
		tick();
		tag("v1.1.1");
		ObjectId c2 = modify("bbb");

		if (!useAnnotatedTags && !describeUseAllTags) {
			assertNull(describe(c1));
			assertNull(describe(c2));

			assertEquals("fd70040", describe(c1, false, true));
			assertEquals("b89dead", describe(c2, false, true));

			return;
		}

		// Ensure that if we're interested in any tags, we get the most recent tag
		// as per Git behaviour since 1.7.1.1
		if (useAnnotatedTags) {
			assertEquals("v1.1.1", describe(c1));
			assertEquals("v1.1.1-1-gb89dead", describe(c2));
			// Ensure that if we're only interested in one of multiple tags, we get the right match
			assertEquals("v1.0.1", describe(c1, "v1.0*"));
			assertEquals("v1.1.1", describe(c1, "v1.1*"));
			assertEquals("v1.0.1-1-gb89dead", describe(c2, "v1.0*"));
			assertEquals("v1.1.1-1-gb89dead", describe(c2, "v1.1*"));

			// Ensure that ordering of match precedence is preserved as per Git behaviour
			assertEquals("v1.1.1", describe(c1, "v1.0*", "v1.1*"));
			assertEquals("v1.1.1", describe(c1, "v1.1*", "v1.0*"));
			assertEquals("v1.1.1-1-gb89dead", describe(c2, "v1.0*", "v1.1*"));
			assertEquals("v1.1.1-1-gb89dead", describe(c2, "v1.1*", "v1.0*"));
		} else {
			// no timestamps so no guarantees on which tag is chosen
			assertNotNull(describe(c1));
			assertNotNull(describe(c2));

			assertNotNull(describe(c1, "v1.0*"));
			assertNotNull(describe(c1, "v1.1*"));
			assertNotNull(describe(c2, "v1.0*"));
			assertNotNull(describe(c2, "v1.1*"));

			// Ensure that ordering of match precedence is preserved as per Git behaviour
			assertNotNull(describe(c1, "v1.0*", "v1.1*"));
			assertNotNull(describe(c1, "v1.1*", "v1.0*"));
			assertNotNull(describe(c2, "v1.0*", "v1.1*"));
			assertNotNull(describe(c2, "v1.1*", "v1.0*"));
		}
	}

	/**
	 * Make sure it finds a tag when not all ancestries include a tag.
	 *
	 * <pre>
	 * c1 -+-&gt; T  -
	 *     |       |
	 *     +-&gt; c3 -+-&gt; c4
	 * </pre>
	 *
	 * @throws Exception
	 */
	@MethodSource("getUseAnnotatedTagsValues")
	@ParameterizedTest(name = "git tag -a {0}?-a: with git describe {1}?--tags:")
	void testDescribeBranch(boolean useAnnotatedTags, boolean describeUseAllTags) throws Exception {
		initDescribeCommandTest(useAnnotatedTags, describeUseAllTags);
		ObjectId c1 = modify("aaa");

		ObjectId c2 = modify("bbb");
		tag("t");

		branch("b", c1);

		ObjectId c3 = modify("ccc");

		ObjectId c4 = merge(c2);

		assertNameStartsWith(c4, "119892b");
		if (useAnnotatedTags || describeUseAllTags) {
			assertEquals("t-2-g119892b", describe(c4), "2 commits: c4 and c3");
		} else {
			assertNull(describe(c4));

			assertEquals("119892b", describe(c4, false, true));
		}
		assertNull(describe(c3));
		assertNull(describe(c3, true, false));
	}

	private void branch(String name, ObjectId base) throws GitAPIException {
		git.checkout().setCreateBranch(true).setName(name)
				.setStartPoint(base.name()).call();
	}

	/**
	 * When t2 dominates t1, it's clearly preferable to describe by using t2.
	 *
	 * <pre>
	 * t1 -+-&gt; t2  -
	 *     |       |
	 *     +-&gt; c3 -+-&gt; c4
	 * </pre>
	 *
	 * @throws Exception
	 */
	@MethodSource("getUseAnnotatedTagsValues")
	@ParameterizedTest(name = "git tag -a {0}?-a: with git describe {1}?--tags:")
	void t1DominatesT2(boolean useAnnotatedTags, boolean describeUseAllTags) throws Exception {
		initDescribeCommandTest(useAnnotatedTags, describeUseAllTags);
		ObjectId c1 = modify("aaa");
		tag("t1");

		ObjectId c2 = modify("bbb");
		tag("t2");

		branch("b", c1);

		ObjectId c3 = modify("ccc");
		assertNameStartsWith(c3, "0244e7f");

		ObjectId c4 = merge(c2);

		assertNameStartsWith(c4, "119892b");

		if (useAnnotatedTags || describeUseAllTags) {
			assertEquals("t2-2-g119892b", describe(c4)); // 2 commits: c4 and c3
			assertEquals("t1-1-g0244e7f", describe(c3));
		} else {
			assertNull(describe(c4));
			assertNull(describe(c3));

			assertEquals("119892b", describe(c4, false, true));
			assertEquals("0244e7f", describe(c3, false, true));
		}
	}

	/**
	 * When t1 annotated dominates t2 lightweight tag
	 *
	 * <pre>
	 * t1 -+-> t2  -
	 *     |       |
	 *     +-> c3 -+-> c4
	 * </pre>
	 *
	 * @throws Exception
	 */
	@MethodSource("getUseAnnotatedTagsValues")
	@ParameterizedTest(name = "git tag -a {0}?-a: with git describe {1}?--tags:")
	void t1AnnotatedDominatesT2lightweight(boolean useAnnotatedTags, boolean describeUseAllTags) throws Exception {
		initDescribeCommandTest(useAnnotatedTags, describeUseAllTags);
		ObjectId c1 = modify("aaa");
		tag("t1", useAnnotatedTags);

		ObjectId c2 = modify("bbb");
		tag("t2", false);

		assertNameStartsWith(c2, "3747db3");
		if (useAnnotatedTags && !describeUseAllTags) {
			assertEquals(
					"t1-1-g3747db3", describe(c2), "only annotated tag t1 expected to be used for describe"); // 1 commits: t2 overridden
			// by t1
		} else if (!useAnnotatedTags && !describeUseAllTags) {
			assertNull(describe(c2), "no commits to describe expected");
		} else {
			assertEquals("t2",
					describe(c2),
					"lightweight tag t2 expected in describe");
		}

		branch("b", c1);

		ObjectId c3 = modify("ccc");

		assertNameStartsWith(c3, "0244e7f");
		if (useAnnotatedTags || describeUseAllTags) {
			assertEquals("t1-1-g0244e7f", describe(c3));
		}

		ObjectId c4 = merge(c2);

		assertNameStartsWith(c4, "119892b");
		if (describeUseAllTags) {
			assertEquals(
					"t2-2-g119892b", describe(c4), "2 commits for describe commit increment expected since lightweight tag: c4 and c3"); // 2 commits: c4 and c3
		} else if (!useAnnotatedTags) {
			assertNull(describe(c4), "no matching commits expected");
		} else {
			assertEquals(
					"t1-3-g119892b", describe(c4), "3 commits for describe commit increment expected since annotated tag: c4 and c3 and c2"); //
		}
	}

	/**
	 * When t1 is nearer than t2, t2 should be found
	 *
	 * <pre>
	 * c1 -+-&gt; c2 -&gt; t1 -+
	 *     |             |
	 *     +-&gt; t2 -&gt; c3 -+-&gt; c4
	 * </pre>
	 *
	 * @throws Exception
	 */
	@MethodSource("getUseAnnotatedTagsValues")
	@ParameterizedTest(name = "git tag -a {0}?-a: with git describe {1}?--tags:")
	void t1nearerT2(boolean useAnnotatedTags, boolean describeUseAllTags) throws Exception {
		initDescribeCommandTest(useAnnotatedTags, describeUseAllTags);
		ObjectId c1 = modify("aaa");
		modify("bbb");
		ObjectId t1 = modify("ccc");
		tag("t1");

		branch("b", c1);
		modify("ddd");
		tag("t2");
		modify("eee");
		ObjectId c4 = merge(t1);

		assertNameStartsWith(c4, "bb389a4");
		if (useAnnotatedTags || describeUseAllTags) {
			assertEquals("t1-3-gbb389a4", describe(c4));
		} else {
			assertNull(describe(c4));

			assertEquals("bb389a4", describe(c4, false, true));
		}
	}

	/**
	 * When t1 and t2 have same depth native git seems to add the depths of both
	 * paths
	 *
	 * <pre>
	 * c1 -+-&gt; t1 -&gt; c2 -+
	 *     |             |
	 *     +-&gt; t2 -&gt; c3 -+-&gt; c4
	 * </pre>
	 *
	 * @throws Exception
	 */
	@MethodSource("getUseAnnotatedTagsValues")
	@ParameterizedTest(name = "git tag -a {0}?-a: with git describe {1}?--tags:")
	void t1sameDepthT2(boolean useAnnotatedTags, boolean describeUseAllTags) throws Exception {
		initDescribeCommandTest(useAnnotatedTags, describeUseAllTags);
		ObjectId c1 = modify("aaa");
		modify("bbb");
		tag("t1");
		ObjectId c2 = modify("ccc");

		branch("b", c1);
		modify("ddd");
		tag("t2");
		modify("eee");
		ObjectId c4 = merge(c2);

		assertNameStartsWith(c4, "bb389a4");
		if (useAnnotatedTags || describeUseAllTags) {
			assertEquals("t2-4-gbb389a4", describe(c4));
		} else {
			assertNull(describe(c4));

			assertEquals("bb389a4", describe(c4, false, true));
		}
	}

	@MethodSource("getUseAnnotatedTagsValues")
	@ParameterizedTest(name = "git tag -a {0}?-a: with git describe {1}?--tags:")
	void globMatchWithSlashes(boolean useAnnotatedTags, boolean describeUseAllTags) throws Exception {
		initDescribeCommandTest(useAnnotatedTags, describeUseAllTags);
		ObjectId c1 = modify("aaa");
		tag("a/b/version");
		ObjectId c2 = modify("bbb");
		tag("a/b/version2");
		if (useAnnotatedTags || describeUseAllTags) {
			assertEquals("a/b/version", describe(c1, "*/version*"));
			assertEquals("a/b/version2", describe(c2, "*/version*"));
		} else {
			assertNull(describe(c1));
			assertNull(describe(c1, "*/version*"));
			assertNull(describe(c2));
			assertNull(describe(c2, "*/version*"));
		}
	}

	@MethodSource("getUseAnnotatedTagsValues")
	@ParameterizedTest(name = "git tag -a {0}?-a: with git describe {1}?--tags:")
	void testDescribeUseAllRefsMaster(boolean useAnnotatedTags, boolean describeUseAllTags) throws Exception {
		initDescribeCommandTest(useAnnotatedTags, describeUseAllTags);
		final ObjectId c1 = modify("aaa");
		tag("t1");

		if (useAnnotatedTags || describeUseAllTags) {
			assertEquals("t1", describe(c1));
		} else {
			assertNull(describe(c1));
		}
		assertEquals("heads/master", describeAll(c1));
	}

	/**
	 * Branch off from master and then tag
	 *
	 * <pre>
	 * c1 -+ -> c2
	 *     |
	 *     +-> t1
	 * </pre>
	 * @throws Exception
	 * */
	@MethodSource("getUseAnnotatedTagsValues")
	@ParameterizedTest(name = "git tag -a {0}?-a: with git describe {1}?--tags:")
	void testDescribeUseAllRefsBranch(boolean useAnnotatedTags, boolean describeUseAllTags) throws Exception {
		initDescribeCommandTest(useAnnotatedTags, describeUseAllTags);
		final ObjectId c1 = modify("aaa");
		modify("bbb");

		branch("b", c1);
		final ObjectId c3 = modify("ccc");
		tag("t1");

		if (!useAnnotatedTags && !describeUseAllTags) {
			assertNull(describe(c3));
		} else {
			assertEquals("t1", describe(c3));
		}
		assertEquals("heads/b", describeAll(c3));
	}

	private ObjectId merge(ObjectId c2) throws GitAPIException {
		return git.merge().include(c2).call().getNewHead();
	}

	private ObjectId modify(String content) throws Exception {
		File a = new File(db.getWorkTree(), "a.txt");
		touch(a, content);
		return git.commit().setAll(true).setMessage(content).call().getId();
	}

	private void tag(String tag) throws GitAPIException {
		tag(tag, this.useAnnotatedTags);
	}

	private void tag(String tag, boolean annotatedTag) throws GitAPIException {
		TagCommand tagCommand = git.tag().setName(tag)
				.setAnnotated(annotatedTag);
		if (annotatedTag) {
			tagCommand.setMessage(tag);
		}
		tagCommand.call();
	}

	private static void touch(File f, String contents) throws Exception {
		try (BufferedWriter w = Files.newBufferedWriter(f.toPath(), UTF_8)) {
			w.write(contents);
		}
	}

	private String describe(ObjectId c1, boolean longDesc, boolean always,
			int abbrev) throws GitAPIException, IOException {
		return git.describe().setTarget(c1).setTags(describeUseAllTags)
				.setLong(longDesc).setAlways(always).setAbbrev(abbrev).call();
	}

	private String describe(ObjectId c1, boolean longDesc, boolean always)
			throws GitAPIException, IOException {
		return describe(c1, longDesc, always, OBJECT_ID_ABBREV_STRING_LENGTH);
	}

	private String describe(ObjectId c1) throws GitAPIException, IOException {
		return describe(c1, false, false);
	}

	private String describeAll(ObjectId c1) throws GitAPIException, IOException {
		return git.describe().setTarget(c1).setTags(describeUseAllTags)
				.setLong(false).setAlways(false).setAll(true).call();
	}

	private String describe(ObjectId c1, String... patterns) throws Exception {
		return git.describe().setTarget(c1).setTags(describeUseAllTags)
				.setMatch(patterns).call();
	}

	private static void assertNameStartsWith(ObjectId c4, String prefix) {
		assertTrue(c4.name().startsWith(prefix), c4.name());
	}

	public void initDescribeCommandTest(boolean useAnnotatedTags, boolean describeUseAllTags) {
		this.useAnnotatedTags = useAnnotatedTags;
		this.describeUseAllTags = describeUseAllTags;
	}
}
