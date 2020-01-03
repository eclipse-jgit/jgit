/*
 * Copyright (C) 2010, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.file;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Config.SectionParser;
import org.eclipse.jgit.lib.CoreConfig;

class WriteConfig {
	/** Key for {@link Config#get(SectionParser)}. */
	static final Config.SectionParser<WriteConfig> KEY = WriteConfig::new;

	private final int compression;

	private final boolean fsyncObjectFiles;

	private final boolean fsyncRefFiles;

	private WriteConfig(Config rc) {
		compression = rc.get(CoreConfig.KEY).getCompression();
		fsyncObjectFiles = rc.getBoolean("core", "fsyncobjectfiles", false); //$NON-NLS-1$ //$NON-NLS-2$
		fsyncRefFiles = rc.getBoolean("core", "fsyncreffiles", false); //$NON-NLS-1$ //$NON-NLS-2$
	}

	int getCompression() {
		return compression;
	}

	boolean getFSyncObjectFiles() {
		return fsyncObjectFiles;
	}

	boolean getFSyncRefFiles() {
		return fsyncRefFiles;
	}
}
