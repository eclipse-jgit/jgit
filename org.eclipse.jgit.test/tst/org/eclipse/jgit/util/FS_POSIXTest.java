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

import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class FS_POSIXTest {
	private SystemReader originalSystemReaderInstance;

	private FileBasedConfig mockSystemConfig;

	private FileBasedConfig mockUserConfig;

	@Before
	public void setUp() throws Exception {
		SystemReader systemReader = Mockito.mock(SystemReader.class);

		originalSystemReaderInstance = SystemReader.getInstance();
		SystemReader.setInstance(systemReader);

		mockSystemConfig = mock(FileBasedConfig.class);
		mockUserConfig = mock(FileBasedConfig.class);
		when(systemReader.openSystemConfig(any(), any()))
				.thenReturn(mockSystemConfig);
		when(systemReader.openUserConfig(any(), any()))
				.thenReturn(mockUserConfig);

		when(mockSystemConfig.getString(ConfigConstants.CONFIG_CORE_SECTION,
				null, ConfigConstants.CONFIG_KEY_SUPPORTSATOMICFILECREATION))
						.thenReturn(null);
	}

	@After
	public void tearDown() {
		SystemReader.setInstance(originalSystemReaderInstance);
	}

	@Test
	public void supportsAtomicCreateNewFile_shouldReturnSupportedAsDefault() {
		assertTrue(new FS_POSIX().supportsAtomicCreateNewFile());
	}

	@Test
	public void supportsAtomicCreateNewFile_shouldReturnTrueIfFlagIsSetInUserConfig() {
		setAtomicCreateCreationFlag(mockUserConfig, "true");
		assertTrue(new FS_POSIX().supportsAtomicCreateNewFile());
	}

	@Test
	public void supportsAtomicCreateNewFile_shouldReturnTrueIfFlagIsSetInSystemConfig() {
		setAtomicCreateCreationFlag(mockSystemConfig, "true");
		assertTrue(new FS_POSIX().supportsAtomicCreateNewFile());
	}

	@Test
	public void supportsAtomicCreateNewFile_shouldReturnFalseIfFlagUnsetInUserConfig() {
		setAtomicCreateCreationFlag(mockUserConfig, "false");
		assertFalse(new FS_POSIX().supportsAtomicCreateNewFile());
	}

	@Test
	public void supportsAtomicCreateNewFile_shouldReturnFalseIfFlagUnsetInSystemConfig() {
		setAtomicCreateCreationFlag(mockSystemConfig, "false");
		assertFalse(new FS_POSIX().supportsAtomicCreateNewFile());
	}

	private void setAtomicCreateCreationFlag(FileBasedConfig config,
			String value) {
		when(config.getString(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_SUPPORTSATOMICFILECREATION))
						.thenReturn(value);
	}
}
