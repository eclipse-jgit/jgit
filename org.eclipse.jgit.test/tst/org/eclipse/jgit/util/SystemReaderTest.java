/*
 * Copyright (C) 2019, Matthias Sohn <matthias.sohn@sap.com>
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
package org.eclipse.jgit.util;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class SystemReaderTest {
	private Path trash;

	private Path mockSystemConfig;

	private Path mockUserConfig;

	@Mock
	private FS fs;

	@Before
	public void setup() throws Exception {
		trash = Files.createTempDirectory("jgit_test");
		mockSystemConfig = trash.resolve("systemgitconfig");
		Files.write(mockSystemConfig, "[core]\n  trustFolderStat = false\n"
				.getBytes(StandardCharsets.UTF_8));
		mockUserConfig = trash.resolve(".gitconfig");
		Files.write(mockUserConfig,
				"[core]\n  bare = false\n".getBytes(StandardCharsets.UTF_8));
		when(fs.getGitSystemConfig()).thenReturn(mockSystemConfig.toFile());
		when(fs.userHome()).thenReturn(trash.toFile());
		SystemReader.setInstance(null);
	}

	@After
	public void teardown() throws Exception {
		FileUtils.delete(trash.toFile(), FileUtils.RECURSIVE | FileUtils.RETRY);
	}

	@Test
	public void openSystemConfigReturnsDifferentInstances() throws Exception {
		FileBasedConfig system1 = SystemReader.getInstance()
				.openSystemConfig(null, fs);
		system1.load();
		assertEquals("false",
				system1.getString("core", null, "trustFolderStat"));

		FileBasedConfig system2 = SystemReader.getInstance()
				.openSystemConfig(null, fs);
		system2.load();
		assertEquals("false",
				system2.getString("core", null, "trustFolderStat"));

		system1.setString("core", null, "trustFolderStat", "true");
		assertEquals("true",
				system1.getString("core", null, "trustFolderStat"));
		assertEquals("false",
				system2.getString("core", null, "trustFolderStat"));
	}

	@Test
	public void openUserConfigReturnsDifferentInstances() throws Exception {
		FileBasedConfig user1 = SystemReader.getInstance().openUserConfig(null,
				fs);
		user1.load();
		assertEquals("false", user1.getString("core", null, "bare"));

		FileBasedConfig user2 = SystemReader.getInstance().openUserConfig(null,
				fs);
		user2.load();
		assertEquals("false", user2.getString("core", null, "bare"));

		user1.setString("core", null, "bare", "true");
		assertEquals("true", user1.getString("core", null, "bare"));
		assertEquals("false", user2.getString("core", null, "bare"));
	}
}
