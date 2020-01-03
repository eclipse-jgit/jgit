/*
 * Copyright (C) 2019, Google LLC and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.pgm;

import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.spi.ExplicitBooleanOptionHandler;

@Command(common = true, usage = "usage_convertRefStorage")
class ConvertRefStorage extends TextBuiltin {

	@Option(name = "--format", usage = "usage_convertRefStorageFormat")
	private String format = "reftable"; //$NON-NLS-1$

	@Option(name = "--backup", handler = ExplicitBooleanOptionHandler.class, aliases = {
			"-b" }, usage = "usage_convertRefStorageBackup")
	private boolean backup = true;

	@Option(name = "--reflogs", handler = ExplicitBooleanOptionHandler.class, aliases = {
			"-r" }, usage = "usage_convertRefStorageRefLogs")
	private boolean writeLogs = true;

	/** {@inheritDoc} */
	@Override
	protected void run() throws Exception {
		((FileRepository) db).convertRefStorage(format, writeLogs, backup);
	}
}
