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

package org.eclipse.jgit.pgm;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.junit.http.AppServer;
import org.eclipse.jgit.lfs.server.LargeFileRepository;
import org.eclipse.jgit.lfs.server.LargeObjectServlet;
import org.eclipse.jgit.lfs.server.LfsProtocolServlet;
import org.eclipse.jgit.lfs.server.PlainFSRepository;
import org.eclipse.jgit.lfs.server.s3.AmazonS3Repository;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

@Command(common = true, usage = "usage_runLfsStore")
class LfsStore extends TextBuiltin {

	static enum StoreType {
		PLAINFS, S3;
	}

	private static final String OBJECTS = "objects/"; //$NON-NLS-1$

	private static final String STORE_PATH = "/" + OBJECTS + "*"; //$NON-NLS-1$//$NON-NLS-2$

	private static final String PROTOCOL_PATH = "/lfs/objects/batch"; //$NON-NLS-1$

	@Option(name = "--port", aliases = { "-p" }, usage = "usage_LFSPort")
	int port;

	@Option(name = "--store", usage = "usage_LFSRunStore")
	StoreType storeType;

	@Option(name = "--store-url", aliases = {
			"-u" }, usage = "usage_LFSStoreUrl")
	String storeUrl;

	@Argument(required = false, metaVar = "metaVar_directory", usage = "usage_LFSDirectory")
	String directory;

	String protocolUrl;

	String accessKey;

	String secretKey;

	protected void run() throws Exception {
		AppServer server = new AppServer(port);
		ServletContextHandler app = server.addContext("/"); //$NON-NLS-1$
		LargeFileRepository repository;

		switch (storeType) {
		case PLAINFS:
			Path dir = Paths.get(directory);
			PlainFSRepository plainFSRepo = new PlainFSRepository(getStoreUrl(),
					dir);
			LargeObjectServlet content = new LargeObjectServlet(plainFSRepo,
					30000);
			app.addServlet(new ServletHolder(content), STORE_PATH);
			repository = plainFSRepo;
			break;

		case S3:
			readAWSKeys();
			repository = new AmazonS3Repository("eu-central-1", //$NON-NLS-1$
					"test-gerrit-lfs", //$NON-NLS-1$
					300, "REDUCED_REDUNDANCY", accessKey, secretKey); //$NON-NLS-1$
			break;
		default:
			throw new IllegalArgumentException(
					"Unknown store type: " + storeType); //$NON-NLS-1$
		}

		LfsProtocolServlet protocol = new LfsProtocolServlet(repository);
		app.addServlet(new ServletHolder(protocol), PROTOCOL_PATH);

		server.setUp();

		outw.println("LFS protocol URL: " + getProtocolUrl()); //$NON-NLS-1$
		if (storeType == StoreType.PLAINFS) {
			outw.println("LFS objects located in: " + directory); //$NON-NLS-1$
			outw.println("LFS store URL: " + getStoreUrl()); //$NON-NLS-1$
		}
	}

	private void readAWSKeys() throws IOException, ConfigInvalidException {
		String credentialsPath = System.getProperty("user.home") //$NON-NLS-1$
				+ "/.aws/credentials"; //$NON-NLS-1$
		FileBasedConfig c = new FileBasedConfig(new File(credentialsPath),
				FS.DETECTED);
		c.load();
		accessKey = c.getString("default", null, "accessKey"); //$NON-NLS-1$//$NON-NLS-2$
		secretKey = c.getString("default", null, "secretKey"); //$NON-NLS-1$ //$NON-NLS-2$
		if (accessKey == null || accessKey.isEmpty()) {
			throw new IllegalStateException(
					"No accessKey in " + credentialsPath);
		}
		if (secretKey == null || secretKey.isEmpty()) {
			throw new IllegalStateException(
					"No secretKey in " + credentialsPath);
		}
	}

	private String getStoreUrl() {
		if (storeUrl == null) {
			if (storeType == StoreType.PLAINFS) {
				// TODO: get real host name from the OS
				storeUrl = "http://localhost:" + port + "/" + OBJECTS; //$NON-NLS-1$ //$NON-NLS-2$
			} else {
				die("Local store not running and no --store-url specified"); //$NON-NLS-1$
			}
		}
		return storeUrl;
	}

	private String getProtocolUrl() {
		if (protocolUrl == null) {
			protocolUrl = "http://localhost:" + port + PROTOCOL_PATH;
		}
		return protocolUrl;
	}
}
