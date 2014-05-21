/*
 * Copyright (C) 2014, Google Inc.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.junit.JGitTestUtil;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.junit.Test;

public class RepoCommandTest extends RepositoryTestCase {

	private static final String BRANCH = "branch";
	private static final String TAG = "release";

	private Repository defaultDb;
	private Repository notDefaultDb;
	private Repository groupADb;
	private Repository groupBDb;

	private String rootUri;
	private String defaultUri;
	private String notDefaultUri;
	private String groupAUri;
	private String groupBUri;

	private ObjectId oldCommitId;

	public void setUp() throws Exception {
		super.setUp();

		defaultDb = createWorkRepository();
		Git git = new Git(defaultDb);
		JGitTestUtil.writeTrashFile(defaultDb, "hello.txt", "branch world");
		git.add().addFilepattern("hello.txt").call();
		oldCommitId = git.commit().setMessage("Initial commit").call().getId();
		git.checkout().setName(BRANCH).setCreateBranch(true).call();
		git.checkout().setName("master").call();
		git.tag().setName(TAG).call();
		JGitTestUtil.writeTrashFile(defaultDb, "hello.txt", "master world");
		git.add().addFilepattern("hello.txt").call();
		git.commit().setMessage("Second commit").call();

		notDefaultDb = createWorkRepository();
		git = new Git(notDefaultDb);
		JGitTestUtil.writeTrashFile(notDefaultDb, "world.txt", "hello");
		git.add().addFilepattern("world.txt").call();
		git.commit().setMessage("Initial commit").call();

		groupADb = createWorkRepository();
		git = new Git(groupADb);
		JGitTestUtil.writeTrashFile(groupADb, "a.txt", "world");
		git.add().addFilepattern("a.txt").call();
		git.commit().setMessage("Initial commit").call();

		groupBDb = createWorkRepository();
		git = new Git(groupBDb);
		JGitTestUtil.writeTrashFile(groupBDb, "b.txt", "world");
		git.add().addFilepattern("b.txt").call();
		git.commit().setMessage("Initial commit").call();

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
		command.setPath(db.getWorkTree().getAbsolutePath() + "/manifest.xml")
			.setURI(rootUri)
			.call();
		File hello = new File(db.getWorkTree(), "foo/hello.txt");
		assertTrue("submodule was checked out", hello.exists());
		BufferedReader reader = new BufferedReader(new FileReader(hello));
		String content = reader.readLine();
		reader.close();
		assertEquals("submodule content is as expected.", "master world", content);
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
		command.setPath(localDb.getWorkTree().getAbsolutePath() + "/manifest.xml")
			.setURI(rootUri)
			.call();
		File file = new File(localDb.getWorkTree(), "foo/hello.txt");
		assertTrue("default has foo", file.exists());
		file = new File(localDb.getWorkTree(), "bar/world.txt");
		assertFalse("default doesn't have bar", file.exists());
		file = new File(localDb.getWorkTree(), "a/a.txt");
		assertTrue("default has a", file.exists());
		file = new File(localDb.getWorkTree(), "b/b.txt");
		assertTrue("default has b", file.exists());

		// all,-a should have bar & b
		localDb = createWorkRepository();
		JGitTestUtil.writeTrashFile(localDb, "manifest.xml", xmlContent.toString());
		command = new RepoCommand(localDb);
		command.setPath(localDb.getWorkTree().getAbsolutePath() + "/manifest.xml")
			.setURI(rootUri)
			.setGroups("all,-a")
			.call();
		file = new File(localDb.getWorkTree(), "foo/hello.txt");
		assertFalse("\"all,-a\" doesn't have foo", file.exists());
		file = new File(localDb.getWorkTree(), "bar/world.txt");
		assertTrue("\"all,-a\" has bar", file.exists());
		file = new File(localDb.getWorkTree(), "a/a.txt");
		assertFalse("\"all,-a\" doesn't have a", file.exists());
		file = new File(localDb.getWorkTree(), "b/b.txt");
		assertTrue("\"all,-a\" has have b", file.exists());
	}

	@Test
	public void testRepoManifestCopyFile() throws Exception {
		Repository localDb = createWorkRepository();
		StringBuilder xmlContent = new StringBuilder();
		xmlContent.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
			.append("<manifest>")
			.append("<remote name=\"remote1\" fetch=\".\" />")
			.append("<default revision=\"master\" remote=\"remote1\" />")
			.append("<project path=\"foo\" name=\"")
			.append(defaultUri)
			.append("\">")
			.append("<copyfile src=\"hello.txt\" dest=\"Hello\" />")
			.append("</project>")
			.append("</manifest>");
		JGitTestUtil.writeTrashFile(localDb, "manifest.xml", xmlContent.toString());
		RepoCommand command = new RepoCommand(localDb);
		command.setPath(localDb.getWorkTree().getAbsolutePath() + "/manifest.xml")
			.setURI(rootUri)
			.call();
		// The original file should exist
		File hello = new File(localDb.getWorkTree(), "foo/hello.txt");
		assertTrue("The original file exists", hello.exists());
		BufferedReader reader = new BufferedReader(new FileReader(hello));
		String content = reader.readLine();
		reader.close();
		assertEquals("The original file has expected content", "master world", content);
		// The dest file should also exist
		hello = new File(localDb.getWorkTree(), "Hello");
		assertTrue("The destination file exists", hello.exists());
		reader = new BufferedReader(new FileReader(hello));
		content = reader.readLine();
		reader.close();
		assertEquals("The destination file has expected content", "master world", content);
	}

	@Test
	public void testBareRepo() throws Exception {
		Repository remoteDb = createBareRepository();
		Repository tempDb = createWorkRepository();
		StringBuilder xmlContent = new StringBuilder();
		xmlContent.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
			.append("<manifest>")
			.append("<remote name=\"remote1\" fetch=\".\" />")
			.append("<default revision=\"master\" remote=\"remote1\" />")
			.append("<project path=\"foo\" name=\"")
			.append(defaultUri)
			.append("\" />")
			.append("</manifest>");
		JGitTestUtil.writeTrashFile(tempDb, "manifest.xml", xmlContent.toString());
		RepoCommand command = new RepoCommand(remoteDb);
		command.setPath(tempDb.getWorkTree().getAbsolutePath() + "/manifest.xml")
			.setURI(rootUri)
			.call();
		// Clone it
		File directory = createTempDirectory("testBareRepo");
		CloneCommand clone = Git.cloneRepository();
		clone.setDirectory(directory);
		clone.setURI(remoteDb.getDirectory().toURI().toString());
		Repository localDb = clone.call().getRepository();
		// The .gitmodules file should exist
		File gitmodules = new File(localDb.getWorkTree(), ".gitmodules");
		assertTrue("The .gitmodules file exists", gitmodules.exists());
		// The first line of .gitmodules file should be expected
		BufferedReader reader = new BufferedReader(new FileReader(gitmodules));
		String content = reader.readLine();
		reader.close();
		assertEquals("The first line of .gitmodules file is as expected.",
				"[submodule \"foo\"]", content);
		// The gitlink should be the same as remote head sha1
		String gitlink = localDb.resolve(Constants.HEAD + ":foo").name();
		String remote = defaultDb.resolve(Constants.HEAD).name();
		assertEquals("The gitlink is same as remote head", remote, gitlink);
	}

	@Test
	public void testRevision() throws Exception {
		StringBuilder xmlContent = new StringBuilder();
		xmlContent.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
			.append("<manifest>")
			.append("<remote name=\"remote1\" fetch=\".\" />")
			.append("<default revision=\"master\" remote=\"remote1\" />")
			.append("<project path=\"foo\" name=\"")
			.append(defaultUri)
			.append("\" revision=\"")
			.append(oldCommitId.name())
			.append("\" />")
			.append("</manifest>");
		writeTrashFile("manifest.xml", xmlContent.toString());
		RepoCommand command = new RepoCommand(db);
		command.setPath(db.getWorkTree().getAbsolutePath() + "/manifest.xml")
			.setURI(rootUri)
			.call();
		File hello = new File(db.getWorkTree(), "foo/hello.txt");
		BufferedReader reader = new BufferedReader(new FileReader(hello));
		String content = reader.readLine();
		reader.close();
		assertEquals("submodule content is as expected.", "branch world", content);
	}

	@Test
	public void testRevisionBranch() throws Exception {
		StringBuilder xmlContent = new StringBuilder();
		xmlContent.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
			.append("<manifest>")
			.append("<remote name=\"remote1\" fetch=\".\" />")
			.append("<default revision=\"")
			.append(BRANCH)
			.append("\" remote=\"remote1\" />")
			.append("<project path=\"foo\" name=\"")
			.append(defaultUri)
			.append("\" />")
			.append("</manifest>");
		writeTrashFile("manifest.xml", xmlContent.toString());
		RepoCommand command = new RepoCommand(db);
		command.setPath(db.getWorkTree().getAbsolutePath() + "/manifest.xml")
			.setURI(rootUri)
			.call();
		File hello = new File(db.getWorkTree(), "foo/hello.txt");
		BufferedReader reader = new BufferedReader(new FileReader(hello));
		String content = reader.readLine();
		reader.close();
		assertEquals("submodule content is as expected.", "branch world", content);
	}

	@Test
	public void testRevisionTag() throws Exception {
		StringBuilder xmlContent = new StringBuilder();
		xmlContent.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
			.append("<manifest>")
			.append("<remote name=\"remote1\" fetch=\".\" />")
			.append("<default revision=\"master\" remote=\"remote1\" />")
			.append("<project path=\"foo\" name=\"")
			.append(defaultUri)
			.append("\" revision=\"")
			.append(TAG)
			.append("\" />")
			.append("</manifest>");
		writeTrashFile("manifest.xml", xmlContent.toString());
		RepoCommand command = new RepoCommand(db);
		command.setPath(db.getWorkTree().getAbsolutePath() + "/manifest.xml")
			.setURI(rootUri)
			.call();
		File hello = new File(db.getWorkTree(), "foo/hello.txt");
		BufferedReader reader = new BufferedReader(new FileReader(hello));
		String content = reader.readLine();
		reader.close();
		assertEquals("submodule content is as expected.", "branch world", content);
	}

	@Test
	public void testRevisionBare() throws Exception {
		Repository remoteDb = createBareRepository();
		Repository tempDb = createWorkRepository();
		StringBuilder xmlContent = new StringBuilder();
		xmlContent.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
			.append("<manifest>")
			.append("<remote name=\"remote1\" fetch=\".\" />")
			.append("<default revision=\"")
			.append(BRANCH)
			.append("\" remote=\"remote1\" />")
			.append("<project path=\"foo\" name=\"")
			.append(defaultUri)
			.append("\" />")
			.append("</manifest>");
		JGitTestUtil.writeTrashFile(tempDb, "manifest.xml", xmlContent.toString());
		RepoCommand command = new RepoCommand(remoteDb);
		command.setPath(tempDb.getWorkTree().getAbsolutePath() + "/manifest.xml")
			.setURI(rootUri)
			.call();
		// Clone it
		File directory = createTempDirectory("testRevisionBare");
		CloneCommand clone = Git.cloneRepository();
		clone.setDirectory(directory);
		clone.setURI(remoteDb.getDirectory().toURI().toString());
		Repository localDb = clone.call().getRepository();
		// The gitlink should be the same as oldCommitId
		String gitlink = localDb.resolve(Constants.HEAD + ":foo").name();
		assertEquals("The gitlink is same as remote head",
				oldCommitId.name(), gitlink);
	}

	@Test
	public void testCopyFileBare() throws Exception {
		Repository remoteDb = createBareRepository();
		Repository tempDb = createWorkRepository();
		StringBuilder xmlContent = new StringBuilder();
		xmlContent.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
			.append("<manifest>")
			.append("<remote name=\"remote1\" fetch=\".\" />")
			.append("<default revision=\"master\" remote=\"remote1\" />")
			.append("<project path=\"foo\" name=\"")
			.append(defaultUri)
			.append("\" revision=\"")
			.append(BRANCH)
			.append("\" >")
			.append("<copyfile src=\"hello.txt\" dest=\"Hello\" />")
			.append("</project>")
			.append("</manifest>");
		JGitTestUtil.writeTrashFile(tempDb, "manifest.xml", xmlContent.toString());
		RepoCommand command = new RepoCommand(remoteDb);
		command.setPath(tempDb.getWorkTree().getAbsolutePath() + "/manifest.xml")
			.setURI(rootUri)
			.call();
		// Clone it
		File directory = createTempDirectory("testCopyFileBare");
		CloneCommand clone = Git.cloneRepository();
		clone.setDirectory(directory);
		clone.setURI(remoteDb.getDirectory().toURI().toString());
		Repository localDb = clone.call().getRepository();
		// The Hello file should exist
		File hello = new File(localDb.getWorkTree(), "Hello");
		assertTrue("The Hello file exists", hello.exists());
		// The content of Hello file should be expected
		BufferedReader reader = new BufferedReader(new FileReader(hello));
		String content = reader.readLine();
		reader.close();
		assertEquals("The Hello file has expected content", "branch world", content);
	}

	@Test
	public void testReplaceManifestBare () throws Exception {
		Repository remoteDb = createBareRepository();
		Repository tempDb = createWorkRepository();
		StringBuilder xmlContent = new StringBuilder();
		xmlContent.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
			.append("<manifest>")
			.append("<remote name=\"remote1\" fetch=\".\" />")
			.append("<default revision=\"master\" remote=\"remote1\" />")
			.append("<project path=\"foo\" name=\"")
			.append(defaultUri)
			.append("\" revision=\"")
			.append(BRANCH)
			.append("\" >")
			.append("<copyfile src=\"hello.txt\" dest=\"Hello\" />")
			.append("</project>")
			.append("</manifest>");
		JGitTestUtil.writeTrashFile(tempDb, "old.xml", xmlContent.toString());
		RepoCommand command = new RepoCommand(remoteDb);
		command.setPath(tempDb.getWorkTree().getAbsolutePath() + "/old.xml")
			.setURI(rootUri)
			.call();
		xmlContent = new StringBuilder();
		xmlContent.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
			.append("<manifest>")
			.append("<remote name=\"remote1\" fetch=\".\" />")
			.append("<default revision=\"master\" remote=\"remote1\" />")
			.append("<project path=\"bar\" name=\"")
			.append(defaultUri)
			.append("\" revision=\"")
			.append(BRANCH)
			.append("\" >")
			.append("<copyfile src=\"hello.txt\" dest=\"Hello.txt\" />")
			.append("</project>")
			.append("</manifest>");
		JGitTestUtil.writeTrashFile(tempDb, "new.xml", xmlContent.toString());
		command = new RepoCommand(remoteDb);
		command.setPath(tempDb.getWorkTree().getAbsolutePath() + "/new.xml")
			.setOldPath(tempDb.getWorkTree().getAbsolutePath() + "/old.xml")
			.setURI(rootUri)
			.call();
		// Clone it
		File directory = createTempDirectory("testReplaceManifestBare");
		CloneCommand clone = Git.cloneRepository();
		clone.setDirectory(directory);
		clone.setURI(remoteDb.getDirectory().toURI().toString());
		Repository localDb = clone.call().getRepository();
		// The Hello file should not exist
		File hello = new File(localDb.getWorkTree(), "Hello");
		assertFalse("The Hello file doesn't exist", hello.exists());
		// The Hello.txt file should exist
		File hellotxt = new File(localDb.getWorkTree(), "Hello.txt");
		assertTrue("The Hello.txt file exists", hellotxt.exists());
		// The .gitmodules file should have 'submodule "bar"' and shouldn't have
		// 'submodule "foo"' lines.
		File dotmodules = new File(localDb.getWorkTree(),
				Constants.DOT_GIT_MODULES);
		BufferedReader reader = new BufferedReader(new FileReader(dotmodules));
		boolean foo = false;
		boolean bar = false;
		while (true) {
			String line = reader.readLine();
			if (line == null)
				break;
			if (line.contains("submodule \"foo\""))
				foo = true;
			if (line.contains("submodule \"bar\""))
				bar = true;
		}
		reader.close();
		assertTrue("The bar submodule exists", bar);
		assertFalse("The foo submodule doesn't exist", foo);
	}

	private void resolveRelativeUris() {
		// Find the longest common prefix ends with "/" as rootUri.
		defaultUri = defaultDb.getDirectory().toURI().toString();
		notDefaultUri = notDefaultDb.getDirectory().toURI().toString();
		groupAUri = groupADb.getDirectory().toURI().toString();
		groupBUri = groupBDb.getDirectory().toURI().toString();
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
