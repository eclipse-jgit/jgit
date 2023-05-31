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
	 *            parameter name
	 * @param value
	 *            parameter value
	 */
	public void setInitParameter(String name, String value) {
		parameters.put(name, value);
	}

	@Override
	public String getInitParameter(String name) {
		return parameters.get(name);
	}

	@Override
	public Enumeration<String> getInitParameterNames() {
		final Iterator<String> i = parameters.keySet().iterator();
		return new Enumeration<>() {

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

	@Override
	public String getServletName() {
		return "MOCK_SERVLET";
	}

	@Override
	public ServletContext getServletContext() {
		return null;
	}
}
