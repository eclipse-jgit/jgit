/*
 * Copyright (C) 2009-2010, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.errors;

import java.io.File;
import java.text.MessageFormat;

import org.eclipse.jgit.internal.JGitText;

/**
 * Indicates a local repository does not exist.
 */
public class RepositoryNotFoundException extends TransportException {
	private static final long serialVersionUID = 1L;

	/**
	 * Constructs an exception indicating a local repository does not exist.
	 *
	 * @param location
	 *            description of the repository not found, usually file path.
	 */
	public RepositoryNotFoundException(File location) {
		this(location.getPath());
	}

	/**
	 * Constructs an exception indicating a local repository does not exist.
	 *
	 * @param location
	 *            description of the repository not found, usually file path.
	 * @param why
	 *            why the repository does not exist.
	 */
	public RepositoryNotFoundException(File location, Throwable why) {
		this(location.getPath(), why);
	}

	/**
	 * Constructs an exception indicating a local repository does not exist.
	 *
	 * @param location
	 *            description of the repository not found, usually file path.
	 */
	public RepositoryNotFoundException(String location) {
		super(message(location));
	}

	/**
	 * Constructs an exception indicating a local repository does not exist.
	 *
	 * @param location
	 *            description of the repository not found, usually file path.
	 * @param why
	 *            why the repository does not exist.
	 */
	public RepositoryNotFoundException(String location, Throwable why) {
		super(message(location), why);
	}

	private static String message(String location) {
		return MessageFormat.format(JGitText.get().repositoryNotFound, location);
	}
}
