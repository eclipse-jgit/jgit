/*
 * Copyright (C) 2012, Matthias Sohn <matthias.sohn@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.pgm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.CLIRepositoryTestCase;
import org.eclipse.jgit.util.SystemReader;
import org.junit.Before;
import org.junit.Test;

public class ConfigTest extends CLIRepositoryTestCase {
	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		try (Git git = new Git(db)) {
			git.commit().setMessage("initial commit").call();
		}
	}

	@SuppressWarnings("boxing")
	@Test
	public void testListConfig() throws Exception {
		boolean isWindows = SystemReader.getInstance().getProperty("os.name")
				.startsWith("Windows");
		boolean isMac = SystemReader.getInstance().getProperty("os.name")
				.equals("Mac OS X");

		String[] output = execute("git config --list");

		Map<String, String> options = parseOptions(output);

		assertEquals(!isWindows, Boolean.valueOf(options.get("core.filemode")));
		assertTrue((Boolean.valueOf(options.get("core.logallrefupdates"))));
		if (isMac) {
			assertTrue(
					(Boolean.valueOf(options.get("core.precomposeunicode"))));
		}
		assertEquals(Integer.valueOf(0),
				Integer.valueOf(options.get("core.repositoryformatversion")));
	}

	private Map<String, String> parseOptions(String[] output) {
		Map<String, String> options = new HashMap<>();
		Arrays.stream(output).forEachOrdered(s -> {
			int p = s.indexOf('=');
			if (p == -1) {
				return;
			}
			String key = s.substring(0, p);
			String value = s.substring(p + 1);
			options.put(key, value);
		});
		return options;
	}

}
