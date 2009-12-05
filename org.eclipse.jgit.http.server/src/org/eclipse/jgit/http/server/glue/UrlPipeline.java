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

package org.eclipse.jgit.http.server.glue;

import java.io.IOException;
import java.util.Enumeration;
import java.util.NoSuchElementException;
import java.util.Set;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

abstract class UrlPipeline {
	private final Filter[] filters;

	private final HttpServlet servlet;

	UrlPipeline(final Filter[] filters, final HttpServlet servlet) {
		this.filters = filters;
		this.servlet = servlet;
	}

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
			ref.init(new FilterConfig() {
				public String getInitParameter(String name) {
					return null;
				}

				public Enumeration getInitParameterNames() {
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

				public String getFilterName() {
					return ref.getClass().getName();
				}
			});
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

				public Enumeration getInitParameterNames() {
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

	abstract boolean match(HttpServletRequest req);

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
