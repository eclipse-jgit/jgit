/*
 * Copyright (C) 2013, CloudBees, Inc.
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
package org.eclipse.jgit.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.errors.InvalidPatternException;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DescribeCommandTest extends RepositoryTestCase {

	private Git git;

	@Parameter
	public boolean useAnnotatedTags;

	@Parameters
	public static Collection<Boolean[]> getUseAnnotatedTagsValues() {
		return Arrays.asList(new Boolean[][] { { Boolean.TRUE },
				{ Boolean.FALSE } });
	}

	@Override
	public void setUp() throws Exception {
		super.setUp();
		git = new Git(db);
	}

	@Test(expected = RefNotFoundException.class)
	public void noTargetSet() throws Exception {
		git.describe().call();
	}

	@Test
	public void testDescribe() throws Exception {
		ObjectId c1 = modify("aaa");

		ObjectId c2 = modify("bbb");
		tag("alice-t1");

		ObjectId c3 = modify("ccc");
		tag("bob-t2");

		ObjectId c4 = modify("ddd");

		assertNull(describe(c1));
		assertNull(describe(c1, true));
		assertNull(describe(c1, "a*", "b*", "c*"));

		assertEquals("alice-t1", describe(c2));
		assertEquals("alice-t1", describe(c2, "alice*"));
		assertNull(describe(c2, "bob*"));
		assertNull(describe(c2, "?ob*"));
		assertEquals("alice-t1", describe(c2, "a*", "b*", "c*"));

		assertEquals("bob-t2", describe(c3));
		assertEquals("bob-t2-0-g44579eb", describe(c3, true));
		assertEquals("alice-t1-1-g44579eb", describe(c3, "alice*"));
		assertEquals("alice-t1-1-g44579eb", describe(c3, "a??c?-t*"));
		assertEquals("bob-t2", describe(c3, "bob*"));
		assertEquals("bob-t2", describe(c3, "?ob*"));
		assertEquals("bob-t2", describe(c3, "a*", "b*", "c*"));

		assertNameStartsWith(c4, "3e563c5");
		// the value verified with git-describe(1)
		assertEquals("bob-t2-1-g3e563c5", describe(c4));
		assertEquals("bob-t2-1-g3e563c5", describe(c4, true));
		assertEquals("alice-t1-2-g3e563c5", describe(c4, "alice*"));
		assertEquals("bob-t2-1-g3e563c5", describe(c4, "bob*"));
		assertEquals("bob-t2-1-g3e563c5", describe(c4, "a*", "b*", "c*"));

		// test default target
		assertEquals("bob-t2-1-g3e563c5", git.describe().call());
	}

	@Test
	public void testDescribeMultiMatch() throws Exception {
		ObjectId c1 = modify("aaa");
		tag("v1.0.0");
		tag("v1.1.1");
		ObjectId c2 = modify("bbb");

		// Ensure that if we're interested in any tags, we get the first match as per Git behaviour
		assertEquals("v1.0.0", describe(c1));
		assertEquals("v1.0.0-1-g3747db3", describe(c2));

		// Ensure that if we're only interested in one of multiple tags, we get the right match
		assertEquals("v1.0.0", describe(c1, "v1.0*"));
		assertEquals("v1.1.1", describe(c1, "v1.1*"));
		assertEquals("v1.0.0-1-g3747db3", describe(c2, "v1.0*"));
		assertEquals("v1.1.1-1-g3747db3", describe(c2, "v1.1*"));

		// Ensure that ordering of match precedence is preserved as per Git behaviour
		assertEquals("v1.0.0", describe(c1, "v1.0*", "v1.1*"));
		assertEquals("v1.1.1", describe(c1, "v1.1*", "v1.0*"));
		assertEquals("v1.0.0-1-g3747db3", describe(c2, "v1.0*", "v1.1*"));
		assertEquals("v1.1.1-1-g3747db3", describe(c2, "v1.1*", "v1.0*"));
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
	@Test
	public void testDescribeBranch() throws Exception {
		ObjectId c1 = modify("aaa");

		ObjectId c2 = modify("bbb");
		tag("t");

		branch("b", c1);

		ObjectId c3 = modify("ccc");

		ObjectId c4 = merge(c2);

		assertNameStartsWith(c4, "119892b");
		assertEquals("t-2-g119892b", describe(c4)); // 2 commits: c4 and c3
		assertNull(describe(c3));
		assertNull(describe(c3, true));
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
	@Test
	public void t1DominatesT2() throws Exception {
		ObjectId c1 = modify("aaa");
		tag("t1");

		ObjectId c2 = modify("bbb");
		tag("t2");

		branch("b", c1);

		ObjectId c3 = modify("ccc");

		ObjectId c4 = merge(c2);

		assertNameStartsWith(c4, "119892b");
		assertEquals("t2-2-g119892b", describe(c4)); // 2 commits: c4 and c3

		assertNameStartsWith(c3, "0244e7f");
		assertEquals("t1-1-g0244e7f", describe(c3));
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
	@Test
	public void t1nearerT2() throws Exception {
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
		assertEquals("t1-3-gbb389a4", describe(c4));
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
	@Test
	public void t1sameDepthT2() throws Exception {
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
		assertEquals("t2-4-gbb389a4", describe(c4));
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
		TagCommand tagCommand = git.tag().setName(tag)
				.setAnnotated(useAnnotatedTags);
		if (useAnnotatedTags)
			tagCommand.setMessage(tag);
		tagCommand.call();
	}

	private static void touch(File f, String contents) throws Exception {
		try (FileWriter w = new FileWriter(f)) {
			w.write(contents);
		}
	}

	private String describe(ObjectId c1, boolean longDesc)
			throws GitAPIException, IOException {
		return git.describe().setTarget(c1).setLong(longDesc).call();
	}

	private String describe(ObjectId c1) throws GitAPIException, IOException {
		return describe(c1, false);
	}

	private String describe(ObjectId c1, String... patterns) throws GitAPIException, IOException, InvalidPatternException {
		return git.describe().setTarget(c1).setMatch(patterns).call();
	}

	private static void assertNameStartsWith(ObjectId c4, String prefix) {
		assertTrue(c4.name(), c4.name().startsWith(prefix));
	}
}
