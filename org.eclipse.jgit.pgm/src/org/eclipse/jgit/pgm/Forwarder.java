/*
 * Copyright (C) 2026, Nvidia
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.pgm;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.MessageFormat;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.pgm.forwarder.ForwarderConfig;
import org.eclipse.jgit.pgm.internal.CLIText;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.transport.forwarder.FixedRouteListener;
import org.eclipse.jgit.transport.forwarder.GitForwarder;
import org.eclipse.jgit.util.FS;
import org.kohsuke.args4j.Option;

/**
 * Proxy anonymous git protocol to a fixed destination
 *
 * @since 7.7
 */
@Command(usage = "usage_forwarder")
public class Forwarder extends TextBuiltin {

	@Option(name = "--config-file", metaVar = "metaVar_configFile", usage = "usage_forwarderConfigFile")
	File configFile;

	@Override
	protected boolean requiresRepository() {
		return false;
	}

	@Override
	protected void run() throws IOException, InterruptedException {
		if (configFile == null) {
			throw die(CLIText.get().forwarderConfigFileRequired);
		}
		if (!configFile.exists()) {
			throw die(MessageFormat.format(CLIText.get().configFileNotFound,
					configFile.getAbsolutePath()));
		}

		StoredConfig cfg = new FileBasedConfig(configFile, FS.DETECTED);
		try {
			cfg.load();
		} catch (IOException | ConfigInvalidException e) {
			throw die(e.getMessage(), e);
		}

		ForwarderConfig fc = new ForwarderConfig(cfg);
		try (GitForwarder forwarder = new GitForwarder(
				fc.getListen(),
				new FixedRouteListener(fc.getRemote()),
				Executors.newCachedThreadPool()
		)) {
			errw.println(MessageFormat.format(CLIText.get().listeningOn, fc.getListen()));
			errw.println(MessageFormat.format(CLIText.get().forwardingTo, fc.getRemote()));
			errw.flush();

			CountDownLatch latch = new CountDownLatch(1);
			Runtime.getRuntime().addShutdownHook(new Thread(() -> {
				try {
					forwarder.close();
				} finally {
					latch.countDown();
				}
			}, "Forwarder-shutdown")); //$NON-NLS-1$
			latch.await();
		}
	}
}
