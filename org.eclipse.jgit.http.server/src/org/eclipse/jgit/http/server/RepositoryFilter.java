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
import static org.eclipse.jgit.http.server.GitSmartHttpTools.sendError;
import static org.eclipse.jgit.http.server.ServletUtils.ATTRIBUTE_REPOSITORY;

import java.io.IOException;
import java.text.MessageFormat;

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
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.ServiceMayNotContinueException;
import org.eclipse.jgit.transport.resolver.RepositoryResolver;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;

/**
 * Open a repository named by the path info through
 * {@link org.eclipse.jgit.transport.resolver.RepositoryResolver}.
 * <p>
 * This filter assumes it is invoked by
 * {@link org.eclipse.jgit.http.server.GitServlet} and is likely to not work as
 * expected if called from any other class. This filter assumes the path info of
 * the current request is a repository name which can be used by the configured
 * {@link org.eclipse.jgit.transport.resolver.RepositoryResolver} to open a
 * {@link org.eclipse.jgit.lib.Repository} and attach it to the current request.
 * <p>
 * This filter sets request attribute
 * {@link org.eclipse.jgit.http.server.ServletUtils#ATTRIBUTE_REPOSITORY} when
 * it discovers the repository, and automatically closes and removes the
 * attribute when the request is complete.
 */
public class RepositoryFilter implements Filter {
	private final RepositoryResolver<HttpServletRequest> resolver;

	private ServletContext context;

	/**
	 * Create a new filter.
	 *
	 * @param resolver
	 *            the resolver which will be used to translate the URL name
	 *            component to the actual
	 *            {@link org.eclipse.jgit.lib.Repository} instance for the
	 *            current web request.
	 */
	public RepositoryFilter(RepositoryResolver<HttpServletRequest> resolver) {
		this.resolver = resolver;
	}

	/** {@inheritDoc} */
	@Override
	public void init(FilterConfig config) throws ServletException {
		context = config.getServletContext();
	}

	/** {@inheritDoc} */
	@Override
	public void destroy() {
		context = null;
	}

	/** {@inheritDoc} */
	@Override
	public void doFilter(final ServletRequest request,
			final ServletResponse response, final FilterChain chain)
			throws IOException, ServletException {
		HttpServletRequest req = (HttpServletRequest) request;
		HttpServletResponse res = (HttpServletResponse) response;

		if (request.getAttribute(ATTRIBUTE_REPOSITORY) != null) {
			context.log(MessageFormat.format(HttpServerText.get().internalServerErrorRequestAttributeWasAlreadySet
					, ATTRIBUTE_REPOSITORY
					, getClass().getName()));
			sendError(req, res, SC_INTERNAL_SERVER_ERROR);
			return;
		}

		String name = req.getPathInfo();
		while (name != null && 0 < name.length() && name.charAt(0) == '/')
			name = name.substring(1);
		if (name == null || name.length() == 0) {
			sendError(req, res, SC_NOT_FOUND);
			return;
		}

		try (Repository db = resolver.open(req, name)) {
			request.setAttribute(ATTRIBUTE_REPOSITORY, db);
			chain.doFilter(request, response);
		} catch (RepositoryNotFoundException e) {
			sendError(req, res, SC_NOT_FOUND);
			return;
		} catch (ServiceNotEnabledException e) {
			sendError(req, res, SC_FORBIDDEN, e.getMessage());
			return;
		} catch (ServiceNotAuthorizedException e) {
			res.sendError(SC_UNAUTHORIZED, e.getMessage());
			return;
		} catch (ServiceMayNotContinueException e) {
			sendError(req, res, e.getStatusCode(), e.getMessage());
			return;
		} finally {
			request.removeAttribute(ATTRIBUTE_REPOSITORY);
		}
	}
}
