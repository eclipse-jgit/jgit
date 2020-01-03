/*
 * Copyright (C) 2010, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.junit.http;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;

/**
 * Mock ServletConfig
 */
public class MockServletConfig implements ServletConfig {
	private final Map<String, String> parameters = new HashMap<>();

	/**
	 * Set init parameter.
	 *
	 * @param name
	 * @param value
	 */
	public void setInitParameter(String name, String value) {
		parameters.put(name, value);
	}

	/** {@inheritDoc} */
	@Override
	public String getInitParameter(String name) {
		return parameters.get(name);
	}

	/** {@inheritDoc} */
	@Override
	public Enumeration<String> getInitParameterNames() {
		final Iterator<String> i = parameters.keySet().iterator();
		return new Enumeration<String>() {
			@Override
			public boolean hasMoreElements() {
				return i.hasNext();
			}

			@Override
			public String nextElement() {
				return i.next();
			}
		};
	}

	/** {@inheritDoc} */
	@Override
	public String getServletName() {
		return "MOCK_SERVLET";
	}

	/** {@inheritDoc} */
	@Override
	public ServletContext getServletContext() {
		return null;
	}
}
