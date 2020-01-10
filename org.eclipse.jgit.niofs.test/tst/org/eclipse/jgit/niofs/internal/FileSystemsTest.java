/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.niofs.internal;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Random;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FileSystemsTest {

	private static String VALUE = "sample";

	@Test
	public void basicSample() throws IOException {
		//creates a new repository
		final FileSystem value = FileSystems.newFileSystem(URI.create("git://myrepo" + new Random().nextInt()), new HashMap<>());
		//filename Path
		final Path result = Files.write(value.getPath("filename.txt"), VALUE.getBytes());
		//read file content
		assertEquals(VALUE, Files.readAllLines(result).get(0));
	}
}
