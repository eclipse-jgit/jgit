/*
 * Copyright (C) 2016, Matthias Sohn <matthias.sohn@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.file;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.test.resources.SampleDataRepositoryTestCase;
import org.junit.Test;

public class AutoGcTest extends GcTestCase {

	@Test
	public void testNotTooManyLooseObjects() {
		assertFalse("should not find too many loose objects",
				gc.tooManyLooseObjects());
	}

	@Test
	public void testTooManyLooseObjects() throws Exception {
		FileBasedConfig c = repo.getConfig();
		c.setInt(ConfigConstants.CONFIG_GC_SECTION, null,
				ConfigConstants.CONFIG_KEY_AUTO, 255);
		c.save();
		commitChain(10, 50);
		assertTrue("should find too many loose objects",
				gc.tooManyLooseObjects());
	}

	@Test
	public void testNotTooManyPacks() {
		assertFalse("should not find too many packs", gc.tooManyPacks());
	}

	@Test
	public void testTooManyPacks() throws Exception {
		FileBasedConfig c = repo.getConfig();
		c.setInt(ConfigConstants.CONFIG_GC_SECTION, null,
				ConfigConstants.CONFIG_KEY_AUTOPACKLIMIT, 1);
		c.save();
		SampleDataRepositoryTestCase.copyCGitTestPacks(repo);

		assertTrue("should find too many packs", gc.tooManyPacks());
	}
}
