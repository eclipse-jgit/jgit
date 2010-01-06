/*
 * Copyright (C) 2009-2010, Google Inc.
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

import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static javax.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;
import static org.eclipse.jgit.http.server.ServletUtils.ATTRIBUTE_REPOSITORY;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.http.server.resolver.RepositoryResolver;
import org.eclipse.jgit.http.server.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.http.server.resolver.ServiceNotEnabledException;
import org.eclipse.jgit.lib.Repository;

/**
 * Opens a repository named by the path info through {@link RepositoryResolver}.
 * <p>
 * This filter assumes it is invoked by {@link GitServlet} and is likely to not
 * work as expected if called from any other class. This filter assumes the path
 * info of the current request is a repository name which can be used by the
 * configured {@link RepositoryResolver} to open a {@link Repository} and attach
 * it to the current request.
 * <p>
 * This filter sets request attribute {@link ServletUtils#ATTRIBUTE_REPOSITORY}
 * when it discovers the repository, and automatically closes and removes the
 * attribute when the request is complete.
 */
public class RepositoryFilter implements Filter {
	private final RepositoryResolver resolver;

	private ServletContext context;

	/**
	 * Create a new filter.
	 *
	 * @param resolver
	 *            the resolver which will be used to translate the URL name
	 *            component to the actual {@link Repository} instance for the
	 *            current web request.
	 */
	public RepositoryFilter(final RepositoryResolver resolver) {
		this.resolver = resolver;
	}

	public void init(final FilterConfig config) throws ServletException {
		context = config.getServletContext();
	}

	public void destroy() {
		context = null;
	}

	public void doFilter(final ServletRequest request,
			final ServletResponse rsp, final FilterChain chain)
			throws IOException, ServletException {
		if (request.getAttribute(ATTRIBUTE_REPOSITORY) != null) {
			context.log("Internal server error, request attribute "
					+ ATTRIBUTE_REPOSITORY + " was already set when "
					+ getClass().getName() + " was invoked.");
			((HttpServletResponse) rsp).sendError(SC_INTERNAL_SERVER_ERROR);
			return;
		}

		final HttpServletRequest req = (HttpServletRequest) request;

		String name = req.getPathInfo();
		if (name == null || name.length() == 0) {
			((HttpServletResponse) rsp).sendError(SC_NOT_FOUND);
			return;
		}
		if (name.startsWith("/"))
			name = name.substring(1);

		final Repository db;
		try {
			db = resolver.open(req, name);
		} catch (RepositoryNotFoundException e) {
			((HttpServletResponse) rsp).sendError(SC_NOT_FOUND);
			return;
		} catch (ServiceNotAuthorizedException e) {
			((HttpServletResponse) rsp).sendError(SC_UNAUTHORIZED);
			return;
		} catch (ServiceNotEnabledException e) {
			((HttpServletResponse) rsp).sendError(SC_FORBIDDEN);
			return;
		}
		try {
			request.setAttribute(ATTRIBUTE_REPOSITORY, db);
			chain.doFilter(request, rsp);
		} finally {
			request.removeAttribute(ATTRIBUTE_REPOSITORY);
			db.close();
		}
	}
}
