/*
 * Copyright (C) 2009, Constantine Plotnikov <constantine.plotnikov@gmail.com>
 * Copyright (C) 2007, Dave Watson <dwatson@mimvista.com>
 * Copyright (C) 2008-2009, Google Inc.
 * Copyright (C) 2009, JetBrains s.r.o.
 * Copyright (C) 2007-2008, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2006-2008, Shawn O. Pearce <spearce@spearce.org>
 * Copyright (C) 2008, Thad Hughes <thadh@thad.corp.google.com>
 * Copyright (C) 2009, Yann Simon <yann.simon.fr@gmail.com>
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

import java.io.File;

/**
 * An object representing the Git config file.
 *
 * This can be either the repository specific file or the user global
 * file depending on how it is instantiated.
 */
public class RepositoryConfig extends FileBasedConfig {
	/** Section name for a branch configuration. */
	public static final String BRANCH_SECTION = "branch";

	/**
	 * Create a Git configuration file reader/writer/cache for a specific file.
	 *
	 * @param base
	 *            configuration that provides default values if this file does
	 *            not set/override a particular key. Often this is the user's
	 *            global configuration file, or the system level configuration.
	 * @param cfgLocation
	 *            path of the file to load (or save).
	 */
	public RepositoryConfig(final Config base, final File cfgLocation) {
		super(base, cfgLocation);
	}

	/**
	 * @return Core configuration values
	 */
	public CoreConfig getCore() {
		return get(CoreConfig.KEY);
	}

	/**
	 * @return transfer, fetch and receive configuration values
	 */
	public TransferConfig getTransfer() {
		return get(TransferConfig.KEY);
	}

	/** @return standard user configuration data */
	public UserConfig getUserConfig() {
		return get(UserConfig.KEY);
	}

	/**
	 * @return the author name as defined in the git variables
	 *         and configurations. If no name could be found, try
	 *         to use the system user name instead.
	 */
	public String getAuthorName() {
		return getUserConfig().getAuthorName();
	}

	/**
	 * @return the committer name as defined in the git variables
	 *         and configurations. If no name could be found, try
	 *         to use the system user name instead.
	 */
	public String getCommitterName() {
		return getUserConfig().getCommitterName();
	}

	/**
	 * @return the author email as defined in git variables and
	 *         configurations. If no email could be found, try to
	 *         propose one default with the user name and the
	 *         host name.
	 */
	public String getAuthorEmail() {
		return getUserConfig().getAuthorEmail();
	}

	/**
	 * @return the committer email as defined in git variables and
	 *         configurations. If no email could be found, try to
	 *         propose one default with the user name and the
	 *         host name.
	 */
	public String getCommitterEmail() {
		return getUserConfig().getCommitterEmail();
	}
}
