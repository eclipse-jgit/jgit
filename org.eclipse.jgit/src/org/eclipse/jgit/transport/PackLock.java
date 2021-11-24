/*
 * Copyright (C) 2021 Thomas Wolf <thomas.wolf@paranor.ch> and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.transport;

import java.io.IOException;

/**
 * A {@code PackLock} describes a {@code .keep} file that holds a pack in place.
 * If {@link PackParser#parse(org.eclipse.jgit.lib.ProgressMonitor)} creates
 * such a pack lock, it returns the lock so that it can be unlocked once the
 * pack doesn't need a {@code .keep} file anymore.
 *
 * @since 6.0
 */
public interface PackLock {

	/**
	 * Remove the {@code .keep} file that holds a pack in place.
	 *
	 * @throws java.io.IOException
	 *             if deletion of the {@code .keep} file failed
	 */
	void unlock() throws IOException;
}
