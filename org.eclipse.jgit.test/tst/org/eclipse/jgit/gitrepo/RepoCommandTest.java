/*
 * Copyright (C) 2014, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.gitrepo;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRefNameException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.gitrepo.RepoCommand.ManifestErrorException;
import org.eclipse.jgit.gitrepo.RepoCommand.RemoteFile;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.junit.JGitTestUtil;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.BlobBasedConfig;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.RawParseUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

	@Override
	@BeforeEach
	public void setUp() throws Exception {
		super.setUp();

		defaultDb = createWorkRepository();
		try (Git git = new Git(defaultDb)) {
			JGitTestUtil.writeTrashFile(defaultDb, "hello.txt", "branch world");
			git.add().addFilepattern("hello.txt").call();
			oldCommitId = git.commit().setMessage("Initial commit").call().getId();
			git.checkout().setName(BRANCH).setCreateBranch(true).call();
			git.checkout().setName("master").call();
			git.tag().setName(TAG).call();
			JGitTestUtil.writeTrashFile(defaultDb, "hello.txt", "master world");
			git.add().addFilepattern("hello.txt").call();
			git.commit().setMessage("Second commit").call();
			addRepoToClose(defaultDb);
		}

		notDefaultDb = createWorkRepository();
		try (Git git = new Git(notDefaultDb)) {
			JGitTestUtil.writeTrashFile(notDefaultDb, "world.txt", "hello");
			git.add().addFilepattern("world.txt").call();
			git.commit().setMessage("Initial commit").call();
			addRepoToClose(notDefaultDb);
		}

		groupADb = createWorkRepository();
		try (Git git = new Git(groupADb)) {
			JGitTestUtil.writeTrashFile(groupADb, "a.txt", "world");
			git.add().addFilepattern("a.txt").call();
			git.commit().setMessage("Initial commit").call();
			addRepoToClose(groupADb);
		}

		groupBDb = createWorkRepository();
		try (Git git = new Git(groupBDb)) {
			JGitTestUtil.writeTrashFile(groupBDb, "b.txt", "world");
			git.add().addFilepattern("b.txt").call();
			git.commit().setMessage("Initial commit").call();
			addRepoToClose(groupBDb);
		}

		resolveRelativeUris();
	}

	static class IndexedRepos implements RepoCommand.RemoteReader {
		Map<String, Repository> uriRepoMap;

		IndexedRepos() {
			uriRepoMap = new HashMap<>();
		}

		void put(String u, Repository r) {
			uriRepoMap.put(u, r);
		}

		@Override
		public ObjectId sha1(String uri, String refname) throws GitAPIException {
			if (!uriRepoMap.containsKey(uri)) {
				return null;
			}

			Repository r = uriRepoMap.get(uri);
			try {
				Ref ref = r.findRef(refname);
				if (ref == null) return null;

				ref = r.getRefDatabase().peel(ref);
				ObjectId id = ref.getObjectId();
				return id;
			} catch (IOException e) {
				throw new InvalidRemoteException("", e);
			}
		}

		@Override
		public RemoteFile readFileWithMode(String uri, String ref, String path)
				throws GitAPIException, IOException {
			Repository repo = uriRepoMap.get(uri);
			ObjectId refCommitId = sha1(uri, ref);
			if (refCommitId == null) {
				throw new InvalidRefNameException(MessageFormat
						.format(JGitText.get().refNotResolved, ref));
			}
			RevCommit commit = repo.parseCommit(refCommitId);
			TreeWalk tw = TreeWalk.forPath(repo, path, commit.getTree());

			// TODO(ifrade): Cope better with big files (e.g. using InputStream
			// instead of byte[])
			return new RemoteFile(tw.getObjectReader().open(tw.getObjectId(0))
					.getCachedBytes(Integer.MAX_VALUE), tw.getFileMode(0));
		}
	}

	private Repository cloneRepository(Repository repo, boolean bare)
			throws Exception {
		Repository r = Git.cloneRepository()
				.setURI(repo.getDirectory().toURI().toString())
				.setDirectory(createUniqueTestGitDir(true)).setBare(bare).call()
				.getRepository();
		if (bare) {
			assertTrue(r.isBare());
		} else {
			assertFalse(r.isBare());
		}
		return r;
	}

	private static void assertContents(Path path, String expected)
			throws IOException {
		try (BufferedReader reader = Files.newBufferedReader(path, UTF_8)) {
			String content = reader.readLine();
			assertEquals(expected, content, "Unexpected content in " + path.getFileName());
		}
	}

	@Test
	void runTwiceIsNOP() throws Exception {
		try (Repository child = cloneRepository(groupADb, true);
				Repository dest = cloneRepository(db, true)) {
			StringBuilder xmlContent = new StringBuilder();
			xmlContent.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
					.append("<manifest>")
					.append("<remote name=\"remote1\" fetch=\"..\" />")
					.append("<default revision=\"master\" remote=\"remote1\" />")
					.append("<project path=\"base\" name=\"platform/base\" />")
					.append("</manifest>");
			RepoCommand cmd = new RepoCommand(dest);

			IndexedRepos repos = new IndexedRepos();
			repos.put("platform/base", child);

			RevCommit commit = cmd
					.setInputStream(new ByteArrayInputStream(
							xmlContent.toString().getBytes(UTF_8)))
					.setRemoteReader(repos).setURI("platform/")
					.setTargetURI("platform/superproject")
					.setRecordRemoteBranch(true).setRecordSubmoduleLabels(true)
					.call();

			String firstIdStr = commit.getId().name() + ":" + ".gitmodules";
			commit = new RepoCommand(dest)
					.setInputStream(new ByteArrayInputStream(
							xmlContent.toString().getBytes(UTF_8)))
					.setRemoteReader(repos).setURI("platform/")
					.setTargetURI("platform/superproject")
					.setRecordRemoteBranch(true).setRecordSubmoduleLabels(true)
					.call();
			String idStr = commit.getId().name() + ":" + ".gitmodules";
			assertEquals(firstIdStr, idStr);
		}
	}

	@Test
	void androidSetup() throws Exception {
		try (Repository child = cloneRepository(groupADb, true);
				Repository dest = cloneRepository(db, true)) {
			StringBuilder xmlContent = new StringBuilder();
			xmlContent.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
					.append("<manifest>")
					.append("<remote name=\"remote1\" fetch=\"..\" />")
					.append("<default revision=\"master\" remote=\"remote1\" />")
					.append("<project path=\"base\" name=\"platform/base\" />")
					.append("</manifest>");
			RepoCommand cmd = new RepoCommand(dest);

			IndexedRepos repos = new IndexedRepos();
			repos.put("platform/base", child);

			RevCommit commit = cmd
					.setInputStream(new ByteArrayInputStream(
							xmlContent.toString().getBytes(UTF_8)))
					.setRemoteReader(repos).setURI("platform/")
					.setTargetURI("platform/superproject")
					.setRecordRemoteBranch(true).setRecordSubmoduleLabels(true)
					.call();

			String idStr = commit.getId().name() + ":" + ".gitmodules";
			ObjectId modId = dest.resolve(idStr);

			try (ObjectReader reader = dest.newObjectReader()) {
				byte[] bytes = reader.open(modId)
						.getCachedBytes(Integer.MAX_VALUE);
				Config base = new Config();
				BlobBasedConfig cfg = new BlobBasedConfig(base, bytes);
				String subUrl = cfg.getString("submodule", "platform/base",
						"url");
				assertEquals(subUrl, "../base");
			}
		}
	}

	@Test
	void recordUnreachableRemotes() throws Exception {
		StringBuilder xmlContent = new StringBuilder();
		xmlContent.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
				.append("<manifest>")
				.append("<remote name=\"remote1\" fetch=\"https://host.com/\" />")
				.append("<default revision=\"master\" remote=\"remote1\" />")
				.append("<project path=\"base\" name=\"platform/base\" />")
				.append("</manifest>");

		try (Repository dest = cloneRepository(db, true)) {
			RevCommit commit = new RepoCommand(dest)
					.setInputStream(new ByteArrayInputStream(
							xmlContent.toString().getBytes(UTF_8)))
					.setRemoteReader(new IndexedRepos()).setURI("platform/")
					.setTargetURI("platform/superproject")
					.setRecordRemoteBranch(true).setIgnoreRemoteFailures(true)
					.setRecordSubmoduleLabels(true).call();

			String idStr = commit.getId().name() + ":" + ".gitmodules";
			ObjectId modId = dest.resolve(idStr);

			try (ObjectReader reader = dest.newObjectReader()) {
				byte[] bytes = reader.open(modId)
						.getCachedBytes(Integer.MAX_VALUE);
				Config base = new Config();
				BlobBasedConfig cfg = new BlobBasedConfig(base, bytes);
				String subUrl = cfg.getString("submodule", "platform/base",
						"url");
				assertEquals(subUrl, "https://host.com/platform/base");
			}
		}
	}

	@Test
	void gerritSetup() throws Exception {
		try (Repository child = cloneRepository(groupADb, true);
				Repository dest = cloneRepository(db, true)) {
			StringBuilder xmlContent = new StringBuilder();
			xmlContent.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
					.append("<manifest>")
					.append("<remote name=\"remote1\" fetch=\".\" />")
					.append("<default revision=\"master\" remote=\"remote1\" />")
					.append("<project path=\"plugins/cookbook\" name=\"plugins/cookbook\" />")
					.append("</manifest>");
			RepoCommand cmd = new RepoCommand(dest);

			IndexedRepos repos = new IndexedRepos();
			repos.put("plugins/cookbook", child);

			RevCommit commit = cmd
					.setInputStream(new ByteArrayInputStream(
							xmlContent.toString().getBytes(UTF_8)))
					.setRemoteReader(repos).setURI("").setTargetURI("gerrit")
					.setRecordRemoteBranch(true).setRecordSubmoduleLabels(true)
					.call();

			String idStr = commit.getId().name() + ":" + ".gitmodules";
			ObjectId modId = dest.resolve(idStr);

			try (ObjectReader reader = dest.newObjectReader()) {
				byte[] bytes = reader.open(modId)
						.getCachedBytes(Integer.MAX_VALUE);
				Config base = new Config();
				BlobBasedConfig cfg = new BlobBasedConfig(base, bytes);
				String subUrl = cfg.getString("submodule", "plugins/cookbook",
						"url");
				assertEquals(subUrl, "../plugins/cookbook");
			}
		}
	}

	@Test
	void absoluteRemoteURL() throws Exception {
		try (Repository child = cloneRepository(groupADb, true);
				Repository dest = cloneRepository(db, true)) {
			String abs = "https://chromium.googlesource.com";
			String repoUrl = "https://chromium.googlesource.com/chromium/src";
			boolean fetchSlash = false;
			boolean baseSlash = false;
			do {
				do {
					String fetchUrl = fetchSlash ? abs + "/" : abs;
					String baseUrl = baseSlash ? abs + "/" : abs;

					StringBuilder xmlContent = new StringBuilder();
					xmlContent.append(
							"<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
							.append("<manifest>")
							.append("<remote name=\"origin\" fetch=\""
									+ fetchUrl + "\" />")
							.append("<default revision=\"master\" remote=\"origin\" />")
							.append("<project path=\"src\" name=\"chromium/src\" />")
							.append("</manifest>");
					RepoCommand cmd = new RepoCommand(dest);

					IndexedRepos repos = new IndexedRepos();
					repos.put(repoUrl, child);

					RevCommit commit = cmd
							.setInputStream(new ByteArrayInputStream(
									xmlContent.toString().getBytes(UTF_8)))
							.setRemoteReader(repos).setURI(baseUrl)
							.setTargetURI("gerrit").setRecordRemoteBranch(true)
							.setRecordSubmoduleLabels(true).call();

					String idStr = commit.getId().name() + ":" + ".gitmodules";
					ObjectId modId = dest.resolve(idStr);

					try (ObjectReader reader = dest.newObjectReader()) {
						byte[] bytes = reader.open(modId)
								.getCachedBytes(Integer.MAX_VALUE);
						Config base = new Config();
						BlobBasedConfig cfg = new BlobBasedConfig(base, bytes);
						String subUrl = cfg.getString("submodule",
								"chromium/src", "url");
						assertEquals(
								"https://chromium.googlesource.com/chromium/src",
								subUrl);
					}
					fetchSlash = !fetchSlash;
				} while (fetchSlash);
				baseSlash = !baseSlash;
			} while (baseSlash);
		}
	}

	@Test
	void absoluteRemoteURLAbsoluteTargetURL() throws Exception {
		try (Repository child = cloneRepository(groupADb, true);
				Repository dest = cloneRepository(db, true)) {
			String abs = "https://chromium.googlesource.com";
			String repoUrl = "https://chromium.googlesource.com/chromium/src";
			boolean fetchSlash = false;
			boolean baseSlash = false;
			do {
				do {
					String fetchUrl = fetchSlash ? abs + "/" : abs;
					String baseUrl = baseSlash ? abs + "/" : abs;

					StringBuilder xmlContent = new StringBuilder();
					xmlContent.append(
							"<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
							.append("<manifest>")
							.append("<remote name=\"origin\" fetch=\""
									+ fetchUrl + "\" />")
							.append("<default revision=\"master\" remote=\"origin\" />")
							.append("<project path=\"src\" name=\"chromium/src\" />")
							.append("</manifest>");
					RepoCommand cmd = new RepoCommand(dest);

					IndexedRepos repos = new IndexedRepos();
					repos.put(repoUrl, child);

					RevCommit commit = cmd
							.setInputStream(new ByteArrayInputStream(
									xmlContent.toString().getBytes(UTF_8)))
							.setRemoteReader(repos).setURI(baseUrl)
							.setTargetURI(abs + "/superproject")
							.setRecordRemoteBranch(true)
							.setRecordSubmoduleLabels(true).call();

					String idStr = commit.getId().name() + ":" + ".gitmodules";
					ObjectId modId = dest.resolve(idStr);

					try (ObjectReader reader = dest.newObjectReader()) {
						byte[] bytes = reader.open(modId)
								.getCachedBytes(Integer.MAX_VALUE);
						Config base = new Config();
						BlobBasedConfig cfg = new BlobBasedConfig(base, bytes);
						String subUrl = cfg.getString("submodule",
								"chromium/src", "url");
						assertEquals("../chromium/src", subUrl);
					}
					fetchSlash = !fetchSlash;
				} while (fetchSlash);
				baseSlash = !baseSlash;
			} while (baseSlash);
		}
	}

	@Test
	void testAddRepoManifest() throws Exception {
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
		assertTrue(hello.exists(), "submodule should be checked out");
		assertContents(hello.toPath(), "master world");
	}

	@Test
	void testRepoManifestGroups() throws Exception {
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
		JGitTestUtil.writeTrashFile(
				localDb, "manifest.xml", xmlContent.toString());
		RepoCommand command = new RepoCommand(localDb);
		command
				.setPath(localDb.getWorkTree().getAbsolutePath() + "/manifest.xml")
				.setURI(rootUri)
				.call();
		File file = new File(localDb.getWorkTree(), "foo/hello.txt");
		assertTrue(file.exists(), "default should have foo");
		file = new File(localDb.getWorkTree(), "bar/world.txt");
		assertFalse(file.exists(), "default shouldn't have bar");
		file = new File(localDb.getWorkTree(), "a/a.txt");
		assertTrue(file.exists(), "default should have a");
		file = new File(localDb.getWorkTree(), "b/b.txt");
		assertTrue(file.exists(), "default should have b");

		// all,-a should have bar & b
		localDb = createWorkRepository();
		JGitTestUtil.writeTrashFile(
				localDb, "manifest.xml", xmlContent.toString());
		command = new RepoCommand(localDb);
		command
				.setPath(localDb.getWorkTree().getAbsolutePath() + "/manifest.xml")
				.setURI(rootUri)
				.setGroups("all,-a")
				.call();
		file = new File(localDb.getWorkTree(), "foo/hello.txt");
		assertFalse(file.exists(), "\"all,-a\" shouldn't have foo");
		file = new File(localDb.getWorkTree(), "bar/world.txt");
		assertTrue(file.exists(), "\"all,-a\" should have bar");
		file = new File(localDb.getWorkTree(), "a/a.txt");
		assertFalse(file.exists(), "\"all,-a\" shuoldn't have a");
		file = new File(localDb.getWorkTree(), "b/b.txt");
		assertTrue(file.exists(), "\"all,-a\" should have b");
	}

	@Test
	void testRepoManifestCopyFile() throws Exception {
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
		JGitTestUtil.writeTrashFile(
				localDb, "manifest.xml", xmlContent.toString());
		RepoCommand command = new RepoCommand(localDb);
		command
				.setPath(localDb.getWorkTree().getAbsolutePath() + "/manifest.xml")
				.setURI(rootUri)
				.call();
		// The original file should exist
		File hello = new File(localDb.getWorkTree(), "foo/hello.txt");
		assertTrue(hello.exists(), "The original file should exist");
		if (FS.DETECTED.supportsExecute()) {
			assertFalse(FS.DETECTED.canExecute(hello),
					"The original file should not be executable");
		}
		assertContents(hello.toPath(), "master world");
		// The dest file should also exist
		hello = new File(localDb.getWorkTree(), "Hello");
		assertTrue(hello.exists(), "The destination file should exist");
		if (FS.DETECTED.supportsExecute()) {
			assertFalse(FS.DETECTED.canExecute(hello),
					"The destination file should not be executable");
		}
		assertContents(hello.toPath(), "master world");
	}

	@Test
	void testRepoManifestCopyFile_executable() throws Exception {
		assumeTrue(FS.DETECTED.supportsExecute());
		try (Git git = new Git(defaultDb)) {
			git.checkout().setName("master").call();
			File f = JGitTestUtil.writeTrashFile(defaultDb, "hello.sh",
					"content of the executable file");
			FS.DETECTED.setExecute(f, true);
			git.add().addFilepattern("hello.sh").call();
			git.commit().setMessage("Add binary file").call();
		}

		Repository localDb = createWorkRepository();
		StringBuilder xmlContent = new StringBuilder();
		xmlContent.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
				.append("<manifest>")
				.append("<remote name=\"remote1\" fetch=\".\" />")
				.append("<default revision=\"master\" remote=\"remote1\" />")
				.append("<project path=\"foo\" name=\"").append(defaultUri)
				.append("\">")
				.append("<copyfile src=\"hello.sh\" dest=\"copy-hello.sh\" />")
				.append("</project>").append("</manifest>");
		JGitTestUtil.writeTrashFile(localDb, "manifest.xml",
				xmlContent.toString());
		RepoCommand command = new RepoCommand(localDb);
		command.setPath(
				localDb.getWorkTree().getAbsolutePath() + "/manifest.xml")
				.setURI(rootUri).call();

		// The original file should exist and be an executable
		File hello = new File(localDb.getWorkTree(), "foo/hello.sh");
		assertTrue(hello.exists(), "The original file should exist");
		assertTrue(FS.DETECTED.canExecute(hello),
				"The original file must be executable");
		try (BufferedReader reader = Files.newBufferedReader(hello.toPath(),
				UTF_8)) {
			String content = reader.readLine();
			assertEquals("content of the executable file", content, "The original file should have expected content");
		}

		// The destination file should also exist and be an executable
		hello = new File(localDb.getWorkTree(), "copy-hello.sh");
		assertTrue(hello.exists(), "The destination file should exist");
		assertTrue(FS.DETECTED.canExecute(hello),
				"The destination file must be executable");
		try (BufferedReader reader = Files.newBufferedReader(hello.toPath(),
				UTF_8)) {
			String content = reader.readLine();
			assertEquals("content of the executable file", content, "The destination file should have expected content");
		}
	}

	@Test
	void testBareRepo() throws Exception {
		Repository remoteDb = createBareRepository();
		Repository tempDb = createWorkRepository();

		StringBuilder xmlContent = new StringBuilder();
		xmlContent.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
				.append("<manifest>")
				.append("<remote name=\"remote1\" fetch=\".\" />")
				.append("<default revision=\"master\" remote=\"remote1\" />")
				.append("<project path=\"foo\" name=\"").append(defaultUri)
				.append("\" />").append("</manifest>");
		JGitTestUtil.writeTrashFile(tempDb, "manifest.xml",
				xmlContent.toString());
		RepoCommand command = new RepoCommand(remoteDb);
		command.setPath(
				tempDb.getWorkTree().getAbsolutePath() + "/manifest.xml")
				.setURI(rootUri).call();
		// Clone it
		File directory = createTempDirectory("testBareRepo");
		try (Repository localDb = Git.cloneRepository().setDirectory(directory)
				.setURI(remoteDb.getDirectory().toURI().toString()).call()
				.getRepository()) {
			// The .gitmodules file should exist
			File gitmodules = new File(localDb.getWorkTree(), ".gitmodules");
			assertTrue(gitmodules.exists(),
					"The .gitmodules file should exist");
			// The first line of .gitmodules file should be expected
			try (BufferedReader reader = Files
					.newBufferedReader(gitmodules.toPath(), UTF_8)) {
				String content = reader.readLine();
				assertEquals(
						"[submodule \"" + defaultUri + "\"]", content, "The first line of .gitmodules file should be as expected");
			}
			// The gitlink should be the same as remote head sha1
			String gitlink = localDb.resolve(Constants.HEAD + ":foo").name();
			String remote = defaultDb.resolve(Constants.HEAD).name();
			assertEquals(remote, gitlink, "The gitlink should be the same as remote head");
		}
	}

	@Test
	void testRevision() throws Exception {
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
		try (BufferedReader reader = Files.newBufferedReader(hello.toPath(),
				UTF_8)) {
			String content = reader.readLine();
			assertEquals("branch world", content, "submodule content should be as expected");
		}
	}

	@Test
	void testRevisionBranch() throws Exception {
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
		assertContents(hello.toPath(), "branch world");
	}

	@Test
	void testRevisionTag() throws Exception {
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
		assertContents(hello.toPath(), "branch world");
	}

	@Test
	void testRevisionBare() throws Exception {
		Repository remoteDb = createBareRepository();
		Repository tempDb = createWorkRepository();

		StringBuilder xmlContent = new StringBuilder();
		xmlContent.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
				.append("<manifest>")
				.append("<remote name=\"remote1\" fetch=\".\" />")
				.append("<default revision=\"").append(BRANCH)
				.append("\" remote=\"remote1\" />")
				.append("<project path=\"foo\" name=\"").append(defaultUri)
				.append("\" />").append("</manifest>");
		JGitTestUtil.writeTrashFile(tempDb, "manifest.xml",
				xmlContent.toString());
		RepoCommand command = new RepoCommand(remoteDb);
		command.setPath(
				tempDb.getWorkTree().getAbsolutePath() + "/manifest.xml")
				.setURI(rootUri).call();
		// Clone it
		File directory = createTempDirectory("testRevisionBare");
		try (Repository localDb = Git.cloneRepository().setDirectory(directory)
				.setURI(remoteDb.getDirectory().toURI().toString()).call()
				.getRepository()) {
			// The gitlink should be the same as oldCommitId
			String gitlink = localDb.resolve(Constants.HEAD + ":foo").name();
			assertEquals(oldCommitId.name(), gitlink, "The gitlink is same as remote head");

			File dotmodules = new File(localDb.getWorkTree(),
					Constants.DOT_GIT_MODULES);
			assertTrue(dotmodules.exists());
			// The .gitmodules file should have "branch" lines
			String gitModulesContents = RawParseUtils
					.decode(IO.readFully(dotmodules));
			assertTrue(gitModulesContents.contains("branch = branch"));
		}
	}

	@Test
	void testRevisionBare_ignoreTags() throws Exception {
		Repository remoteDb = createBareRepository();
		Repository tempDb = createWorkRepository();

		StringBuilder xmlContent = new StringBuilder();
		xmlContent.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
				.append("<manifest>")
				.append("<remote name=\"remote1\" fetch=\".\" />")
				.append("<default revision=\"").append("refs/tags/" + TAG)
				.append("\" remote=\"remote1\" />")
				.append("<project path=\"foo\" name=\"")
				.append(defaultUri)
				.append("\" />").append("</manifest>");
		JGitTestUtil.writeTrashFile(tempDb, "manifest.xml",
				xmlContent.toString());
		RepoCommand command = new RepoCommand(remoteDb);
		command.setPath(
				tempDb.getWorkTree().getAbsolutePath() + "/manifest.xml")
				.setURI(rootUri).call();
		// Clone it
		File directory = createTempDirectory("testReplaceManifestBare");
		File dotmodules;
		try (Repository localDb = Git.cloneRepository().setDirectory(directory)
				.setURI(remoteDb.getDirectory().toURI().toString()).call()
				.getRepository()) {
			dotmodules = new File(localDb.getWorkTree(),
					Constants.DOT_GIT_MODULES);
			assertTrue(dotmodules.exists());
		}

		// The .gitmodules file should not have "branch" lines
		String gitModulesContents = RawParseUtils
				.decode(IO.readFully(dotmodules));
		assertFalse(gitModulesContents.contains("branch"));
		assertTrue(gitModulesContents.contains("ref = refs/tags/" + TAG));
	}

	@Test
	void testCopyFileBare() throws Exception {
		Repository remoteDb = createBareRepository();
		Repository tempDb = createWorkRepository();

		StringBuilder xmlContent = new StringBuilder();
		xmlContent.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
				.append("<manifest>")
				.append("<remote name=\"remote1\" fetch=\".\" />")
				.append("<default revision=\"master\" remote=\"remote1\" />")
				.append("<project path=\"foo\" name=\"").append(defaultUri)
				.append("\" revision=\"").append(BRANCH).append("\" >")
				.append("<copyfile src=\"hello.txt\" dest=\"Hello\" />")
				.append("<copyfile src=\"hello.txt\" dest=\"foo/Hello\" />")
				.append("</project>").append("</manifest>");
		JGitTestUtil.writeTrashFile(tempDb, "manifest.xml",
				xmlContent.toString());
		RepoCommand command = new RepoCommand(remoteDb);
		command.setPath(
				tempDb.getWorkTree().getAbsolutePath() + "/manifest.xml")
				.setURI(rootUri).call();
		// Clone it
		File directory = createTempDirectory("testCopyFileBare");
		try (Repository localDb = Git.cloneRepository().setDirectory(directory)
				.setURI(remoteDb.getDirectory().toURI().toString()).call()
				.getRepository()) {
			// The Hello file should exist
			File hello = new File(localDb.getWorkTree(), "Hello");
			assertTrue(hello.exists(), "The Hello file should exist");
			// The foo/Hello file should be skipped.
			File foohello = new File(localDb.getWorkTree(), "foo/Hello");
			assertFalse(foohello.exists(),
					"The foo/Hello file should be skipped");
			// The content of Hello file should be expected
			assertContents(hello.toPath(), "branch world");
		}
	}

	@Test
	void testCopyFileBare_executable() throws Exception {
		try (Git git = new Git(defaultDb)) {
			git.checkout().setName(BRANCH).call();
			File f = JGitTestUtil.writeTrashFile(defaultDb, "hello.sh",
					"content of the executable file");
			f.setExecutable(true);
			git.add().addFilepattern("hello.sh").call();
			git.commit().setMessage("Add binary file").call();
		}

		Repository remoteDb = createBareRepository();
		Repository tempDb = createWorkRepository();

		StringBuilder xmlContent = new StringBuilder();
		xmlContent.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
				.append("<manifest>")
				.append("<remote name=\"remote1\" fetch=\".\" />")
				.append("<default revision=\"master\" remote=\"remote1\" />")
				.append("<project path=\"foo\" name=\"").append(defaultUri)
				.append("\" revision=\"").append(BRANCH)
				.append("\" >")
				.append("<copyfile src=\"hello.txt\" dest=\"Hello\" />")
				.append("<copyfile src=\"hello.txt\" dest=\"foo/Hello\" />")
				.append("<copyfile src=\"hello.sh\" dest=\"copy-hello.sh\" />")
				.append("</project>").append("</manifest>");
		JGitTestUtil.writeTrashFile(tempDb, "manifest.xml",
				xmlContent.toString());
		RepoCommand command = new RepoCommand(remoteDb);
		command.setPath(
				tempDb.getWorkTree().getAbsolutePath() + "/manifest.xml")
				.setURI(rootUri).call();
		// Clone it
		File directory = createTempDirectory("testCopyFileBare");
		try (Repository localDb = Git.cloneRepository().setDirectory(directory)
				.setURI(remoteDb.getDirectory().toURI().toString()).call()
				.getRepository()) {
			// The Hello file should exist
			File hello = new File(localDb.getWorkTree(), "Hello");
			assertTrue(hello.exists(), "The Hello file should exist");
			// The foo/Hello file should be skipped.
			File foohello = new File(localDb.getWorkTree(), "foo/Hello");
			assertFalse(foohello.exists(),
					"The foo/Hello file should be skipped");
			// The content of Hello file should be expected
			try (BufferedReader reader = Files.newBufferedReader(hello.toPath(),
					UTF_8)) {
				String content = reader.readLine();
				assertEquals("branch world", content, "The Hello file should have expected content");
			}

			// The executable file must be there and preserve the executable bit
			File helloSh = new File(localDb.getWorkTree(), "copy-hello.sh");
			assertTrue(helloSh.exists(), "Destination file should exist");
			assertContents(helloSh.toPath(), "content of the executable file");
			assertTrue(helloSh.canExecute(),
					"Destination file should be executable");

		}
	}

	@Test
	void testReplaceManifestBare() throws Exception {
		Repository remoteDb = createBareRepository();
		Repository tempDb = createWorkRepository();

		StringBuilder xmlContent = new StringBuilder();
		xmlContent.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
				.append("<manifest>")
				.append("<remote name=\"remote1\" fetch=\".\" />")
				.append("<default revision=\"master\" remote=\"remote1\" />")
				.append("<project path=\"foo\" name=\"").append(defaultUri)
				.append("\" revision=\"").append(BRANCH).append("\" >")
				.append("<copyfile src=\"hello.txt\" dest=\"Hello\" />")
				.append("</project>").append("</manifest>");
		JGitTestUtil.writeTrashFile(tempDb, "old.xml", xmlContent.toString());
		RepoCommand command = new RepoCommand(remoteDb);
		command.setPath(tempDb.getWorkTree().getAbsolutePath() + "/old.xml")
				.setURI(rootUri).call();
		xmlContent = new StringBuilder();
		xmlContent.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
				.append("<manifest>")
				.append("<remote name=\"remote1\" fetch=\".\" />")
				.append("<default revision=\"master\" remote=\"remote1\" />")
				.append("<project path=\"bar\" name=\"").append(notDefaultUri)
				.append("\" >")
				.append("<copyfile src=\"world.txt\" dest=\"World.txt\" />")
				.append("</project>").append("</manifest>");
		JGitTestUtil.writeTrashFile(tempDb, "new.xml", xmlContent.toString());
		command = new RepoCommand(remoteDb);
		command.setPath(tempDb.getWorkTree().getAbsolutePath() + "/new.xml")
				.setURI(rootUri).call();
		// Clone it
		File directory = createTempDirectory("testReplaceManifestBare");
		File dotmodules;
		try (Repository localDb = Git.cloneRepository().setDirectory(directory)
				.setURI(remoteDb.getDirectory().toURI().toString()).call()
				.getRepository()) {
			// The Hello file should not exist
			File hello = new File(localDb.getWorkTree(), "Hello");
			assertFalse(hello.exists(), "The Hello file shouldn't exist");
			// The Hello.txt file should exist
			File hellotxt = new File(localDb.getWorkTree(), "World.txt");
			assertTrue(hellotxt.exists(), "The World.txt file should exist");
			dotmodules = new File(localDb.getWorkTree(),
					Constants.DOT_GIT_MODULES);
		}
		// The .gitmodules file should have 'submodule "bar"' and shouldn't
		// have
		// 'submodule "foo"' lines.
		try (BufferedReader reader = Files
				.newBufferedReader(dotmodules.toPath(), UTF_8)) {
			boolean foo = false;
			boolean bar = false;
			while (true) {
				String line = reader.readLine();
				if (line == null)
					break;
				if (line.contains("submodule \"" + defaultUri + "\""))
					foo = true;
				if (line.contains("submodule \"" + notDefaultUri + "\""))
					bar = true;
			}
			assertTrue(bar, "The bar submodule should exist");
			assertFalse(foo, "The foo submodule shouldn't exist");
		}
	}

	@Test
	void testRemoveOverlappingBare() throws Exception {
		Repository remoteDb = createBareRepository();
		Repository tempDb = createWorkRepository();

		StringBuilder xmlContent = new StringBuilder();
		xmlContent.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
				.append("<manifest>")
				.append("<remote name=\"remote1\" fetch=\".\" />")
				.append("<default revision=\"master\" remote=\"remote1\" />")
				.append("<project path=\"foo/bar\" name=\"").append(groupBUri)
				.append("\" />").append("<project path=\"a\" name=\"")
				.append(groupAUri).append("\" />")
				.append("<project path=\"foo\" name=\"").append(defaultUri)
				.append("\" />").append("</manifest>");
		JGitTestUtil.writeTrashFile(tempDb, "manifest.xml",
				xmlContent.toString());
		RepoCommand command = new RepoCommand(remoteDb);
		command.setPath(
				tempDb.getWorkTree().getAbsolutePath() + "/manifest.xml")
				.setURI(rootUri).call();
		// Clone it
		File directory = createTempDirectory("testRemoveOverlappingBare");
		File dotmodules;
		try (Repository localDb = Git.cloneRepository().setDirectory(directory)
				.setURI(remoteDb.getDirectory().toURI().toString()).call()
				.getRepository()) {
			dotmodules = new File(localDb.getWorkTree(),
					Constants.DOT_GIT_MODULES);
		}

		// Check .gitmodules file
		try (BufferedReader reader = Files
				.newBufferedReader(dotmodules.toPath(), UTF_8)) {
			boolean foo = false;
			boolean foobar = false;
			boolean a = false;
			while (true) {
				String line = reader.readLine();
				if (line == null)
					break;
				if (line.contains("submodule \"" + defaultUri + "\""))
					foo = true;
				if (line.contains("submodule \"" + groupBUri + "\""))
					foobar = true;
				if (line.contains("submodule \"" + groupAUri + "\""))
					a = true;
			}
			assertTrue(foo, "The " + defaultUri + " submodule should exist");
			assertFalse(foobar,
					"The " + groupBUri + " submodule shouldn't exist");
			assertTrue(a, "The " + groupAUri + " submodule should exist");
		}
	}

	@Test
	void testIncludeTag() throws Exception {
		Repository localDb = createWorkRepository();
		Repository tempDb = createWorkRepository();

		StringBuilder xmlContent = new StringBuilder();
		xmlContent.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
				.append("<manifest>")
				.append("<include name=\"_include.xml\" />")
				.append("<default revision=\"master\" remote=\"remote1\" />")
				.append("</manifest>");
		JGitTestUtil.writeTrashFile(
				tempDb, "manifest.xml", xmlContent.toString());

		xmlContent = new StringBuilder();
		xmlContent.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
				.append("<manifest>")
				.append("<remote name=\"remote1\" fetch=\".\" />")
				.append("<default revision=\"master\" remote=\"remote1\" />")
				.append("<project path=\"foo\" name=\"")
				.append(defaultUri)
				.append("\" />")
				.append("</manifest>");
		JGitTestUtil.writeTrashFile(
				tempDb, "_include.xml", xmlContent.toString());

		RepoCommand command = new RepoCommand(localDb);
		command
				.setPath(tempDb.getWorkTree().getAbsolutePath() + "/manifest.xml")
				.setURI(rootUri)
				.call();
		File hello = new File(localDb.getWorkTree(), "foo/hello.txt");
		assertTrue(hello.exists(), "submodule should be checked out");
		try (BufferedReader reader = Files.newBufferedReader(hello.toPath(),
				UTF_8)) {
			String content = reader.readLine();
			assertEquals("master world", content, "submodule content should be as expected");
		}
	}

	@Test
	void testRemoteAlias() throws Exception {
		StringBuilder xmlContent = new StringBuilder();
		xmlContent.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
				.append("<manifest>")
				.append("<remote name=\"remote1\" fetch=\".\" alias=\"remote2\" />")
				.append("<default revision=\"master\" remote=\"remote2\" />")
				.append("<project path=\"foo\" name=\"")
				.append(defaultUri)
				.append("\" />")
				.append("</manifest>");

		Repository localDb = createWorkRepository();
		JGitTestUtil.writeTrashFile(
				localDb, "manifest.xml", xmlContent.toString());
		RepoCommand command = new RepoCommand(localDb);
		command
				.setPath(localDb.getWorkTree().getAbsolutePath() + "/manifest.xml")
				.setURI(rootUri)
				.call();
		File file = new File(localDb.getWorkTree(), "foo/hello.txt");
		assertTrue(file.exists(), "We should have foo");
	}

	@Test
	void testTargetBranch() throws Exception {
		Repository remoteDb1 = createBareRepository();
		Repository remoteDb2 = createBareRepository();
		Repository tempDb = createWorkRepository();

		StringBuilder xmlContent = new StringBuilder();
		xmlContent.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
				.append("<manifest>")
				.append("<remote name=\"remote1\" fetch=\".\" />")
				.append("<default revision=\"master\" remote=\"remote1\" />")
				.append("<project path=\"foo\" name=\"").append(defaultUri)
				.append("\" />").append("</manifest>");
		JGitTestUtil.writeTrashFile(tempDb, "manifest.xml",
				xmlContent.toString());
		RepoCommand command = new RepoCommand(remoteDb1);
		command.setPath(
				tempDb.getWorkTree().getAbsolutePath() + "/manifest.xml")
				.setURI(rootUri).setTargetBranch("test").call();
		ObjectId branchId = remoteDb1
				.resolve(Constants.R_HEADS + "test^{tree}");
		command = new RepoCommand(remoteDb2);
		command.setPath(
				tempDb.getWorkTree().getAbsolutePath() + "/manifest.xml")
				.setURI(rootUri).call();
		ObjectId defaultId = remoteDb2.resolve(Constants.HEAD + "^{tree}");
		assertEquals(
				branchId, defaultId, "The tree id of branch db and default db should be the same");
	}

	@Test
	void testRecordRemoteBranch() throws Exception {
		Repository remoteDb = createBareRepository();
		Repository tempDb = createWorkRepository();

		StringBuilder xmlContent = new StringBuilder();
		xmlContent.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
				.append("<manifest>")
				.append("<remote name=\"remote1\" fetch=\".\" />")
				.append("<default revision=\"master\" remote=\"remote1\" />")
				.append("<project path=\"with-branch\" ")
				.append("revision=\"master\" ").append("name=\"")
				.append(notDefaultUri).append("\" />")
				.append("<project path=\"with-long-branch\" ")
				.append("revision=\"refs/heads/master\" ").append("name=\"")
				.append(defaultUri).append("\" />").append("</manifest>");
		JGitTestUtil.writeTrashFile(tempDb, "manifest.xml",
				xmlContent.toString());

		RepoCommand command = new RepoCommand(remoteDb);
		command.setPath(
				tempDb.getWorkTree().getAbsolutePath() + "/manifest.xml")
				.setURI(rootUri).setRecordRemoteBranch(true).call();
		// Clone it
		File directory = createTempDirectory("testBareRepo");
		try (Repository localDb = Git.cloneRepository().setDirectory(directory)
				.setURI(remoteDb.getDirectory().toURI().toString()).call()
				.getRepository();) {
			// The .gitmodules file should exist
			File gitmodules = new File(localDb.getWorkTree(), ".gitmodules");
			assertTrue(gitmodules.exists(),
					"The .gitmodules file should exist");
			FileBasedConfig c = new FileBasedConfig(gitmodules, FS.DETECTED);
			c.load();
			assertEquals(
					"master",
					c.getString("submodule", notDefaultUri, "branch"),
					"Recording remote branches should work for short branch descriptions");
			assertEquals(
					"refs/heads/master",
					c.getString("submodule", defaultUri, "branch"),
					"Recording remote branches should work for full ref specs");
		}
	}


	@Test
	void testRecordSubmoduleLabels() throws Exception {
		Repository remoteDb = createBareRepository();
		Repository tempDb = createWorkRepository();

		StringBuilder xmlContent = new StringBuilder();
		xmlContent.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
				.append("<manifest>")
				.append("<remote name=\"remote1\" fetch=\".\" />")
				.append("<default revision=\"master\" remote=\"remote1\" />")
				.append("<project path=\"test\" ")
				.append("revision=\"master\" ").append("name=\"")
				.append(notDefaultUri).append("\" ")
				.append("groups=\"a1,a2\" />").append("</manifest>");
		JGitTestUtil.writeTrashFile(tempDb, "manifest.xml",
				xmlContent.toString());

		RepoCommand command = new RepoCommand(remoteDb);
		command.setPath(
				tempDb.getWorkTree().getAbsolutePath() + "/manifest.xml")
				.setURI(rootUri).setRecordSubmoduleLabels(true).call();
		// Clone it
		File directory = createTempDirectory("testBareRepo");
		try (Repository localDb = Git.cloneRepository().setDirectory(directory)
				.setURI(remoteDb.getDirectory().toURI().toString()).call()
				.getRepository();) {
			// The .gitattributes file should exist
			File gitattributes = new File(localDb.getWorkTree(),
					".gitattributes");
			assertTrue(gitattributes.exists(),
					"The .gitattributes file should exist");
			try (BufferedReader reader = Files
					.newBufferedReader(gitattributes.toPath(),
							UTF_8)) {
				String content = reader.readLine();
				assertEquals("/test a1 a2", content, ".gitattributes content should be as expected");
			}
		}
	}

	@Test
	void testRecordShallowRecommendation() throws Exception {
		Repository remoteDb = createBareRepository();
		Repository tempDb = createWorkRepository();

		StringBuilder xmlContent = new StringBuilder();
		xmlContent.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
				.append("<manifest>")
				.append("<remote name=\"remote1\" fetch=\".\" />")
				.append("<default revision=\"master\" remote=\"remote1\" />")
				.append("<project path=\"shallow-please\" ").append("name=\"")
				.append(defaultUri).append("\" ").append("clone-depth=\"1\" />")
				.append("<project path=\"non-shallow\" ").append("name=\"")
				.append(notDefaultUri).append("\" />").append("</manifest>");
		JGitTestUtil.writeTrashFile(tempDb, "manifest.xml",
				xmlContent.toString());

		RepoCommand command = new RepoCommand(remoteDb);
		command.setPath(
				tempDb.getWorkTree().getAbsolutePath() + "/manifest.xml")
				.setURI(rootUri).setRecommendShallow(true).call();
		// Clone it
		File directory = createTempDirectory("testBareRepo");
		try (Repository localDb = Git.cloneRepository().setDirectory(directory)
				.setURI(remoteDb.getDirectory().toURI().toString()).call()
				.getRepository();) {
			// The .gitmodules file should exist
			File gitmodules = new File(localDb.getWorkTree(), ".gitmodules");
			assertTrue(gitmodules.exists(),
					"The .gitmodules file should exist");
			FileBasedConfig c = new FileBasedConfig(gitmodules, FS.DETECTED);
			c.load();
			assertEquals("true",
					c.getString("submodule", defaultUri, "shallow"),
					"Recording shallow configuration should work");
			assertNull(c.getString("submodule", notDefaultUri, "shallow"),
					"Recording non shallow configuration should work");
		}
	}

	@Test
	void testRemoteRevision() throws Exception {
		StringBuilder xmlContent = new StringBuilder();
		xmlContent.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
				.append("<manifest>")
				.append("<remote name=\"remote1\" fetch=\".\" />")
				.append("<remote name=\"remote2\" fetch=\".\" revision=\"")
				.append(BRANCH)
				.append("\" />")
				.append("<default remote=\"remote1\" revision=\"master\" />")
				.append("<project path=\"foo\" remote=\"remote2\" name=\"")
				.append(defaultUri)
				.append("\" />")
				.append("</manifest>");
		writeTrashFile("manifest.xml", xmlContent.toString());
		RepoCommand command = new RepoCommand(db);
		command.setPath(db.getWorkTree().getAbsolutePath() + "/manifest.xml")
				.setURI(rootUri)
				.call();
		File hello = new File(db.getWorkTree(), "foo/hello.txt");
		assertContents(hello.toPath(), "branch world");
	}

	@Test
	void testDefaultRemoteRevision() throws Exception {
		StringBuilder xmlContent = new StringBuilder();
		xmlContent.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
				.append("<manifest>")
				.append("<remote name=\"remote1\" fetch=\".\" revision=\"")
				.append(BRANCH)
				.append("\" />")
				.append("<default remote=\"remote1\" />")
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
		try (BufferedReader reader = Files.newBufferedReader(hello.toPath(),
				UTF_8)) {
			String content = reader.readLine();
			assertEquals("branch world", content, "submodule content should be as expected");
		}
	}

	@Test
	void testTwoPathUseTheSameName() throws Exception {
		Repository remoteDb = createBareRepository();
		Repository tempDb = createWorkRepository();

		StringBuilder xmlContent = new StringBuilder();
		xmlContent.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
				.append("<manifest>")
				.append("<remote name=\"remote1\" fetch=\".\" />")
				.append("<default revision=\"master\" remote=\"remote1\" />")
				.append("<project path=\"path1\" ").append("name=\"")
				.append(defaultUri).append("\" />")
				.append("<project path=\"path2\" ").append("name=\"")
				.append(defaultUri).append("\" />").append("</manifest>");
		JGitTestUtil.writeTrashFile(tempDb, "manifest.xml",
				xmlContent.toString());

		RepoCommand command = new RepoCommand(remoteDb);
		command.setPath(
				tempDb.getWorkTree().getAbsolutePath() + "/manifest.xml")
				.setURI(rootUri).setRecommendShallow(true).call();
		File directory = createTempDirectory("testBareRepo");
		try (Repository localDb = Git.cloneRepository().setDirectory(directory)
				.setURI(remoteDb.getDirectory().toURI().toString()).call()
				.getRepository();) {
			File gitmodules = new File(localDb.getWorkTree(), ".gitmodules");
			assertTrue(gitmodules.exists(),
					"The .gitmodules file should exist");
			FileBasedConfig c = new FileBasedConfig(gitmodules, FS.DETECTED);
			c.load();
			assertEquals("path1",
					c.getString("submodule", defaultUri + "/path1", "path"),
					"A module should exist for path1");
			assertEquals("path2",
					c.getString("submodule", defaultUri + "/path2", "path"),
					"A module should exist for path2");
		}
	}

	@Test
	void testInvalidPath() throws Exception {
		Repository remoteDb = createBareRepository();
		Repository tempDb = createWorkRepository();

		StringBuilder xmlContent = new StringBuilder();
		xmlContent.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
				.append("<manifest>")
				.append("<remote name=\"remote1\" fetch=\".\" />")
				.append("<default revision=\"master\" remote=\"remote1\" />")
				.append("<project path=\".\" ").append("name=\"")
				.append(defaultUri).append("\" />").append("</manifest>");
		JGitTestUtil.writeTrashFile(tempDb, "manifest.xml",
				xmlContent.toString());

		RepoCommand command = new RepoCommand(remoteDb);
		command.setPath(
				tempDb.getWorkTree().getAbsolutePath() + "/manifest.xml")
				.setURI(rootUri).setRecommendShallow(true);
		assertThrows(ManifestErrorException.class, () -> command.call());
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
				rootUri = defaultUri.substring(0, start) + "manifest";
				defaultUri = defaultUri.substring(start);
				notDefaultUri = notDefaultUri.substring(start);
				groupAUri = groupAUri.substring(start);
				groupBUri = groupBUri.substring(start);
				return;
			}
			start = newStart;
		}
	}

	void testRelative(String a, String b, String want) {
		String got = RepoCommand.relativize(URI.create(a), URI.create(b)).toString();

		if (!got.equals(want)) {
			fail(String.format("relative('%s', '%s') = '%s', want '%s'", a, b, got, want));
		}
	}

	@Test
	void relative() {
		testRelative("a/b/", "a/", "../");
		// Normalization:
		testRelative("a/p/..//b/", "a/", "../");
		testRelative("a/b", "a/", "");
		testRelative("a/", "a/b/", "b/");
		testRelative("a/", "a/b", "b");
		testRelative("/a/b/c", "/b/c", "../../b/c");
		testRelative("/abc", "bcd", "bcd");
		testRelative("abc", "def", "def");
		testRelative("abc", "/bcd", "/bcd");
		testRelative("http://a", "a/b", "a/b");
		testRelative("http://base.com/a/", "http://child.com/a/b", "http://child.com/a/b");
		testRelative("http://base.com/a/", "http://base.com/a/b", "b");
	}
}
