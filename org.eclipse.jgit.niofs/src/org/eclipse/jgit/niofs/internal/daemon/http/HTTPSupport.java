/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.internal.daemon.http;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletRegistration;
import javax.servlet.annotation.WebListener;

import org.eclipse.jgit.http.server.GitServlet;
import org.eclipse.jgit.niofs.internal.JGitFileSystemProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@WebListener
public class HTTPSupport implements ServletContextListener {

	private static final String GIT_PATH = "git";
	private static final Logger LOG = LoggerFactory.getLogger(HTTPSupport.class);

	private ServletContext servletContext = null;

	@Override
	public void contextInitialized(ServletContextEvent sce) {
		servletContext = sce.getServletContext();
		final JGitFileSystemProvider fsProvider = resolveProvider();
		if (fsProvider != null && (fsProvider.getConfig().isHttpEnabled() || fsProvider.getConfig().isHttpsEnabled())) {
			if (fsProvider.getConfig().isHttpEnabled()) {
				fsProvider.addHostName("http", fsProvider.getConfig().getHttpHostName() + ":"
						+ fsProvider.getConfig().getHttpPort() + servletContext.getContextPath() + "/" + GIT_PATH);
			}
			if (fsProvider.getConfig().isHttpsEnabled()) {
				fsProvider.addHostName("https", fsProvider.getConfig().getHttpsHostName() + ":"
						+ fsProvider.getConfig().getHttpsPort() + servletContext.getContextPath() + "/" + GIT_PATH);
			}
			final GitServlet gitServlet = new GitServlet();
			gitServlet.setRepositoryResolver(fsProvider.getRepositoryResolver());
			gitServlet.setAsIsFileService(null);
			gitServlet.setReceivePackFactory((req, db) -> fsProvider.getReceivePack("http", req, db));
			ServletRegistration.Dynamic sd = servletContext.addServlet("GitServlet", gitServlet);
			sd.addMapping("/" + GIT_PATH + "/*");
			sd.setLoadOnStartup(1);
			sd.setAsyncSupported(false);
		}
	}

	@Override
	public void contextDestroyed(ServletContextEvent sce) {
		servletContext = null;
	}

	protected JGitFileSystemProvider resolveProvider() {
		try {
			// TODO: change this
			return null;
			// return (JGitFileSystemProvider)
			// FileSystemProviders.resolveProvider(URI.create(JGitFileSystemProviderConfiguration.SCHEME
			// + "://whatever"));
		} catch (Exception ex) {
			LOG.error("Error trying to resolve JGitFileSystemProvider.", ex);
		}
		return null;
	}
}
