/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.internal;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Random;

import org.eclipse.jgit.niofs.JGitFileSystemBuilder;
import org.junit.Test;

public class JGitFileSystemBuilderTest extends BaseTest {

	@Test
	public void testSimpleBuilderSample() throws IOException {
		final FileSystem fs = JGitFileSystemBuilder.newFileSystem("myrepo" + new Random().nextInt());

		Path foo = fs.getPath("/foo");
		Files.createDirectory(foo);

		Path hello = foo.resolve("hello.txt"); // /foo/hello.txt

		Files.write(hello, Collections.singletonList("hello world"), StandardCharsets.UTF_8);

		assertEquals("hello world", Files.readAllLines(hello).get(0));
	}
}
