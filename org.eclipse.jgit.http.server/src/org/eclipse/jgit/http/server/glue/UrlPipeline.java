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

package org.eclipse.jgit.http.server.glue;

import java.io.IOException;
import java.util.Enumeration;
import java.util.NoSuchElementException;
import java.util.Set;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Encapsulates the entire serving stack for a single URL.
 * <p>
 * Subclasses provide the implementation of {@link #match(HttpServletRequest)},
 * which is called by {@link MetaServlet} in registration order to determine the
 * pipeline that will be used to handle a request.
 * <p>
 * The very bottom of each pipeline is a single {@link HttpServlet} that will
 * handle producing the response for this pipeline's URL. {@link Filter}s may
 * also be registered and applied around the servlet's processing, to manage
 * request attributes, set standard response headers, or completely override the
 * response generation.
 */
abstract class UrlPipeline {
	/** Filters to apply around {@link #servlet}; may be empty but never null. */
	private final Filter[] filters;

	/** Instance that must generate the response; never null. */
	private final HttpServlet servlet;

	UrlPipeline(final Filter[] filters, final HttpServlet servlet) {
		this.filters = filters;
		this.servlet = servlet;
	}

	/**
	 * Initialize all contained filters and servlets.
	 *
	 * @param context
	 *            the servlet container context our {@link MetaServlet} is
	 *            running within.
	 * @param inited
	 *            <i>(input/output)</i> the set of filters and servlets which
	 *            have already been initialized within the container context. If
	 *            those same instances appear in this pipeline they are not
	 *            initialized a second time. Filters and servlets that are first
	 *            initialized by this pipeline will be added to this set.
	 * @throws ServletException
	 *             a filter or servlet is unable to initialize.
	 */
	void init(final ServletContext context, final Set<Object> inited)
			throws ServletException {
		for (Filter ref : filters)
			initFilter(ref, context, inited);
		initServlet(servlet, context, inited);
	}

	private static void initFilter(final Filter ref,
			final ServletContext context, final Set<Object> inited)
			throws ServletException {
		if (!inited.contains(ref)) {
			ref.init(new NoParameterFilterConfig(ref.getClass().getName(),
					context));
			inited.add(ref);
		}
	}

	private static void initServlet(final HttpServlet ref,
			final ServletContext context, final Set<Object> inited)
			throws ServletException {
		if (!inited.contains(ref)) {
			ref.init(new ServletConfig() {
				public String getInitParameter(String name) {
					return null;
				}

				public Enumeration<String> getInitParameterNames() {
					return new Enumeration<String>() {
						public boolean hasMoreElements() {
							return false;
						}

						public String nextElement() {
							throw new NoSuchElementException();
						}
					};
				}

				public ServletContext getServletContext() {
					return context;
				}

				public String getServletName() {
					return ref.getClass().getName();
				}
			});
			inited.add(ref);
		}
	}

	/**
	 * Destroy all contained filters and servlets.
	 *
	 * @param destroyed
	 *            <i>(input/output)</i> the set of filters and servlets which
	 *            have already been destroyed within the container context. If
	 *            those same instances appear in this pipeline they are not
	 *            destroyed a second time. Filters and servlets that are first
	 *            destroyed by this pipeline will be added to this set.
	 */
	void destroy(final Set<Object> destroyed) {
		for (Filter ref : filters)
			destroyFilter(ref, destroyed);
		destroyServlet(servlet, destroyed);
	}

	private static void destroyFilter(Filter ref, Set<Object> destroyed) {
		if (!destroyed.contains(ref)) {
			ref.destroy();
			destroyed.add(ref);
		}
	}

	private static void destroyServlet(HttpServlet ref, Set<Object> destroyed) {
		if (!destroyed.contains(ref)) {
			ref.destroy();
			destroyed.add(ref);
		}
	}

	/**
	 * Determine if this pipeline handles the request's URL.
	 * <p>
	 * This method should match on the request's {@code getPathInfo()} method,
	 * as {@link MetaServlet} passes the request along as-is to each pipeline's
	 * match method.
	 *
	 * @param req
	 *            current HTTP request being considered by {@link MetaServlet}.
	 * @return {@code true} if this pipeline is configured to handle the
	 *         request; {@code false} otherwise.
	 */
	abstract boolean match(HttpServletRequest req);

	/**
	 * Execute the filters and the servlet on the request.
	 * <p>
	 * Invoked by {@link MetaServlet} once {@link #match(HttpServletRequest)}
	 * has determined this pipeline is the correct pipeline to handle the
	 * current request.
	 *
	 * @param req
	 *            current HTTP request.
	 * @param rsp
	 *            current HTTP response.
	 * @throws ServletException
	 *             request cannot be completed.
	 * @throws IOException
	 *             IO error prevents the request from being completed.
	 */
	void service(HttpServletRequest req, HttpServletResponse rsp)
			throws ServletException, IOException {
		if (0 < filters.length)
			new Chain(filters, servlet).doFilter(req, rsp);
		else
			servlet.service(req, rsp);
	}

	private static class Chain implements FilterChain {
		private final Filter[] filters;

		private final HttpServlet servlet;

		private int filterIdx;

		Chain(final Filter[] filters, final HttpServlet servlet) {
			this.filters = filters;
			this.servlet = servlet;
		}

		public void doFilter(ServletRequest req, ServletResponse rsp)
				throws IOException, ServletException {
			if (filterIdx < filters.length)
				filters[filterIdx++].doFilter(req, rsp, this);
			else
				servlet.service(req, rsp);
		}
	}
}
