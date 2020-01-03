/*
 * Copyright (C) 2011, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.dfs;

import java.io.IOException;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.StoredConfig;

/**
 * Config implementation used by DFS repositories.
 * <p>
 * The current implementation acts as if there is no persistent storage: loading
 * simply clears the config, and saving does nothing.
 */
public final class DfsConfig extends StoredConfig {
	/** {@inheritDoc} */
	@Override
	public void load() throws IOException, ConfigInvalidException {
		clear();
	}

	/** {@inheritDoc} */
	@Override
	public void save() throws IOException {
		// TODO actually store this configuration.
	}
}
