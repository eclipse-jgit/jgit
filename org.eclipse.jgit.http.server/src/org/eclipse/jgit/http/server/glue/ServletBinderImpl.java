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

import java.util.ArrayList;
import java.util.List;

import javax.servlet.Filter;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jgit.http.server.HttpServerText;

abstract class ServletBinderImpl implements ServletBinder {
	private final List<Filter> filters;

	private HttpServlet httpServlet;

	ServletBinderImpl() {
		this.filters = new ArrayList<>();
	}

	/** {@inheritDoc} */
	@Override
	public ServletBinder through(Filter filter) {
		if (filter == null)
			throw new NullPointerException(HttpServerText.get().filterMustNotBeNull);
		filters.add(filter);
		return this;
	}

	/** {@inheritDoc} */
	@Override
	public void with(HttpServlet servlet) {
		if (servlet == null)
			throw new NullPointerException(HttpServerText.get().servletMustNotBeNull);
		if (httpServlet != null)
			throw new IllegalStateException(HttpServerText.get().servletWasAlreadyBound);
		httpServlet = servlet;
	}

	/**
	 * Get the servlet
	 *
	 * @return the configured servlet, or singleton returning 404 if none.
	 */
	protected HttpServlet getServlet() {
		if (httpServlet != null)
			return httpServlet;
		else
			return new ErrorServlet(HttpServletResponse.SC_NOT_FOUND);
	}

	/**
	 * Get filters
	 *
	 * @return the configured filters; zero-length array if none.
	 */
	protected Filter[] getFilters() {
		return filters.toArray(new Filter[filters.size()]);
	}

	/** @return the pipeline that matches and executes this chain. */
	abstract UrlPipeline create();
}
