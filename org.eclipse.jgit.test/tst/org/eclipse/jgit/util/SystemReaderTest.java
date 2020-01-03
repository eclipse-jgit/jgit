/*
 * Copyright (C) 2019, Matthias Sohn <matthias.sohn@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
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
