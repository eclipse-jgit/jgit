/*
 * Copyright (C) 2009-2010, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.http.server;

import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import static org.eclipse.jgit.http.server.ServletUtils.getRepository;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jgit.internal.storage.file.ObjectDirectory;
import org.eclipse.jgit.lib.Repository;

/**
 * Requires the target {@link Repository} to be available via local filesystem.
 * <p>
 * The target {@link Repository} must be using a {@link ObjectDirectory}, so the
 * downstream servlet can directly access its contents on disk.
 */
class IsLocalFilter implements Filter {
	/** {@inheritDoc} */
	@Override
	public void init(FilterConfig config) throws ServletException {
		// Do nothing.
	}

	/** {@inheritDoc} */
	@Override
	public void destroy() {
		// Do nothing.
	}

	/** {@inheritDoc} */
	@Override
	public void doFilter(ServletRequest request, ServletResponse response,
			FilterChain chain) throws IOException, ServletException {
		if (isLocal(getRepository(request)))
			chain.doFilter(request, response);
		else
			((HttpServletResponse) response).sendError(SC_FORBIDDEN);
	}

	private static boolean isLocal(Repository db) {
		return db.getObjectDatabase() instanceof ObjectDirectory;
	}
}
