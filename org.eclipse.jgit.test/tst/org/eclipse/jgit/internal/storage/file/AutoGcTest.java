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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.test.resources.SampleDataRepositoryTestCase;
import org.junit.jupiter.api.Test;

public class AutoGcTest extends GcTestCase {

	@Test
	void testNotTooManyLooseObjects() {
		assertFalse(gc.tooManyLooseObjects(),
				"should not find too many loose objects");
	}

	@Test
	void testTooManyLooseObjects() throws Exception {
		FileBasedConfig c = repo.getConfig();
		c.setInt(ConfigConstants.CONFIG_GC_SECTION, null,
				ConfigConstants.CONFIG_KEY_AUTO, 255);
		c.save();
		commitChain(10, 50);
		assertTrue(gc.tooManyLooseObjects(),
				"should find too many loose objects");
	}

	@Test
	void testNotTooManyPacks() {
		assertFalse(gc.tooManyPacks(), "should not find too many packs");
	}

	@Test
	void testTooManyPacks() throws Exception {
		FileBasedConfig c = repo.getConfig();
		c.setInt(ConfigConstants.CONFIG_GC_SECTION, null,
				ConfigConstants.CONFIG_KEY_AUTOPACKLIMIT, 1);
		c.save();
		SampleDataRepositoryTestCase.copyCGitTestPacks(repo);

		assertTrue(gc.tooManyPacks(), "should find too many packs");
	}
}
