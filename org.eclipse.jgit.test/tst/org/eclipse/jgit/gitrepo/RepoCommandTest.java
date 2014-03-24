/*
 * Copyright (C) 2011, 2013 Chris Aniszczyk <caniszczyk@gmail.com>
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
package org.eclipse.jgit.gitrepo;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.junit.JGitTestUtil;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.Repository;
import org.junit.Test;

public class RepoCommandTest extends RepositoryTestCase {

	private Git defaultGit;
	private Git notDefaultGit;
	private Git groupAGit;
	private Git groupBGit;

	private String rootUri;
	private String defaultUri;
	private String notDefaultUri;
	private String groupAUri;
	private String groupBUri;

	public void setUp() throws Exception {
		super.setUp();

		Repository remoteDb = createWorkRepository();
		defaultGit = new Git(remoteDb);
		JGitTestUtil.writeTrashFile(remoteDb, "hello.txt", "world");
		defaultGit.add().addFilepattern("hello.txt").call();
		defaultGit.commit().setMessage("Initial commit").call();

		remoteDb = createWorkRepository();
		notDefaultGit = new Git(remoteDb);
		JGitTestUtil.writeTrashFile(remoteDb, "world.txt", "hello");
		notDefaultGit.add().addFilepattern("world.txt").call();
		notDefaultGit.commit().setMessage("Initial commit").call();

		remoteDb = createWorkRepository();
		groupAGit = new Git(remoteDb);
		JGitTestUtil.writeTrashFile(remoteDb, "a.txt", "world");
		groupAGit.add().addFilepattern("a.txt").call();
		groupAGit.commit().setMessage("Initial commit").call();

		remoteDb = createWorkRepository();
		groupBGit = new Git(remoteDb);
		JGitTestUtil.writeTrashFile(remoteDb, "b.txt", "world");
		groupBGit.add().addFilepattern("b.txt").call();
		groupBGit.commit().setMessage("Initial commit").call();

		resolveRelativeUris();
	}

	@Test
	public void testAddRepoManifest() throws Exception {
		StringBuilder xmlContent = new StringBuilder();
		xmlContent.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
			.append("<manifest>")
			.append("<remote name=\"remote1\" fetch=\".\" />")
			.append("<default revision=\"master\" remote=\"remote1\" />")
			.append("<project path=\"foo\" name=\"")
			.append(defaultUri)
			.append("\" />")
			.append("</manifest>");
		writeTrashFile("manifest.xml", xmlContent.toString());
		RepoCommand command = new RepoCommand(db);
		command.setPath(db.getWorkTree() + "/manifest.xml")
			.setURI(rootUri)
			.call();
		File hello = new File(db.getWorkTree() + "/foo/hello.txt");
		assertTrue(hello.exists());
		BufferedReader reader = new BufferedReader(new FileReader(hello));
		String content = reader.readLine();
		assertTrue(content.startsWith("world"));
	}

	@Test
	public void testRepoManifestGroups() throws Exception {
		StringBuilder xmlContent = new StringBuilder();
		xmlContent.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
			.append("<manifest>")
			.append("<remote name=\"remote1\" fetch=\".\" />")
			.append("<default revision=\"master\" remote=\"remote1\" />")
			.append("<project path=\"foo\" name=\"")
			.append(defaultUri)
			.append("\" groups=\"a,test\" />")
			.append("<project path=\"bar\" name=\"")
			.append(notDefaultUri)
			.append("\" groups=\"notdefault\" />")
			.append("<project path=\"a\" name=\"")
			.append(groupAUri)
			.append("\" groups=\"a\" />")
			.append("<project path=\"b\" name=\"")
			.append(groupBUri)
			.append("\" groups=\"b\" />")
			.append("</manifest>");

		// default should have foo, a & b
		Repository localDb = createWorkRepository();
		JGitTestUtil.writeTrashFile(localDb, "manifest.xml", xmlContent.toString());
		RepoCommand command = new RepoCommand(localDb);
		command.setPath(localDb.getWorkTree() + "/manifest.xml")
			.setURI(rootUri)
			.call();
		File file = new File(localDb.getWorkTree() + "/foo/hello.txt");
		assertTrue(file.exists());
		file = new File(localDb.getWorkTree() + "/bar/world.txt");
		assertFalse(file.exists());
		file = new File(localDb.getWorkTree() + "/a/a.txt");
		assertTrue(file.exists());
		file = new File(localDb.getWorkTree() + "/b/b.txt");
		assertTrue(file.exists());

		// all,-a should have bar & b
		localDb = createWorkRepository();
		JGitTestUtil.writeTrashFile(localDb, "manifest.xml", xmlContent.toString());
		command = new RepoCommand(localDb);
		command.setPath(localDb.getWorkTree() + "/manifest.xml")
			.setURI(rootUri)
			.setGroups("all,-a")
			.call();
		file = new File(localDb.getWorkTree() + "/foo/hello.txt");
		assertFalse(file.exists());
		file = new File(localDb.getWorkTree() + "/bar/world.txt");
		assertTrue(file.exists());
		file = new File(localDb.getWorkTree() + "/a/a.txt");
		assertFalse(file.exists());
		file = new File(localDb.getWorkTree() + "/b/b.txt");
		assertTrue(file.exists());
	}

	private void resolveRelativeUris() {
		// Find the longest common prefix ends with "/" as rootUri.
		defaultUri = defaultGit.getRepository().getDirectory().toURI().toString();
		notDefaultUri = notDefaultGit.getRepository().getDirectory().toURI().toString();
		groupAUri = groupAGit.getRepository().getDirectory().toURI().toString();
		groupBUri = groupBGit.getRepository().getDirectory().toURI().toString();
		int start = 0;
		while (start <= defaultUri.length()) {
			int newStart = defaultUri.indexOf('/', start + 1);
			String prefix = defaultUri.substring(0, newStart);
			if (!notDefaultUri.startsWith(prefix) ||
					!groupAUri.startsWith(prefix) ||
					!groupBUri.startsWith(prefix)) {
				start++;
				rootUri = defaultUri.substring(0, start);
				defaultUri = defaultUri.substring(start);
				notDefaultUri = notDefaultUri.substring(start);
				groupAUri = groupAUri.substring(start);
				groupBUri = groupBUri.substring(start);
				return;
			}
			start = newStart;
		}
	}
}
