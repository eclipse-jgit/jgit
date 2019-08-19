/*
 * Copyright (C) 2012, Matthias Sohn <matthias.sohn@sap.com>
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
