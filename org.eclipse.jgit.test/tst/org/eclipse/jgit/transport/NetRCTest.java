/*
 * Copyright (C) 2014, Alexey Kuznetsov <axet@me.com>
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

package org.eclipse.jgit.transport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.util.FileUtils;
import org.junit.Before;
import org.junit.Test;

public class NetRCTest extends RepositoryTestCase {
	private File home;

	private NetRC netrc;

	private File configFile;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();

		home = new File(trash, "home");
		FileUtils.mkdir(home);

		configFile = new File(home, ".netrc");
	}

	private void config(final String data) throws IOException {
		final OutputStreamWriter fw = new OutputStreamWriter(
				new FileOutputStream(configFile), "UTF-8");
		fw.write(data);
		fw.close();
	}

	@Test
	public void testNetRCFile() throws Exception {
		config("machine my.host login test password 2222"
				+ "\n"

				+ "#machine ignore.host.example login ignoreuser password 5555"
				+ "\n"

				+ "machine twolinehost.example #login kuznetsov.alexey password 5555"
				+ "\n"

				+ "login twologin password 6666"
				+ "\n"

				+ "machine afterlinehost.example login test1 password 8888"
				+ "\n"

				+ "machine macdef.example login test7 password 7777 macdef mac1"
				+ "\n"

				+ "init +apc 1" + "\n"

				+ "init2 +apc 2" + "\n");

		netrc = new NetRC(configFile);

		assertNull(netrc.getEntry("ignore.host.example"));

		assertNull(netrc.getEntry("cignore.host.example"));

		assertEquals("twologin", netrc.getEntry("twolinehost.example").login);
		assertEquals("6666", new String(
				netrc.getEntry("twolinehost.example").password));

		assertEquals("test1", netrc.getEntry("afterlinehost.example").login);
		assertEquals("8888", new String(
				netrc.getEntry("afterlinehost.example").password));

		// macdef test
		assertEquals("test7", netrc.getEntry("macdef.example").login);
		assertEquals("7777", new String(
				netrc.getEntry("macdef.example").password));
		assertEquals("init +apc 1" + "\n" + "init2 +apc 2" + "\n",
				netrc.getEntry("macdef.example").macbody);
	}

	@Test
	public void testNetRCDefault() throws Exception {
		config("machine my.host login test password 2222"
				+ "\n"

				+ "#machine ignore.host.example login ignoreuser password 5555"
				+ "\n"

				+ "machine twolinehost.example #login kuznetsov.alexey password 5555"
				+ "\n"

				+ "login twologin password 6666"
				+ "\n"

				+ "machine afterlinehost.example login test1 password 8888"
				+ "\n"

				+ "machine macdef.example login test7 password 7777 macdef mac1"
				+ "\n"

				+ "init +apc 1" + "\n"

				+ "init2 +apc 2" + "\n"

				+ "\n"

				+ "default login test5 password 3333" + "\n"

		);

		netrc = new NetRC(configFile);

		// default test
		assertEquals("test5", netrc.getEntry("ignore.host.example").login);
		assertEquals("3333", new String(
				netrc.getEntry("ignore.host.example").password));

		// default test
		assertEquals("test5", new String(
				netrc.getEntry("ignore.host.example").login));
		assertEquals("3333", new String(
				netrc.getEntry("ignore.host.example").password));

		// default test
		assertEquals("test5", netrc.getEntry("cignore.host.example").login);
		assertEquals("3333", new String(
				netrc.getEntry("cignore.host.example").password));

		assertEquals("twologin", netrc.getEntry("twolinehost.example").login);
		assertEquals("6666", new String(
				netrc.getEntry("twolinehost.example").password));

		assertEquals("test1", netrc.getEntry("afterlinehost.example").login);
		assertEquals("8888", new String(
				netrc.getEntry("afterlinehost.example").password));

		// macdef test
		assertEquals("test7", netrc.getEntry("macdef.example").login);
		assertEquals("7777", new String(
				netrc.getEntry("macdef.example").password));
		assertEquals("init +apc 1" + "\n" + "init2 +apc 2" + "\n",
				netrc.getEntry("macdef.example").macbody);
	}
}
