/*
 * Copyright (C) 2010, Google Inc.
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
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
