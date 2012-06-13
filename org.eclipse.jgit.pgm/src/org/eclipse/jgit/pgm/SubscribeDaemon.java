/*
 * Copyright (C) 2012, Google Inc.
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

package org.eclipse.jgit.pgm;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.storage.file.FileSnapshot;
import org.eclipse.jgit.transport.PubSubConfig;
import org.eclipse.jgit.transport.SubscriptionExecutor;
import org.eclipse.jgit.util.FS;
import org.kohsuke.args4j.Option;

@Command(common = false, usage = "usage_RunSubscribeDaemon")
class SubscribeDaemon extends TextBuiltin {
	@Option(name = "-p", metaVar = "metaVar_seconds", usage = "usage_pubsubConfigPollTime")
	private int pollTime = 5; // Default to 5 seconds

	/** Name of the pubsub config file */
	protected static String GLOBAL_PUBSUB_FILE = ".gitpubsub";

	/** @return the pubsub config file */
	public static File getConfigFile() {
		return new File(FS.detect().userHome(), GLOBAL_PUBSUB_FILE);
	}

	/**
	 * @return the config file for this user
	 * @throws ConfigInvalidException
	 * @throws IOException
	 * @throws URISyntaxException
	 */
	public static PubSubConfig getConfig()
			throws IOException, ConfigInvalidException, URISyntaxException {
		File cfg = getConfigFile();
		FileBasedConfig c = new FileBasedConfig(cfg, FS.detect());
		c.load();
		return new PubSubConfig(c);
	}

	/**
	 * @param pubsubConfig
	 * @throws IOException
	 */
	public static void updateConfig(PubSubConfig pubsubConfig)
			throws IOException {
		File cfg = getConfigFile();
		FileBasedConfig c = new FileBasedConfig(cfg, FS.detect());
		pubsubConfig.update(c);
		c.save();
	}

	@Override
	protected void run() throws Exception {
		File configFile = getConfigFile();
		SubscriptionExecutor manager = new SubscriptionExecutor(out);

		try {
			while (true) {
				// Poll the pubsub config file for changes
				FileSnapshot configFileSnapshot = FileSnapshot.save(configFile);
				manager.applyConfig(getConfig().getPublishers());
				while (!configFileSnapshot.isModified(configFile))
					Thread.sleep(1000 * pollTime);
			}
		} finally {
			manager.close();
		}
	}
}
