/*
 * Copyright (C) 2023, SAP SE and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.lib;

import java.io.IOException;

import org.eclipse.jgit.errors.FsckError;

/**
 * Interface for checking repository integrity and consistency
 *
 * @since 5.13.2
 */
public interface Fsck {

	/**
	 * Verify the integrity and connectivity of all objects in the object
	 * database.
	 *
	 * @param pm
	 *            callback to provide progress feedback during the check.
	 * @return all errors about the repository.
	 * @throws java.io.IOException
	 *             if encounters IO errors during the process.
	 */
	FsckError check(ProgressMonitor pm) throws IOException;

	/**
	 * Use a customized object checker instead of the default one. Caller can
	 * specify a skip list to ignore some errors.
	 *
	 * It will be reset at the start of each {{@link #check(ProgressMonitor)}
	 * call.
	 *
	 * @param objChecker
	 *            A customized object checker.
	 */
	void setObjectChecker(ObjectChecker objChecker);

	/**
	 * Whether fsck should bypass object validity and integrity checks and only
	 * check connectivity.
	 *
	 * @param connectivityOnly
	 *            whether fsck should bypass object validity and integrity
	 *            checks and only check connectivity. The default is
	 *            {@code false}, meaning to run all checks.
	 */
	void setConnectivityOnly(boolean connectivityOnly);

}