/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jgit.niofs.internal.JGitFileSystemProvider;

public final class JGitFileSystemBuilder {

	private static final JGitFileSystemProvider PROVIDER = new JGitFileSystemProvider();
	private static final Map<String, String> DEFAULT_OPTIONS = new HashMap<>();

	private JGitFileSystemBuilder() {
		DEFAULT_OPTIONS.put("init", "true");
	}

	public static FileSystem newFileSystem(final String repoName) throws IOException {
		return PROVIDER.newFileSystem(URI.create("git://" + repoName), DEFAULT_OPTIONS);
	}
}
