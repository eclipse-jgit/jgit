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

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Fields;

/**
 * A single request made through {@link org.eclipse.jgit.junit.http.AppServer}.
 */
public class AccessEvent {
	private final String method;

	private final HttpURI uri;

	private final Map<String, String> requestHeaders;

	private final Map<String, String[]> parameters;

	private int status;

	private Map<String, String> responseHeaders;

	AccessEvent(Request req) {
		method = req.getMethod();
		uri = req.getHttpURI();
		requestHeaders = cloneHeaders(req);
		parameters = cloneParameters(req);
	}

	void setResponse(Response rsp) {
		status = rsp.getStatus();
		responseHeaders = cloneHeaders(rsp);
	}

	private static Map<String, String> cloneHeaders(Request req) {
		Map<String, String> r = new TreeMap<>();
		for (HttpField f : req.getHeaders()) {
			String key = f.getName();
			if (!r.containsKey(key)) {
				r.put(key, f.getValue());
			}
		}
		return Collections.unmodifiableMap(r);
	}

	private static Map<String, String> cloneHeaders(Response rsp) {
		Map<String, String> r = new TreeMap<>();
		for (HttpField f : rsp.getHeaders()) {
			String key = f.getName();
			if (!r.containsKey(key)) {
				r.put(key, f.getValue());
			}
		}
		return Collections.unmodifiableMap(r);
	}

	private static Map<String, String[]> cloneParameters(Request req) {
		Map<String, String[]> r = new TreeMap<>();

		Fields fields;
		try {
			fields = Request.getParameters(req);
			for (String n : fields.getNames()) {
				r.put(n, fields.getValues(n).toArray(new String[0]));
			}
		} catch (Exception e) {
			throw new RuntimeException("Failed to extract request parameters",
					e);
		}
		return r;
	}

	/**
	 * Get the <code>method</code>.
	 *
	 * @return {@code "GET"} or {@code "POST"}
	 */
	public String getMethod() {
		return method;
	}

	/**
	 * Get <code>path</code>.
	 *
	 * @return path of the file on the server, e.g. {@code /git/HEAD}.
	 */
	public String getPath() {
		return uri.getPath();
	}

	/**
	 * Get request header
	 *
	 * @param name
	 *            name of the request header to read.
	 * @return first value of the request header; null if not sent.
	 */
	public String getRequestHeader(String name) {
		return requestHeaders.get(name);
	}

	/**
	 * Get parameter
	 *
	 * @param name
	 *            name of the request parameter to read.
	 * @return first value of the request parameter; null if not sent.
	 */
	public String getParameter(String name) {
		String[] r = parameters.get(name);
		return r != null && 1 <= r.length ? r[0] : null;
	}

	/**
	 * Get <code>parameters</code>
	 *
	 * @return all parameters in the request.
	 */
	public Map<String, String[]> getParameters() {
		return parameters;
	}

	/**
	 * Get the <code>status</code>.
	 *
	 * @return HTTP status code of the response, e.g. 200, 403, 500.
	 */
	public int getStatus() {
		return status;
	}

	/**
	 * Get response header.
	 *
	 * @param name
	 *            name of the response header to read.
	 * @return first value of the response header; null if not sent.
	 */
	public String getResponseHeader(String name) {
		return responseHeaders != null ? responseHeaders.get(name) : null;
	}

	@Override
	public String toString() {
		StringBuilder b = new StringBuilder();
		b.append(method);
		b.append(' ');
		b.append(uri.getPath());
		if (!parameters.isEmpty()) {
			b.append('?');
			boolean first = true;
			for (Map.Entry<String, String[]> e : parameters.entrySet()) {
				for (String val : e.getValue()) {
					if (!first) {
						b.append('&');
					}
					first = false;

					b.append(e.getKey());
					b.append('=');
					b.append(val);
				}
			}
		}
		b.append(' ');
		b.append(status);
		return b.toString();
	}
}
