/*
 * Copyright (C) 2019 Nail Samatov <sanail@yandex.ru>
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FilePermission;
import java.io.IOException;
import java.lang.reflect.ReflectPermission;
import java.nio.file.Files;
import java.security.Permission;
import java.security.SecurityPermission;
import java.util.ArrayList;
import java.util.List;
import java.util.PropertyPermission;
import java.util.logging.LoggingPermission;

import javax.security.auth.AuthPermission;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.junit.JGitTestUtil;
import org.eclipse.jgit.junit.MockSystemReader;
import org.eclipse.jgit.junit.SeparateClassloaderTestRunner;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.jgit.util.SystemReader;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * <p>
 * Tests if jgit works if SecurityManager is enabled.
 * </p>
 *
 * <p>
 * Note: JGit's classes shouldn't be used before SecurityManager is configured.
 * If you use some JGit's class before SecurityManager is replaced then part of
 * the code can be invoked outside of our custom SecurityManager and this test
 * becomes useless.
 * </p>
 *
 * <p>
 * For example the class {@link org.eclipse.jgit.util.FS} is used widely in jgit
 * sources. It contains DETECTED static field. At the first usage of the class
 * FS the field DETECTED is initialized and during initialization many system
 * operations that SecurityManager can forbid are invoked.
 * </p>
 *
 * <p>
 * For this reason this test doesn't extend LocalDiskRepositoryTestCase (it uses
 * JGit's classes in setUp() method) and other JGit's utility classes. It's done
 * to affect SecurityManager as less as possible.
 * </p>
 *
 * <p>
 * We use SeparateClassloaderTestRunner to isolate FS.DETECTED field
 * initialization between different tests run.
 * </p>
 */
@RunWith(SeparateClassloaderTestRunner.class)
public class SecurityManagerTest {
	private File root;

	private SecurityManager originalSecurityManager;

	private List<Permission> permissions = new ArrayList<>();

	@Before
	public void setUp() throws Exception {
		// Create working directory
		SystemReader.setInstance(new MockSystemReader());
		root = Files.createTempDirectory("jgit-security").toFile();

		// Add system permissions
		permissions.add(new RuntimePermission("*"));
		permissions.add(new SecurityPermission("*"));
		permissions.add(new AuthPermission("*"));
		permissions.add(new ReflectPermission("*"));
		permissions.add(new PropertyPermission("*", "read,write"));
		permissions.add(new LoggingPermission("control", null));

		permissions.add(new FilePermission(
				System.getProperty("java.home") + "/-", "read"));

		String tempDir = System.getProperty("java.io.tmpdir");
		permissions.add(new FilePermission(tempDir, "read,write,delete"));
		permissions
				.add(new FilePermission(tempDir + "/-", "read,write,delete"));

		// Add permissions to dependent jar files.
		String classPath = System.getProperty("java.class.path");
		if (classPath != null) {
			for (String path : classPath.split(File.pathSeparator)) {
				permissions.add(new FilePermission(path, "read"));
			}
		}
		// Add permissions to jgit class files.
		String jgitSourcesRoot = new File(System.getProperty("user.dir"))
				.getParent();
		permissions.add(new FilePermission(jgitSourcesRoot + "/-", "read"));

		// Add permissions to working dir for jgit. Our git repositories will be
		// initialized and cloned here.
		permissions.add(new FilePermission(root.getPath() + "/-",
				"read,write,delete,execute"));

		// Replace Security Manager
		originalSecurityManager = System.getSecurityManager();
		System.setSecurityManager(new SecurityManager() {

			@Override
			public void checkPermission(Permission requested) {
				for (Permission permission : permissions) {
					if (permission.implies(requested)) {
						return;
					}
				}

				super.checkPermission(requested);
			}
		});
	}

	@After
	public void tearDown() throws Exception {
		System.setSecurityManager(originalSecurityManager);

		// Note: don't use this method before security manager is replaced in
		// setUp() method. The method uses FS.DETECTED internally and can affect
		// the test.
		FileUtils.delete(root, FileUtils.RECURSIVE | FileUtils.RETRY);
	}

	@Test
	public void testInitAndClone() throws IOException, GitAPIException {
		File remote = new File(root, "remote");
		File local = new File(root, "local");

		try (Git git = Git.init().setDirectory(remote).call()) {
			JGitTestUtil.write(new File(remote, "hello.txt"), "Hello world!");
			git.add().addFilepattern(".").call();
			git.commit().setMessage("Initial commit").call();
		}

		try (Git git = Git.cloneRepository().setURI(remote.toURI().toString())
				.setDirectory(local).call()) {
			assertTrue(new File(local, ".git").exists());

			JGitTestUtil.write(new File(local, "hi.txt"), "Hi!");
			git.add().addFilepattern(".").call();
			RevCommit commit1 = git.commit().setMessage("Commit on local repo")
					.call();
			assertEquals("Commit on local repo", commit1.getFullMessage());
			assertNotNull(TreeWalk.forPath(git.getRepository(), "hello.txt",
					commit1.getTree()));
		}

	}

}
