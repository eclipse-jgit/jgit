/*
 * Copyright (C) 2009, Google Inc.
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

package org.eclipse.jgit.http.server;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jgit.http.server.resolver.FileResolver;
import org.eclipse.jgit.http.server.resolver.RepositoryResolver;

/**
 * Routes requests which match Git repository access over HTTP.
 * <p>
 * If a request cannot be handled by this filter, it is forwarded to the
 * remaining filter chain to allow additional application URLs to be handled.
 */
public class RepositoryRouter implements Filter {
	private RepositoryResolver resolver;

	private ServletContext context;

	private Collection<ServletDefinition> servlets;

	/**
	 * New router that will load its base directory from {@code web.xml}.
	 * <p>
	 * The required parameter {@code base-path} must be configured to point to
	 * the local filesystem directory where all served Git repositories reside.
	 */
	public RepositoryRouter() {
		this.resolver = null;
	}

	/**
	 * New router configured with a specific resolver.
	 * 
	 * @param resolver
	 *            the resolver to use when matching URL to Git repository.
	 */
	public RepositoryRouter(final RepositoryResolver resolver) {
		if (resolver == null)
			throw new NullPointerException("RepositoryResolver not supplied");
		this.resolver = resolver;
	}

	public void init(FilterConfig filterConfig) throws ServletException {
		if (resolver == null) {
			final String basePath = filterConfig.getInitParameter("base-path");
			if (basePath == null || "".equals(basePath))
				throw new ServletException("Filter parameter base-path not set");
			resolver = new FileResolver(new File(basePath));
		}

		context = filterConfig.getServletContext();
		servlets = new ArrayList<ServletDefinition>();

		bind("^/(.*)/(HEAD|refs/.*)$", new GetRefServlet());
		bind("^/(.*)/info/refs$", new InfoRefsServlet());
		bind("^/(.*)/objects/info/packs$", new InfoPacksServlet());
		bind("^/(.*)/objects/([0-9a-f]{2}/[0-9a-f]{38})$",
				new ObjectFileServlet.Loose());
		bind("^/(.*)/objects/(pack/pack-[0-9a-f]{40}\\.pack)$",
				new ObjectFileServlet.Pack());
		bind("^/(.*)/objects/(pack/pack-[0-9a-f]{40}\\.idx)$",
				new ObjectFileServlet.PackIdx());

		for (ServletDefinition d : servlets)
			d.init(context);
	}

	private void bind(final String pattern, final HttpServlet servlet) {
		servlets.add(new ServletDefinition(pattern, servlet, resolver));
	}

	public void destroy() {
		for (ServletDefinition d : servlets)
			d.destroy();
	}

	public void doFilter(final ServletRequest request,
			final ServletResponse response, final FilterChain chain)
			throws IOException, ServletException {
		final HttpServletRequest req = (HttpServletRequest) request;
		final HttpServletResponse rsp = (HttpServletResponse) response;

		for (ServletDefinition d : servlets) {
			if (d.canService(req)) {
				d.service(req, rsp);
				return;
			}
		}

		// No match above? Default to any other rules available.
		//
		chain.doFilter(req, rsp);
	}
}
