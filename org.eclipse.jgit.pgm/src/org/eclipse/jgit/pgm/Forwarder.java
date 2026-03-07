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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.pgm.forwarder.ForwarderConfig;
import org.eclipse.jgit.pgm.internal.CLIText;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.transport.forwarder.GitForwarder;
import org.eclipse.jgit.transport.forwarder.RouteRequest;
import org.eclipse.jgit.transport.forwarder.RouteResponse;
import org.eclipse.jgit.transport.forwarder.RoutingListener;
import org.eclipse.jgit.util.FS;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Command(usage = "usage_forwarder")
class Forwarder extends TextBuiltin {
	private static final class LimitingRoutingListener implements RoutingListener {
		private record RouteState(AtomicInteger globalLimit, AtomicInteger projectLimit) {
			void release() {
				if (projectLimit != null) {
					projectLimit.decrementAndGet();
				}
				if (globalLimit != null) {
					globalLimit.decrementAndGet();
				}
			}
		}

		private final RouteResponse response;
		private final ForwarderConfig forwarderConfig;
		private final AtomicInteger globalInflight;
		private final Map<ForwarderConfig.RepositoryLimit, AtomicInteger> inFlightByRepo = new HashMap<>();
		private final ConcurrentMap<String, RouteState> routeStates = new ConcurrentHashMap<>();

		LimitingRoutingListener(RouteResponse response, ForwarderConfig fc) {
			this.response = response;
			this.forwarderConfig = fc;
			this.globalInflight = fc.getGlobalLimit() <= 0 ? null : new AtomicInteger(0);
			for (ForwarderConfig.RepositoryLimit lim : fc.getProjectLimits()) {
				this.inFlightByRepo.put(lim, new AtomicInteger(0));
			}
		}

		@Override
		public RouteResponse onConnect(RouteRequest request) {
			if (!tryAcquire(globalInflight, forwarderConfig.getGlobalLimit())) {
				LOG.warn(MessageFormat.format(CLIText.get().forwarderRejectingGlobalMaxStart, request));
				return new RouteResponse(null, CLIText.get().forwarderGlobalMaxStartExceeded);
			}

			String repoName = request.repo();
			AtomicInteger repoInFlight = null;
			if (repoName != null) {
				ForwarderConfig.RepositoryLimit repositoryLimit = findFirstMatch(repoName);
				if (repositoryLimit != null) {
					repoInFlight = inFlightByRepo.get(repositoryLimit);
					if (!tryAcquire(repoInFlight, repositoryLimit.maxStart())) {
						globalInflight.decrementAndGet();

						LOG.warn(MessageFormat.format(CLIText.get().forwarderRejectingRepoMaxStart, request));
						return new RouteResponse(null,
								MessageFormat.format(CLIText.get().forwarderRepoMaxStartExceeded,
										repositoryLimit.pattern()));
					}
				}
			}

			routeStates.put(request.requestId(), new RouteState(globalInflight, repoInFlight));
			return response;
		}

		@Override
		public void onClose(RouteRequest request, @Nullable RouteResponse routeResponse) {
			releaseState(request);
		}

		@Override
		public void onException(RouteRequest request, Exception error) {
			releaseState(request);
		}

		@Override
		public void onOpenException(RouteRequest request,
				@Nullable RouteResponse routeResponse, Exception error) {
			releaseState(request);
		}

		private void releaseState(RouteRequest request) {
			RouteState state = routeStates.remove(request.requestId());
			if (state != null) {
				state.release();
			}
		}

		private ForwarderConfig.RepositoryLimit findFirstMatch(String project) {
			for (ForwarderConfig.RepositoryLimit limit : forwarderConfig.getProjectLimits()) {
				if (limit.matches(project)) {
					return limit;
				}
			}
			return null;
		}

		private static boolean tryAcquire(AtomicInteger inFlight, int max) {
			if (inFlight == null) {
				return true;
			}

			int previousValue = inFlight.getAndUpdate(cur -> (cur < max) ? cur + 1 : cur);
			return previousValue < max;
		}
	}

	private static final Logger LOG = LoggerFactory.getLogger(Forwarder.class);

	@Option(name = "--config-file", metaVar = "metaVar_configFile", usage = "usage_forwarderConfigFile")
	File configFile;

	@Option(name = "--pid-file", metaVar = "metaVar_pid", usage = "usage_forwarderPidFile")
	File pidFile = new File("jgit-forwarder.pid"); //$NON-NLS-1$

	@Override
	protected boolean requiresRepository() {
		return false;
	}

	@Override
	protected void run() throws IOException, InterruptedException {
		Files.writeString(pidFile.toPath(),
				ProcessHandle.current().pid() + System.lineSeparator());

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
				new LimitingRoutingListener(new RouteResponse(fc.getRemote(), null), fc),
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
