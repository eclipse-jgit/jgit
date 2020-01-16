/*
 * Copyright (C) 2010, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.lib;

import java.io.IOException;

import org.eclipse.jgit.errors.ConfigInvalidException;

/**
 * Persistent configuration that can be stored and loaded from a location.
 */
public abstract class StoredConfig extends Config {
	/**
	 * Create a configuration with no default fallback.
	 */
	public StoredConfig() {
		super();
	}

	/**
	 * Create an empty configuration with a fallback for missing keys.
	 *
	 * @param defaultConfig
	 *            the base configuration to be consulted when a key is missing
	 *            from this configuration instance.
	 */
	public StoredConfig(Config defaultConfig) {
		super(defaultConfig);
	}

	/**
	 * Load the configuration from the persistent store.
	 * <p>
	 * If the configuration does not exist, this configuration is cleared, and
	 * thus behaves the same as though the backing store exists, but is empty.
	 *
	 * @throws java.io.IOException
	 *             the configuration could not be read (but does exist).
	 * @throws org.eclipse.jgit.errors.ConfigInvalidException
	 *             the configuration is not properly formatted.
	 */
	public abstract void load() throws IOException, ConfigInvalidException;

	/**
	 * Save the configuration to the persistent store.
	 *
	 * @throws java.io.IOException
	 *             the configuration could not be written.
	 */
	public abstract void save() throws IOException;

	/** {@inheritDoc} */
	@Override
	public void clear() {
		super.clear();
	}
}
