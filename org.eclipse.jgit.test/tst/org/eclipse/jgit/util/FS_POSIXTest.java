/*
 * Copyright (C) 2019, Vishal Devgire <vishaldevgire@gmail.com>
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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jgit.junit.MockSystemReader;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class FS_POSIXTest {
	private SystemReader originalSystemReaderInstance;

	private FileBasedConfig systemConfig;

	private FileBasedConfig userConfig;

	private Path tmp;

	@Before
	public void setUp() throws Exception {
		tmp = Files.createTempDirectory("jgit_test_");
		MockSystemReader mockSystemReader = new MockSystemReader();
		SystemReader.setInstance(mockSystemReader);

		// Measure timer resolution before the test to avoid time critical tests
		// are affected by time needed for measurement.
		// The MockSystemReader must be configured first since we need to use
		// the same one here
		FS.getFileStoreAttributes(tmp.getParent());
		systemConfig = new FileBasedConfig(
				new File(tmp.toFile(), "systemgitconfig"), FS.DETECTED);
		userConfig = new FileBasedConfig(systemConfig,
				new File(tmp.toFile(), "usergitconfig"), FS.DETECTED);
		// We have to set autoDetach to false for tests, because tests expect to
		// be able to clean up by recursively removing the repository, and
		// background GC might be in the middle of writing or deleting files,
		// which would disrupt this.
		userConfig.setBoolean(ConfigConstants.CONFIG_GC_SECTION, null,
				ConfigConstants.CONFIG_KEY_AUTODETACH, false);
		userConfig.save();
		mockSystemReader.setSystemGitConfig(systemConfig);
		mockSystemReader.setUserGitConfig(userConfig);

		originalSystemReaderInstance = SystemReader.getInstance();
		SystemReader.setInstance(mockSystemReader);
	}

	@After
	public void tearDown() throws IOException {
		SystemReader.setInstance(originalSystemReaderInstance);
		FileUtils.delete(tmp.toFile(), FileUtils.RECURSIVE | FileUtils.RETRY);
	}

	@Test
	public void supportsAtomicCreateNewFile_shouldReturnSupportedAsDefault() {
		assertTrue(new FS_POSIX().supportsAtomicCreateNewFile());
	}

	@Test
	public void supportsAtomicCreateNewFile_shouldReturnTrueIfFlagIsSetInUserConfig() {
		setAtomicCreateCreationFlag(userConfig, "true");
		assertTrue(new FS_POSIX().supportsAtomicCreateNewFile());
	}

	@Test
	public void supportsAtomicCreateNewFile_shouldReturnTrueIfFlagIsSetInSystemConfig() {
		setAtomicCreateCreationFlag(systemConfig, "true");
		assertTrue(new FS_POSIX().supportsAtomicCreateNewFile());
	}

	@Test
	public void supportsAtomicCreateNewFile_shouldReturnFalseIfFlagUnsetInUserConfig() {
		setAtomicCreateCreationFlag(userConfig, "false");
		assertFalse(new FS_POSIX().supportsAtomicCreateNewFile());
	}

	@Test
	public void supportsAtomicCreateNewFile_shouldReturnFalseIfFlagUnsetInSystemConfig() {
		setAtomicCreateCreationFlag(systemConfig, "false");
		assertFalse(new FS_POSIX().supportsAtomicCreateNewFile());
	}

	private void setAtomicCreateCreationFlag(FileBasedConfig config,
			String value) {
		config.setString(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_SUPPORTSATOMICFILECREATION, value);
	}
}
