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
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.text.MessageFormat;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.pgm.internal.CLIText;
import org.eclipse.jgit.transport.forwarder.GitForwarder;
import org.eclipse.jgit.transport.forwarder.RouteRequest;
import org.eclipse.jgit.transport.forwarder.RouteResponse;
import org.eclipse.jgit.transport.forwarder.RoutingListener;
import org.kohsuke.args4j.Option;

@Command(usage = "usage_forwarder")
class Forwarder extends TextBuiltin {
	private static final class FixedRouteListener implements RoutingListener {
		final RouteResponse response;

		/**
		 * @param destinationHost
		 *            destination host
		 * @param destinationPort
		 *            destination port
		 */
		public FixedRouteListener(@NonNull String destinationHost, int destinationPort) {
			this.response = new RouteResponse(destinationHost, destinationPort);
		}

		@Override
		public RouteResponse onConnect(RouteRequest request) {
			return response;
		}

		@Override
		public void onClose(RouteRequest request, @Nullable RouteResponse routeResponse) { }

		@Override
		public void onException(RouteRequest request, Exception error) {
			// No-op
		}

		@Override
		public void onOpenException(RouteRequest request,
				@Nullable RouteResponse routeResponse, Exception error) {
			// No-op
		}
	}

	@Option(name = "--listen-host", metaVar = "metaVar_hostName", usage = "usage_forwarderListenHost")
	String listenHost = "localhost"; //$NON-NLS-1$

	@Option(name = "--listen-port", metaVar = "metaVar_port", usage = "usage_forwarderListenPort")
	int listenPort = 9418;

	@Option(name = "--dest-host", metaVar = "metaVar_hostName", usage = "usage_forwarderDestinationHost")
	String destinationHost = "localhost"; //$NON-NLS-1$

	@Option(name = "--dest-port", metaVar = "metaVar_port", usage = "usage_forwarderDestinationPort")
	int destinationPort = 9419;

	@Option(name = "--pid-file", metaVar = "metaVar_pid", usage = "usage_forwarderPidFile")
	File pidFile;

	@Override
	protected boolean requiresRepository() {
		return false;
	}

	@Override
	protected void run() throws IOException, InterruptedException {
		if(pidFile != null) {
			Files.writeString(pidFile.toPath(),
					ProcessHandle.current().pid() + System.lineSeparator());
		}

		try (GitForwarder forwarder = new GitForwarder(
				listenHost,
				listenPort,
				new FixedRouteListener(destinationHost, destinationPort),
				Executors.newCachedThreadPool())) {
			InetSocketAddress listenAddress = new InetSocketAddress(listenHost, listenPort);
			InetSocketAddress destinationAddress = new InetSocketAddress(destinationHost, destinationPort);
			outw.println(MessageFormat.format(CLIText.get().listeningOn, listenAddress));
			outw.println("Forwarding to " + destinationAddress); //$NON-NLS-1$
			outw.flush();

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
