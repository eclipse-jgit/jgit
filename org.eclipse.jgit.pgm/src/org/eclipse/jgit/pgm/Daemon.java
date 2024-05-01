/*
 * Copyright (C) 2008-2009, Google Inc. and others
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
import java.net.InetSocketAddress;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.pgm.internal.CLIText;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.storage.file.WindowCacheConfig;
import org.eclipse.jgit.storage.pack.PackConfig;
import org.eclipse.jgit.transport.DaemonClient;
import org.eclipse.jgit.transport.DaemonService;
import org.eclipse.jgit.transport.resolver.FileResolver;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.SystemReader;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

@Command(common = true, usage = "usage_exportRepositoriesOverGit")
class Daemon extends TextBuiltin {
	@Option(name = "--config-file", metaVar = "metaVar_configFile", usage = "usage_configFile")
	File configFile;

	@Option(name = "--port", metaVar = "metaVar_port", usage = "usage_portNumberToListenOn")
	int port = org.eclipse.jgit.transport.Daemon.DEFAULT_PORT;

	@Option(name = "--listen", metaVar = "metaVar_hostName", usage = "usage_hostnameOrIpToListenOn")
	String host;

	@Option(name = "--timeout", metaVar = "metaVar_seconds", usage = "usage_abortConnectionIfNoActivity")
	int timeout = -1;

	@Option(name = "--enable", metaVar = "metaVar_service", usage = "usage_enableTheServiceInAllRepositories")
	List<String> enable = new ArrayList<>();

	@Option(name = "--disable", metaVar = "metaVar_service", usage = "usage_disableTheServiceInAllRepositories")
	List<String> disable = new ArrayList<>();

	@Option(name = "--allow-override", metaVar = "metaVar_service", usage = "usage_configureTheServiceInDaemonServicename")
	List<String> canOverride = new ArrayList<>();

	@Option(name = "--forbid-override", metaVar = "metaVar_service", usage = "usage_configureTheServiceInDaemonServicename")
	List<String> forbidOverride = new ArrayList<>();

	@Option(name = "--export-all", usage = "usage_exportWithoutGitDaemonExportOk")
	boolean exportAll;

	@Argument(required = true, metaVar = "metaVar_directory", usage = "usage_directoriesToExport")
	List<File> directory = new ArrayList<>();

	@Override
	protected boolean requiresRepository() {
		return false;
	}

	@Override
	protected void run() throws Exception {
		PackConfig packConfig = new PackConfig();
		StoredConfig cfg;
		if (configFile == null) {
			cfg = getUserConfig();
		} else {
			if (!configFile.exists()) {
				throw die(MessageFormat.format(
						CLIText.get().configFileNotFound, //
						configFile.getAbsolutePath()));
			}
			cfg = new FileBasedConfig(configFile, FS.DETECTED);
		}
			cfg.load();
			new WindowCacheConfig().fromConfig(cfg).install();
			packConfig.fromConfig(cfg);

		int threads = packConfig.getThreads();
		if (threads <= 0)
			threads = Runtime.getRuntime().availableProcessors();
		if (1 < threads)
			packConfig.setExecutor(Executors.newFixedThreadPool(threads));

		final FileResolver<DaemonClient> resolver = new FileResolver<>();
		for (File f : directory) {
			outw.println(MessageFormat.format(CLIText.get().exporting, f.getAbsolutePath()));
			resolver.exportDirectory(f);
		}
		resolver.setExportAll(exportAll);

		final org.eclipse.jgit.transport.Daemon d;
		d = new org.eclipse.jgit.transport.Daemon(
				host != null ? new InetSocketAddress(host, port)
						: new InetSocketAddress(port));
		d.setPackConfig(packConfig);
		d.setRepositoryResolver(resolver);
		if (0 <= timeout)
			d.setTimeout(timeout);

		for (String n : enable)
			service(d, n).setEnabled(true);
		for (String n : disable)
			service(d, n).setEnabled(false);

		for (String n : canOverride)
			service(d, n).setOverridable(true);
		for (String n : forbidOverride)
			service(d, n).setOverridable(false);
		d.start();
		outw.println(MessageFormat.format(CLIText.get().listeningOn, d.getAddress()));
	}

	private StoredConfig getUserConfig() throws IOException {
		StoredConfig userConfig = null;
		try {
			userConfig = SystemReader.getInstance().getUserConfig();
		} catch (ConfigInvalidException e) {
			throw die(e.getMessage());
		}
		return userConfig;
	}

	private static DaemonService service(
			final org.eclipse.jgit.transport.Daemon d,
			final String n) {
		final DaemonService svc = d.getService(n);
		if (svc == null)
			throw die(MessageFormat.format(CLIText.get().serviceNotSupported, n));
		return svc;
	}
}
