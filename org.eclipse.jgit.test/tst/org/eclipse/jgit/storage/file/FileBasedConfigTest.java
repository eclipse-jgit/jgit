/*
 * Copyright (C) 2012, Marc Strapetz
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
package org.eclipse.jgit.storage.file;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.util.FileUtils.pathToString;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.StringTokenizer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.junit.MockSystemReader;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.SystemReader;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class FileBasedConfigTest {

	private static final String USER = "user";

	private static final String NAME = "name";

	private static final String EMAIL = "email";

	private static final String ALICE = "Alice";

	private static final String BOB = "Bob";

	private static final String ALICE_EMAIL = "alice@home";

	private static final String CONTENT1 = "[" + USER + "]\n\t" + NAME + " = "
			+ ALICE + "\n";

	private static final String CONTENT2 = "[" + USER + "]\n\t" + NAME + " = "
			+ BOB + "\n";

	private static final String CONTENT3 = "[" + USER + "]\n\t" + NAME + " = "
			+ ALICE + "\n" + "[" + USER + "]\n\t" + EMAIL + " = " + ALICE_EMAIL;

	private Path trash;

	private MockSystemReader mockSystemReader;

	@Before
	public void setUp() throws Exception {
		mockSystemReader = new MockSystemReader();
		SystemReader.setInstance(mockSystemReader);
		trash = Files.createTempDirectory("tmp_");
	}

	@After
	public void tearDown() throws Exception {
		FileUtils.delete(trash.toFile(),
				FileUtils.RECURSIVE | FileUtils.SKIP_MISSING | FileUtils.RETRY);
	}

	@Test
	public void testSystemEncoding() throws IOException, ConfigInvalidException {
		final Path file = createFile(CONTENT1.getBytes(UTF_8));
		final FileBasedConfig config = new FileBasedConfig(file.toFile(),
				FS.DETECTED);
		config.load();
		assertEquals(ALICE, config.getString(USER, null, NAME));

		config.setString(USER, null, NAME, BOB);
		config.save();
		assertArrayEquals(CONTENT2.getBytes(UTF_8), IO.readFully(file.toFile()));
	}

	@Test
	public void testUTF8withoutBOM() throws IOException, ConfigInvalidException {
		final Path file = createFile(CONTENT1.getBytes(UTF_8));
		final FileBasedConfig config = new FileBasedConfig(file.toFile(),
				FS.DETECTED);
		config.load();
		assertEquals(ALICE, config.getString(USER, null, NAME));

		config.setString(USER, null, NAME, BOB);
		config.save();
		assertArrayEquals(CONTENT2.getBytes(UTF_8), IO.readFully(file.toFile()));
	}

	@Test
	public void testUTF8withBOM() throws IOException, ConfigInvalidException {
		final ByteArrayOutputStream bos1 = new ByteArrayOutputStream();
		bos1.write(0xEF);
		bos1.write(0xBB);
		bos1.write(0xBF);
		bos1.write(CONTENT1.getBytes(UTF_8));

		final Path file = createFile(bos1.toByteArray());
		final FileBasedConfig config = new FileBasedConfig(file.toFile(),
				FS.DETECTED);
		config.load();
		assertEquals(ALICE, config.getString(USER, null, NAME));

		config.setString(USER, null, NAME, BOB);
		config.save();

		final ByteArrayOutputStream bos2 = new ByteArrayOutputStream();
		bos2.write(0xEF);
		bos2.write(0xBB);
		bos2.write(0xBF);
		bos2.write(CONTENT2.getBytes(UTF_8));
		assertArrayEquals(bos2.toByteArray(), IO.readFully(file.toFile()));
	}

	@Test
	public void testLeadingWhitespaces() throws IOException, ConfigInvalidException {
		final ByteArrayOutputStream bos1 = new ByteArrayOutputStream();
		bos1.write(" \n\t".getBytes(UTF_8));
		bos1.write(CONTENT1.getBytes(UTF_8));

		final Path file = createFile(bos1.toByteArray());
		final FileBasedConfig config = new FileBasedConfig(file.toFile(),
				FS.DETECTED);
		config.load();
		assertEquals(ALICE, config.getString(USER, null, NAME));

		config.setString(USER, null, NAME, BOB);
		config.save();

		final ByteArrayOutputStream bos2 = new ByteArrayOutputStream();
		bos2.write(" \n\t".getBytes(UTF_8));
		bos2.write(CONTENT2.getBytes(UTF_8));
		assertArrayEquals(bos2.toByteArray(), IO.readFully(file.toFile()));
	}

	@Test
	public void testIncludeAbsolute()
			throws IOException, ConfigInvalidException {
		final Path includedFile = createFile(CONTENT1.getBytes(UTF_8));
		final ByteArrayOutputStream bos = new ByteArrayOutputStream();
		bos.write("[include]\npath=".getBytes(UTF_8));
		bos.write(pathToString(includedFile.toFile()).getBytes(UTF_8));

		final Path file = createFile(bos.toByteArray());
		final FileBasedConfig config = new FileBasedConfig(file.toFile(),
				FS.DETECTED);
		config.load();
		assertEquals(ALICE, config.getString(USER, null, NAME));
	}

	@Test
	public void testIncludeRelativeDot()
			throws IOException, ConfigInvalidException {
		final Path includedFile = createFile(CONTENT1.getBytes(UTF_8), "dir1");
		final ByteArrayOutputStream bos = new ByteArrayOutputStream();
		bos.write("[include]\npath=".getBytes(UTF_8));
		bos.write(("./" + includedFile.getFileName()).getBytes(UTF_8));

		final Path file = createFile(bos.toByteArray(), "dir1");
		final FileBasedConfig config = new FileBasedConfig(file.toFile(),
				FS.DETECTED);
		config.load();
		assertEquals(ALICE, config.getString(USER, null, NAME));
	}

	@Test
	public void testIncludeRelativeDotDot()
			throws IOException, ConfigInvalidException {
		final Path includedFile = createFile(CONTENT1.getBytes(UTF_8), "dir1");
		final ByteArrayOutputStream bos = new ByteArrayOutputStream();
		bos.write("[include]\npath=".getBytes(UTF_8));
		bos.write(("../" + includedFile.getParent().getFileName() + "/"
				+ includedFile.getFileName()).getBytes(UTF_8));

		final Path file = createFile(bos.toByteArray(), "dir2");
		final FileBasedConfig config = new FileBasedConfig(file.toFile(),
				FS.DETECTED);
		config.load();
		assertEquals(ALICE, config.getString(USER, null, NAME));
	}

	@Test
	public void testIncludeRelativeDotDotNotFound()
			throws IOException, ConfigInvalidException {
		final Path includedFile = createFile(CONTENT1.getBytes(UTF_8));
		final ByteArrayOutputStream bos = new ByteArrayOutputStream();
		bos.write("[include]\npath=".getBytes(UTF_8));
		bos.write(("../" + includedFile.getFileName()).getBytes(UTF_8));

		final Path file = createFile(bos.toByteArray());
		final FileBasedConfig config = new FileBasedConfig(file.toFile(),
				FS.DETECTED);
		config.load();
		assertEquals(null, config.getString(USER, null, NAME));
	}

	@Test
	public void testIncludeWithTilde()
			throws IOException, ConfigInvalidException {
		final Path includedFile = createFile(CONTENT1.getBytes(UTF_8), "home");
		final ByteArrayOutputStream bos = new ByteArrayOutputStream();
		bos.write("[include]\npath=".getBytes(UTF_8));
		bos.write(("~/" + includedFile.getFileName()).getBytes(UTF_8));

		final Path file = createFile(bos.toByteArray(), "repo");
		final FS fs = FS.DETECTED.newInstance();
		fs.setUserHome(includedFile.getParent().toFile());

		final FileBasedConfig config = new FileBasedConfig(file.toFile(), fs);
		config.load();
		assertEquals(ALICE, config.getString(USER, null, NAME));
	}

	@Test
	public void testIncludeDontInlineIncludedLinesOnSave()
			throws IOException, ConfigInvalidException {
		// use a content with multiple sections and multiple key/value pairs
		// because code for first line works different than for subsequent lines
		final Path includedFile = createFile(CONTENT3.getBytes(UTF_8), "dir1");

		final Path file = createFile(new byte[0], "dir2");
		FileBasedConfig config = new FileBasedConfig(file.toFile(),
				FS.DETECTED);
		config.setString("include", null, "path",
				("../" + includedFile.getParent().getFileName() + "/"
						+ includedFile.getFileName()));

		// just by setting the include.path, it won't be included
		assertEquals(null, config.getString(USER, null, NAME));
		assertEquals(null, config.getString(USER, null, EMAIL));
		config.save();

		// and it won't be included after saving
		assertEquals(null, config.getString(USER, null, NAME));
		assertEquals(null, config.getString(USER, null, EMAIL));

		final String expectedText = config.toText();
		assertEquals(2,
				new StringTokenizer(expectedText, "\n", false).countTokens());

		config = new FileBasedConfig(file.toFile(), FS.DETECTED);
		config.load();

		String actualText = config.toText();
		assertEquals(expectedText, actualText);
		// but it will be included after (re)loading
		assertEquals(ALICE, config.getString(USER, null, NAME));
		assertEquals(ALICE_EMAIL, config.getString(USER, null, EMAIL));

		config.save();

		actualText = config.toText();
		assertEquals(expectedText, actualText);
		// and of course preserved after saving
		assertEquals(ALICE, config.getString(USER, null, NAME));
		assertEquals(ALICE_EMAIL, config.getString(USER, null, EMAIL));
	}

	@Test
	public void testSavedConfigFileShouldNotReadUserGitConfig()
			throws IOException {
		AtomicBoolean userConfigTimeRead = new AtomicBoolean(false);

		Path userConfigFile = createFile(CONTENT1.getBytes(), "home");
		mockSystemReader.setUserGitConfig(
				new FileBasedConfig(userConfigFile.toFile(), FS.DETECTED) {

					@Override
					public long getTimeUnit(String section, String subsection,
							String name, long defaultValue, TimeUnit wantUnit) {
						userConfigTimeRead.set(true);
						return super.getTimeUnit(section, subsection, name,
								defaultValue, wantUnit);
					}
				});

		Path file = createFile(CONTENT2.getBytes(), "repo");
		FileBasedConfig fileBasedConfig = new FileBasedConfig(file.toFile(),
				FS.DETECTED);
		fileBasedConfig.save();

		// Needed to trigger the read of FileSnapshot filesystem settings
		fileBasedConfig.isOutdated();
		assertFalse(
				"User config should not be read when accessing config files "
						+ "for avoiding deadlocks",
				userConfigTimeRead.get());
	}

	private Path createFile(byte[] content) throws IOException {
		return createFile(content, null);
	}

	private Path createFile(byte[] content, String subdir) throws IOException {
		Path dir = subdir != null ? trash.resolve(subdir) : trash;
		Files.createDirectories(dir);

		Path f = Files.createTempFile(dir, getClass().getName(), null);
		try (OutputStream os = Files.newOutputStream(f,
				StandardOpenOption.APPEND)) {
			os.write(content);
		}
		return f;
	}
}
