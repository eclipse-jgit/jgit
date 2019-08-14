/*
 * Copyright (C) 2019, Matthias Sohn <matthias.sohn@sap.com>
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
package org.eclipse.jgit.util;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cache for global git configurations
 *
 * @since 5.1.9
 */
public class GlobalConfigCache {
	private final static Logger LOG = LoggerFactory
			.getLogger(GlobalConfigCache.class);

	private static AtomicReference<GlobalConfigCache> INSTANCE = new AtomicReference<>(
			new GlobalConfigCache());

	/**
	 * Get the cache instance
	 *
	 * @return the cache instance
	 */
	public static GlobalConfigCache getInstance() {
		return INSTANCE.get();
	}

	/**
	 * Set the instance
	 *
	 * @param systemConfig
	 *            the new system git config
	 * @param userConfig
	 *            the new user config usually located in ~/.gitconfig
	 * @return the new instance
	 */
	public static GlobalConfigCache setInstance(FileBasedConfig systemConfig,
			FileBasedConfig userConfig) {
		INSTANCE.set(new GlobalConfigCache(systemConfig, userConfig));
		return INSTANCE.get();
	}

	private FileBasedConfig userConfig;

	private FileBasedConfig systemConfig;

	private GlobalConfigCache() {
		FS fs = FS.DETECTED;
		SystemReader sr = SystemReader.getInstance();
		if (StringUtils.isEmptyOrNull(SystemReader.getInstance()
				.getenv(Constants.GIT_CONFIG_NOSYSTEM_KEY))) {
			systemConfig = SystemReader.getInstance().openSystemConfig(null,
					fs);
		} else {
			systemConfig = new FileBasedConfig(null, FS.DETECTED) {
				@Override
				public void load() {
					// empty, do not load
				}

				@Override
				public boolean isOutdated() {
					// regular class would bomb here
					return false;
				}
			};
		}
		userConfig = sr.openUserConfig(systemConfig, fs);
	}

	private GlobalConfigCache(FileBasedConfig systemConfig,
			FileBasedConfig userConfig) {
		this.systemConfig = systemConfig;
		this.userConfig = userConfig;
	}

	/**
	 * Get the gitconfig configuration found in the system-wide "etc" directory.
	 * The configuration will be reloaded automatically if the configuration
	 * file was modified otherwise returns the cached system level config.
	 *
	 * @return the gitconfig configuration found in the system-wide "etc"
	 *         directory
	 * @since 5.1.9
	 */
	public StoredConfig getSystemConfig() {
		if (systemConfig.isOutdated()) {
			try {
				LOG.debug("loading system config {}", //$NON-NLS-1$
						systemConfig);
				systemConfig.load();
			} catch (IOException e) {
				throw new RuntimeException(e);
			} catch (ConfigInvalidException e) {
				throw new RuntimeException(
						MessageFormat.format(
								JGitText.get().systemConfigFileInvalid,
								systemConfig.getFile().getAbsolutePath(), e),
						e);
			}
		}
		return systemConfig;
	}

	/**
	 * Get the git configuration found in the user home. The configuration will
	 * be reloaded automatically if the configuration file was modified. Also
	 * reloads the system config if the system config file was modified. If the
	 * configuration file wasn't modified returns the cached configuration.
	 *
	 * @return the git configuration found in the user home
	 * @since 5.1.9
	 */
	public StoredConfig getUserConfig() {
		getSystemConfig();
		if (userConfig.isOutdated()) {
			try {
				LOG.debug("loading user config {}", userConfig); //$NON-NLS-1$
				userConfig.load();
			} catch (IOException e) {
				throw new RuntimeException(e);
			} catch (ConfigInvalidException e) {
				throw new RuntimeException(MessageFormat.format(
						JGitText.get().userConfigFileInvalid,
						userConfig.getFile().getAbsolutePath(), e), e);
			}
		}
		return userConfig;
	}
}
