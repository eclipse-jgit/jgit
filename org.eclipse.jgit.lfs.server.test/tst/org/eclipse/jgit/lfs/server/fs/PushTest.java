/*
 * Copyright (C) 2018, Markus Duft <markus.duft@ssi-schaefer.com>
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
package org.eclipse.jgit.lfs.server.fs;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.RemoteAddCommand;
import org.eclipse.jgit.junit.JGitTestUtil;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lfs.BuiltinLFS;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.jgit.util.IO;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class PushTest extends LfsServerTest {

	Git git;

	private TestRepository localDb;

	private Repository remoteDb;

	@Override
	@Before
	public void setup() throws Exception {
		super.setup();

		BuiltinLFS.register();

		Path rtmp = Files.createTempDirectory("jgit_test_");
		remoteDb = FileRepositoryBuilder.create(rtmp.toFile());
		remoteDb.create(true);

		Path tmp = Files.createTempDirectory("jgit_test_");
		Repository db = FileRepositoryBuilder
				.create(tmp.resolve(".git").toFile());
		db.create(false);
		StoredConfig cfg = db.getConfig();
		cfg.setString("filter", "lfs", "usejgitbuiltin", "true");
		cfg.setString("lfs", null, "url", server.getURI().toString() + "/lfs");
		cfg.save();

		localDb = new TestRepository<>(db);
		localDb.branch("master").commit().add(".gitattributes",
				"*.bin filter=lfs diff=lfs merge=lfs -text ").create();
		git = Git.wrap(db);

		URIish uri = new URIish(
				"file://" + remoteDb.getDirectory());
		RemoteAddCommand radd = git.remoteAdd();
		radd.setUri(uri);
		radd.setName(Constants.DEFAULT_REMOTE_NAME);
		radd.call();

		git.checkout().setName("master").call();
		git.push().call();
	}

	@After
	public void cleanup() throws Exception {
		remoteDb.close();
		localDb.getRepository().close();
		FileUtils.delete(localDb.getRepository().getWorkTree(),
				FileUtils.RECURSIVE);
		FileUtils.delete(remoteDb.getDirectory(), FileUtils.RECURSIVE);
	}

	@Test
	public void testPushSimple() throws Exception {
		JGitTestUtil.writeTrashFile(localDb.getRepository(), "a.bin",
				"1234567");
		git.add().addFilepattern("a.bin").call();
		RevCommit commit = git.commit().setMessage("add lfs blob").call();
		git.push().call();

		// check object in remote db, should be LFS pointer
		ObjectId id = commit.getId();
		try (RevWalk walk = new RevWalk(remoteDb)) {
			RevCommit rc = walk.parseCommit(id);
			try (TreeWalk tw = new TreeWalk(walk.getObjectReader())) {
				tw.addTree(rc.getTree());
				tw.setFilter(PathFilter.create("a.bin"));
				tw.next();

				assertEquals(tw.getPathString(), "a.bin");
				ObjectLoader ldr = walk.getObjectReader()
						.open(tw.getObjectId(0), Constants.OBJ_BLOB);
				try(InputStream is = ldr.openStream()) {
					assertEquals(
							"version https://git-lfs.github.com/spec/v1\noid sha256:8bb0cf6eb9b17d0f7d22b456f121257dc1254e1f01665370476383ea776df414\nsize 7\n",
							new String(IO
									.readWholeStream(is,
											(int) ldr.getSize())
									.array(), UTF_8));
				}
			}

		}

		assertEquals(
				"[POST /lfs/objects/batch 200, PUT /lfs/objects/8bb0cf6eb9b17d0f7d22b456f121257dc1254e1f01665370476383ea776df414 200]",
				server.getRequests().toString());
	}

}
