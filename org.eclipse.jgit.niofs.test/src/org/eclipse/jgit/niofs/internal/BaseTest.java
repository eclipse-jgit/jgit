/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.internal;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.io.IOUtils;
import org.assertj.core.api.Assertions;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.niofs.internal.op.Git;
import org.eclipse.jgit.niofs.internal.op.commands.Commit;
import org.eclipse.jgit.niofs.internal.op.commands.ListRefs;
import org.eclipse.jgit.niofs.internal.op.model.CommitInfo;
import org.eclipse.jgit.niofs.internal.op.model.DefaultCommitContent;
import org.eclipse.jgit.util.FS_POSIX;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.jgit.util.ProcessResult;
import org.junit.After;
import org.junit.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.stream.Collectors.toMap;
import static org.eclipse.jgit.niofs.internal.JGitFileSystemProviderConfiguration.GIT_DAEMON_ENABLED;
import static org.eclipse.jgit.niofs.internal.JGitFileSystemProviderConfiguration.GIT_NIO_DIR;
import static org.eclipse.jgit.niofs.internal.JGitFileSystemProviderConfiguration.GIT_SSH_ENABLED;

public abstract class BaseTest {

	private static final Logger logger = LoggerFactory.getLogger(BaseTest.class);
	protected static final Map<String, Object> EMPTY_ENV = Collections.emptyMap();

	public IOUtil util;
	public JGitFileSystemProvider provider;

	static class TestFile {

		final String path;
		final String content;

		TestFile(final String path, final String content) {
			this.path = path;
			this.content = content;
		}
	}

	@After
	@Before
	public void clean() {
		if (util != null) {
			cleanup();
		}
		util = new IOUtil();
		try {
			provider = new JGitFileSystemProvider(getGitPreferences());
		} catch (IOException e) {
		}
	}

	private void cleanup() {
		if (provider == null) {
			// this would mean that setup failed. no need to clean up.
			return;
		}

		provider.shutdown();

		if (provider.getGitRepoContainerDir() != null && provider.getGitRepoContainerDir().exists()) {
			try {
				FileUtils.delete(provider.getGitRepoContainerDir(),
								 FileUtils.RECURSIVE | FileUtils.SKIP_MISSING | FileUtils.IGNORE_ERRORS);
			} catch (IOException e) {
			}
		}
		util.cleanup();
	}

	/*
	 * Default Git preferences suitable for most of the tests. If specific test
	 * needs some custom configuration, it needs to override this method and provide
	 * own map of preferences.
	 */
	public Map<String, String> getGitPreferences() {
		Map<String, String> gitPrefs = new HashMap<>();
		// disable the daemons by default as they not needed in most of the cases
		gitPrefs.put(GIT_DAEMON_ENABLED, "false");
		gitPrefs.put(GIT_SSH_ENABLED, "false");
		final String file;
		try {
			file = util.createTempDirectory().getAbsolutePath();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		gitPrefs.put(GIT_NIO_DIR, file);
		return gitPrefs;
	}

	static String multiline(String prefix, String... lines) {
		return Arrays.stream(lines).map(s -> prefix + s).reduce((s1, s2) -> s1 + "\n" + s2).orElse("");
	}

	static TestFile content(final String path, final String content) {
		return new TestFile(path, content);
	}

	static void commit(final Git origin, final String branchName, final String message, final TestFile... testFiles)
			throws IOException {
		final Map<String, File> data = Arrays.stream(testFiles).collect(toMap(f -> f.path, f -> tmpFile(f.content)));
		new Commit(origin, branchName, "name", "name@example.com", message, null, null, false, data).execute();
	}

	static File tmpFile(final String content) {
		try {
			return tempFile(content);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static File tempFile(final String content) throws IOException {
		final File file = File.createTempFile("bar", "foo");
		final OutputStream out = new FileOutputStream(file);

		if (content != null && !content.isEmpty()) {
			out.write(content.getBytes());
			out.flush();
		}

		out.close();
		return file;
	}

	static File tempFile(final byte[] content) throws IOException {
		final File file = File.createTempFile("bar", "foo");
		final FileOutputStream out = new FileOutputStream(file);

		if (content != null && content.length > 0) {
			out.write(content);
			out.flush();
		}

		out.close();
		return file;
	}

	public static Git setupGit(final File tempDir) throws IOException, GitAPIException {

		final Git git = Git.createRepository(tempDir);

		new Commit(git, "master", new CommitInfo(null, "name", "name@example.com", "cool1", null, null), false, null,
				   new DefaultCommitContent(new HashMap<String, File>() {
					   {
						   put("file1.txt", tempFile("content"));
						   put("file2.txt", tempFile("content2"));
					   }
				   })).execute();

		return git;
	}

	static Ref branch(Git origin, String source, String target) throws Exception {
		final Repository repo = origin.getRepository();
		return org.eclipse.jgit.api.Git.wrap(repo).branchCreate().setName(target).setStartPoint(source).call();
	}

	static List<Ref> listRefs(final Git cloned) {
		return new ListRefs(cloned.getRepository()).execute();
	}

	/**
	 * Creates mock hook in defined hooks directory.
	 * @param hooksDirectory Directory in which mock hook is created.
	 * @param hookName Name of the created hook. This is the filename of
	 * created hook file.
	 * @throws FileNotFoundException
	 * @throws UnsupportedEncodingException
	 */
	static void writeMockHook(final File hooksDirectory, final String hookName)
			throws FileNotFoundException, UnsupportedEncodingException {
		final PrintWriter writer = new PrintWriter(new File(hooksDirectory, hookName), "UTF-8");
		writer.println("# something");
		writer.close();
	}

	static int findFreePort() {
		int port = 0;
		try {
			ServerSocket server = new ServerSocket(0);
			port = server.getLocalPort();
			server.close();
		} catch (IOException e) {
			throw new RuntimeException("Can't find free port!");
		}
		logger.debug("Found free port " + port);
		return port;
	}

	/**
	 * Tests if defined hook was executed or not.
	 * @param gitRepoName Name of test git repository that is created for
	 * committing changes.
	 * @param testedHookName Tested hook name. This hook is checked for its
	 * execution.
	 * @param wasExecuted Expected hook execution state. If true, test expects
	 * that defined hook is executed. If false, test expects
	 * that defined hook is not executed.
	 * @throws IOException
	 */
	static void testHook(final JGitFileSystemProvider provider, final String gitRepoName, final String testedHookName, final boolean wasExecuted) throws IOException {
		final URI newRepo = URI.create("git://" + gitRepoName);

		final AtomicBoolean hookExecuted = new AtomicBoolean(false);
		final FileSystem fs = provider.newFileSystem(newRepo, EMPTY_ENV);

		provider.setDetectedFS(new FS_POSIX() {
			@Override
			public ProcessResult runHookIfPresent(Repository repox, String hookName, String[] args)
					throws JGitInternalException {
				if (hookName.equals(testedHookName)) {
					hookExecuted.set(true);
				}
				return new ProcessResult(ProcessResult.Status.OK);
			}
		});

		Assertions.assertThat(fs).isNotNull();

		final Path path = provider.getPath(URI.create("git://user_branch@" + gitRepoName + "/some/path/myfile.txt"));

		final OutputStream outStream = provider.newOutputStream(path);
		Assertions.assertThat(outStream).isNotNull();
		outStream.write("my cool content".getBytes());
		outStream.close();

		final InputStream inStream = provider.newInputStream(path);

		final String content = new Scanner(inStream).useDelimiter("\\A").next();

		inStream.close();

		Assertions.assertThat(content).isNotNull().isEqualTo("my cool content");

		if (wasExecuted) {
			Assertions.assertThat(hookExecuted.get()).isTrue();
		} else {
			Assertions.assertThat(hookExecuted.get()).isFalse();
		}
	}

	public PersonIdent getAuthor() {
		return new PersonIdent("user", "user@example.com");
	}

	public static byte[] loadImage(final String path) throws IOException {
		final InputStream stream = BaseTest.class.getClassLoader().getResourceAsStream(path);
		StringWriter writer = new StringWriter();
		IOUtils.copy(stream, writer, Charset.defaultCharset());
		return writer.toString().getBytes();
	}
}
