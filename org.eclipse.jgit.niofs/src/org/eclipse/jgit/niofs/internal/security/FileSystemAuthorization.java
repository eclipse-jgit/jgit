/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.internal.security;

import java.nio.file.FileSystem;

/**
 * Strategy for authorizing users to perform actions in a secured file system.
 */
public interface FileSystemAuthorization {

	/**
	 * Returns true if the given user is permitted to perform actions within the
	 * given file system.
	 * 
	 * @param fs
	 * @param user
	 * @return
	 */
	boolean authorize(final FileSystem fs, final User user);
}
