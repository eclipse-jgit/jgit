/*
 * Copyright (C) 2015, Sasa Zivkov <sasa.zivkov@sap.com>
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

package org.eclipse.jgit.pgm.debug;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jgit.lfs.server.LargeFileRepository;
import org.eclipse.jgit.lfs.server.LfsProtocolServlet;
import org.eclipse.jgit.lfs.server.fs.FileLfsServlet;
import org.eclipse.jgit.lfs.server.fs.FileLfsRepository;
import org.eclipse.jgit.pgm.Command;
import org.eclipse.jgit.pgm.TextBuiltin;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

@Command(common = true, usage = "usage_runLfsStore")
class LfsStore extends TextBuiltin {

	/**
	 * Tiny web application server for testing
	 */
	class AppServer {

		private final Server server;

		private final ServerConnector connector;

		private final ContextHandlerCollection contexts;

		private URI uri;

		AppServer(int port) {
			server = new Server();

			HttpConfiguration http_config = new HttpConfiguration();
			http_config.setOutputBufferSize(32768);

			connector = new ServerConnector(server,
					new HttpConnectionFactory(http_config));
			connector.setPort(port);
			try {
				String host = InetAddress.getByName("localhost") //$NON-NLS-1$
						.getHostAddress();
				connector.setHost(host);
				if (host.contains(":") && !host.startsWith("[")) //$NON-NLS-1$ //$NON-NLS-2$
					host = "[" + host + "]"; //$NON-NLS-1$//$NON-NLS-2$
				uri = new URI("http://" + host + ":" + port); //$NON-NLS-1$ //$NON-NLS-2$
			} catch (UnknownHostException e) {
				throw new RuntimeException("Cannot find localhost", e); //$NON-NLS-1$
			} catch (URISyntaxException e) {
				throw new RuntimeException("Unexpected URI error on " + uri, e); //$NON-NLS-1$
			}

			contexts = new ContextHandlerCollection();
			server.setHandler(contexts);
			server.setConnectors(new Connector[] { connector });
		}

		/**
		 * Create a new servlet context within the server.
		 * <p>
		 * This method should be invoked before the server is started, once for
		 * each context the caller wants to register.
		 *
		 * @param path
		 *            path of the context; use "/" for the root context if
		 *            binding to the root is desired.
		 * @return the context to add servlets into.
		 */
		ServletContextHandler addContext(String path) {
			assertNotRunning();
			if ("".equals(path)) //$NON-NLS-1$
				path = "/"; //$NON-NLS-1$

			ServletContextHandler ctx = new ServletContextHandler();
			ctx.setContextPath(path);
			contexts.addHandler(ctx);

			return ctx;
		}

		void start() throws Exception {
			server.start();
		}

		void stop() throws Exception {
			server.stop();
		}

		URI getURI() {
			return uri;
		}

		private void assertNotRunning() {
			if (server.isRunning()) {
				throw new IllegalStateException("server is running"); //$NON-NLS-1$
			}
		}
	}

	private static enum StoreType {
		FS;
	}

	private static final String OBJECTS = "objects/"; //$NON-NLS-1$

	private static final String STORE_PATH = "/" + OBJECTS + "*"; //$NON-NLS-1$//$NON-NLS-2$

	private static final String PROTOCOL_PATH = "/lfs/objects/batch"; //$NON-NLS-1$

	@Option(name = "--port", aliases = {"-p" }, metaVar = "metaVar_port",
			usage = "usage_LFSPort")
	int port;

	@Option(name = "--store", metaVar = "metaVar_lfsStorage", usage = "usage_LFSRunStore")
	StoreType storeType;

	@Option(name = "--store-url", aliases = {"-u" }, metaVar = "metaVar_url",
			usage = "usage_LFSStoreUrl")
	String storeUrl;

	@Argument(required = false, metaVar = "metaVar_directory", usage = "usage_LFSDirectory")
	String directory;

	String protocolUrl;

	String accessKey;

	String secretKey;

	@Override
	protected boolean requiresRepository() {
		return false;
	}

	protected void run() throws Exception {
		AppServer server = new AppServer(port);
		URI baseURI = server.getURI();
		ServletContextHandler app = server.addContext("/"); //$NON-NLS-1$

		final LargeFileRepository repository;
		switch (storeType) {
		case FS:
			Path dir = Paths.get(directory);
			FileLfsRepository fsRepo = new FileLfsRepository(
					getStoreUrl(baseURI), dir);
			FileLfsServlet content = new FileLfsServlet(fsRepo, 30000);
			app.addServlet(new ServletHolder(content), STORE_PATH);
			repository = fsRepo;
			break;

		default:
			throw new IllegalArgumentException(
					"Unknown store type: " + storeType); //$NON-NLS-1$
		}

		LfsProtocolServlet protocol = new LfsProtocolServlet() {

			private static final long serialVersionUID = 1L;

			@Override
			protected LargeFileRepository getLargeFileRepository() {
				return repository;
			}

		};
		app.addServlet(new ServletHolder(protocol), PROTOCOL_PATH);

		server.start();

		outw.println("LFS protocol URL: " + getProtocolUrl(baseURI)); //$NON-NLS-1$
		if (storeType == StoreType.FS) {
			outw.println("LFS objects located in: " + directory); //$NON-NLS-1$
			outw.println("LFS store URL: " + getStoreUrl(baseURI)); //$NON-NLS-1$
		}
	}

	private String getStoreUrl(URI baseURI) {
		if (storeUrl == null) {
			if (storeType == StoreType.FS) {
				storeUrl = baseURI + "/" + OBJECTS; //$NON-NLS-1$
			} else {
				die("Local store not running and no --store-url specified"); //$NON-NLS-1$
			}
		}
		return storeUrl;
	}

	private String getProtocolUrl(URI baseURI) {
		if (protocolUrl == null) {
			protocolUrl = baseURI + PROTOCOL_PATH;
		}
		return protocolUrl;
	}
}
