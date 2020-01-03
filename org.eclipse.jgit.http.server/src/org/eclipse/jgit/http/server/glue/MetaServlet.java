/*
 * Copyright (C) 2009-2010, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.http.server.glue;

import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;

import java.io.IOException;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Generic container servlet to manage routing to different pipelines.
 * <p>
 * Callers can create and configure a new processing pipeline by using one of
 * the {@link #serve(String)} or {@link #serveRegex(String)} methods to allocate
 * a binder for a particular URL pattern.
 * <p>
 * Registered filters and servlets are initialized lazily, usually during the
 * first request. Once initialized the bindings in this servlet cannot be
 * modified without destroying the servlet and thereby destroying all registered
 * filters and servlets.
 */
public class MetaServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;

	private final MetaFilter filter;

	/**
	 * Empty servlet with no bindings.
	 */
	public MetaServlet() {
		this(new MetaFilter());
	}

	/**
	 * Initialize a servlet wrapping a filter.
	 *
	 * @param delegateFilter
	 *            the filter being wrapped by the servlet.
	 */
	protected MetaServlet(MetaFilter delegateFilter) {
		filter = delegateFilter;
	}

	/**
	 * Get delegate filter
	 *
	 * @return filter this servlet delegates all routing logic to.
	 */
	protected MetaFilter getDelegateFilter() {
		return filter;
	}

	/**
	 * Construct a binding for a specific path.
	 *
	 * @param path
	 *            pattern to match.
	 * @return binder for the passed path.
	 */
	public ServletBinder serve(String path) {
		return filter.serve(path);
	}

	/**
	 * Construct a binding for a regular expression.
	 *
	 * @param expression
	 *            the regular expression to pattern match the URL against.
	 * @return binder for the passed expression.
	 */
	public ServletBinder serveRegex(String expression) {
		return filter.serveRegex(expression);
	}

	/** {@inheritDoc} */
	@Override
	public void init(ServletConfig config) throws ServletException {
		String name = filter.getClass().getName();
		ServletContext ctx = config.getServletContext();
		filter.init(new NoParameterFilterConfig(name, ctx));
	}

	/** {@inheritDoc} */
	@Override
	public void destroy() {
		filter.destroy();
	}

	/** {@inheritDoc} */
	@Override
	protected void service(HttpServletRequest req, HttpServletResponse res)
			throws ServletException, IOException {
		filter.doFilter(req, res,
				(ServletRequest request, ServletResponse response) -> {
					((HttpServletResponse) response).sendError(SC_NOT_FOUND);
				});
	}

	/**
	 * Configure a newly created binder.
	 *
	 * @param b
	 *            the newly created binder.
	 * @return binder for the caller, potentially after adding one or more
	 *         filters into the pipeline.
	 */
	protected ServletBinder register(ServletBinder b) {
		return filter.register(b);
	}
}
