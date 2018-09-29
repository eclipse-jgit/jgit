/*
 * Copyright (C) 2010, Google Inc.
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

package org.eclipse.jgit.http.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.resolver.FileResolver;
import org.eclipse.jgit.transport.resolver.RepositoryResolver;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;
import org.eclipse.jgit.util.FileUtils;
import org.junit.Test;

public class FileResolverTest extends LocalDiskRepositoryTestCase {
	@Test
	public void testUnreasonableNames() throws ServiceNotEnabledException {
		assertUnreasonable("");
		assertUnreasonable("a\\b");
		assertUnreasonable("../b");
		assertUnreasonable("a/../b");
		assertUnreasonable("a/./b");
		assertUnreasonable("a//b");

		if (new File("/foo").isAbsolute())
			assertUnreasonable("/foo");

		if (new File("//server/share").isAbsolute())
			assertUnreasonable("//server/share");

		if (new File("C:/windows").isAbsolute())
			assertUnreasonable("C:/windows");
	}

	private static void assertUnreasonable(String name)
			throws ServiceNotEnabledException {
		FileResolver<RepositoryResolver> r = new FileResolver<>(
				new File("."), false);
		try {
			r.open(null, name);
			fail("Opened unreasonable name \"" + name + "\"");
		} catch (RepositoryNotFoundException e) {
			assertEquals("repository not found: " + name, e.getMessage());
			assertNull("has no cause", e.getCause());
		}
	}

	@Test
	public void testExportOk() throws IOException {
		final Repository a = createBareRepository();
		final String name = a.getDirectory().getName();
		final File base = a.getDirectory().getParentFile();
		final File export = new File(a.getDirectory(), "git-daemon-export-ok");
		FileResolver<RepositoryResolver> resolver;

		assertFalse("no git-daemon-export-ok", export.exists());
		resolver = new FileResolver<>(base, false /*
																	 * require
																	 * flag
																	 */);
		try {
			resolver.open(null, name);
			fail("opened non-exported repository");
		} catch (ServiceNotEnabledException e) {
			assertEquals("Service not enabled", e.getMessage());
		}

		resolver = new FileResolver<>(base, true /*
																	 * export
																	 * all
																	 */);
		try {
			resolver.open(null, name).close();
		} catch (ServiceNotEnabledException e) {
			fail("did not honor export-all flag");
		}

		FileUtils.createNewFile(export);
		resolver = new FileResolver<>(base, false /*
																	 * require
																	 * flag
																	 */);
		try {
			resolver.open(null, name).close();
		} catch (ServiceNotEnabledException e) {
			fail("did not honor git-daemon-export-ok");
		}
	}

	@Test
	public void testNotAGitRepository() throws IOException,
			ServiceNotEnabledException {
		final Repository a = createBareRepository();
		final String name = a.getDirectory().getName() + "-not-a-git";
		final File base = a.getDirectory().getParentFile();
		FileResolver<RepositoryResolver> resolver = new FileResolver<>(
				base, false);

		try {
			resolver.open(null, name);
			fail("opened non-git repository");
		} catch (RepositoryNotFoundException e) {
			assertEquals("repository not found: " + name, e.getMessage());

			Throwable why = e.getCause();
			assertNotNull("has cause", why);
			assertEquals("repository not found: "
					+ new File(base, name).getPath(), why.getMessage());
		}
	}
}
