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

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.http.server.resolver.RepositoryResolver;
import org.eclipse.jgit.lib.Repository;

class ServletDefinition {
	private final Pattern pattern;

	private final HttpServlet servlet;

	private final RepositoryResolver resolver;

	ServletDefinition(final String pattern, final HttpServlet servlet,
			final RepositoryResolver resolver) {
		this.pattern = Pattern.compile(pattern);
		this.servlet = servlet;
		this.resolver = resolver;
	}

	void init(final ServletContext context) throws ServletException {
		final String name = servlet.getClass().getName();
		servlet.init(new SimpleServletConfig(name, context));
	}

	void destroy() {
		servlet.destroy();
	}

	boolean canService(final HttpServletRequest req) {
		final String path = req.getServletPath();
		return path != null && pattern.matcher(path).matches();
	}

	void service(final HttpServletRequest req, final HttpServletResponse rsp)
			throws IOException, ServletException {
		final Matcher m = pattern.matcher(req.getServletPath());
		if (!m.matches()) {
			// This should have been caught above in canService.
			//
			rsp.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}

		int pathInfoGroup = 1;
		Repository db = null;
		try {
			if (servlet instanceof RepositoryServlet) {
				final String name = m.group(pathInfoGroup++);
				try {
					db = resolver.open(req, name);
				} catch (RepositoryNotFoundException notFound) {
					servlet.getServletContext().log(
							"Repository \"" + name + "\" not found",
							notFound.getCause());
					rsp.sendError(HttpServletResponse.SC_NOT_FOUND);
					return;
				}
				RepositoryServlet.setRepository(req, db);
			}

			servlet.service(new WrappedRequest(req, m, pathInfoGroup), rsp);
		} finally {
			if (db != null)
				db.close();
		}
	}
}
