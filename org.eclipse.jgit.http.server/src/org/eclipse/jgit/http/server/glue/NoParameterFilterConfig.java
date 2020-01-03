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

import java.util.Enumeration;
import java.util.NoSuchElementException;

import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;

final class NoParameterFilterConfig implements FilterConfig {
	private final String filterName;

	private final ServletContext context;

	NoParameterFilterConfig(String filterName, ServletContext context) {
		this.filterName = filterName;
		this.context = context;
	}

	/** {@inheritDoc} */
	@Override
	public String getInitParameter(String name) {
		return null;
	}

	/** {@inheritDoc} */
	@Override
	public Enumeration<String> getInitParameterNames() {
		return new Enumeration<String>() {
			@Override
			public boolean hasMoreElements() {
				return false;
			}

			@Override
			public String nextElement() {
				throw new NoSuchElementException();
			}
		};
	}

	/** {@inheritDoc} */
	@Override
	public ServletContext getServletContext() {
		return context;
	}

	/** {@inheritDoc} */
	@Override
	public String getFilterName() {
		return filterName;
	}
}
