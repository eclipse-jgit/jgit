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

import java.io.File;
import java.io.IOError;
import java.io.IOException;
import java.net.URI;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystem;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Iterator;

public class JGitFSPath implements Path {

	private final JGitFileSystem fs;

	public JGitFSPath(final JGitFileSystem fs) {
		this.fs = fs;
	}

	@Override
	public FileSystem getFileSystem() {
		return fs;
	}

	@Override
	public boolean isAbsolute() {
		return true;
	}

	@Override
	public Path getRoot() {
		return null;
	}

	@Override
	public Path getFileName() {
		return null;
	}

	@Override
	public Path getParent() {
		return null;
	}

	@Override
	public int getNameCount() {
		return -1;
	}

	@Override
	public Path getName(final int index) throws IllegalArgumentException {
		return null;
	}

	@Override
	public Path subpath(final int beginIndex, final int endIndex) throws IllegalArgumentException {
		return null;
	}

	@Override
	public boolean startsWith(final Path other) {
		return false;
	}

	@Override
	public boolean startsWith(final String other) throws InvalidPathException {
		return false;
	}

	@Override
	public boolean endsWith(final Path other) {
		return false;
	}

	@Override
	public boolean endsWith(final String other) throws InvalidPathException {
		return false;
	}

	@Override
	public Path normalize() {
		return this;
	}

	@Override
	public Path resolve(final Path other) {
		return null;
	}

	@Override
	public Path resolve(final String other) throws InvalidPathException {
		return null;
	}

	@Override
	public Path resolveSibling(final Path other) {
		return null;
	}

	@Override
	public Path resolveSibling(final String other) throws InvalidPathException {
		return null;
	}

	@Override
	public Path relativize(final Path other) throws IllegalArgumentException {
		return null;
	}

	@Override
	public URI toUri() throws IOError, SecurityException {
		return URI.create(fs.toString());
	}

	@Override
	public Path toAbsolutePath() throws IOError, SecurityException {
		return this;
	}

	@Override
	public Path toRealPath(final LinkOption... options) throws IOException, SecurityException {
		return this;
	}

	@Override
	public File toFile() throws UnsupportedOperationException {
		return null;
	}

	@Override
	public int compareTo(final Path path) {
		return 0;
	}

	@Override
	public Iterator<Path> iterator() {
		return null;
	}

	@Override
	public WatchKey register(final WatchService watcher, final WatchEvent.Kind<?>[] events,
			final WatchEvent.Modifier... modifiers) throws UnsupportedOperationException, IllegalArgumentException,
			ClosedWatchServiceException, IOException, SecurityException {
		return null;
	}

	@Override
	public WatchKey register(final WatchService watcher, final WatchEvent.Kind<?>... events)
			throws UnsupportedOperationException, IllegalArgumentException, ClosedWatchServiceException, IOException,
			SecurityException {
		return null;
	}
}
