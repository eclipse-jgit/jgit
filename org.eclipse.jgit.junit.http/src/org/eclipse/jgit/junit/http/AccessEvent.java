/*
 * Copyright (C) 2010, Google Inc.
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

package org.eclipse.jgit.junit.http;

import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import java.util.TreeMap;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;

/** A single request made through {@link AppServer}. */
public class AccessEvent {
	private final String method;

	private final String uri;

	private final Map<String, String> requestHeaders;

	private final Map<String, String[]> parameters;

	private final int status;

	private final Map<String, String> responseHeaders;

	AccessEvent(final Request req, final Response rsp) {
		method = req.getMethod();
		uri = req.getRequestURI();
		requestHeaders = cloneHeaders(req);
		parameters = clone(req.getParameterMap());

		status = rsp.getStatus();
		responseHeaders = cloneHeaders(rsp);
	}

	private static Map<String, String> cloneHeaders(final Request req) {
		Map<String, String> r = new TreeMap<String, String>();
		Enumeration hn = req.getHeaderNames();
		while (hn.hasMoreElements()) {
			String key = (String) hn.nextElement();
			if (!r.containsKey(key)) {
				r.put(key, req.getHeader(key));
			}
		}
		return Collections.unmodifiableMap(r);
	}

	private static Map<String, String> cloneHeaders(final Response rsp) {
		Map<String, String> r = new TreeMap<String, String>();
		Enumeration<String> hn = rsp.getHttpFields().getFieldNames();
		while (hn.hasMoreElements()) {
			String key = hn.nextElement();
			if (!r.containsKey(key)) {
				Enumeration<String> v = rsp.getHttpFields().getValues(key);
				r.put(key, v.nextElement());
			}
		}
		return Collections.unmodifiableMap(r);
	}

	@SuppressWarnings("unchecked")
	private static Map<String, String[]> clone(Map parameterMap) {
		return new TreeMap<String, String[]>(parameterMap);
	}

	/** @return {@code "GET"} or {@code "POST"} */
	public String getMethod() {
		return method;
	}

	/** @return path of the file on the server, e.g. {@code /git/HEAD}. */
	public String getPath() {
		return uri;
	}

	/**
	 * @param name
	 *            name of the request header to read.
	 * @return first value of the request header; null if not sent.
	 */
	public String getRequestHeader(String name) {
		return requestHeaders.get(name);
	}

	/**
	 * @param name
	 *            name of the request parameter to read.
	 * @return first value of the request parameter; null if not sent.
	 */
	public String getParameter(String name) {
		String[] r = parameters.get(name);
		return r != null && 1 <= r.length ? r[0] : null;
	}

	/** @return all parameters in the request. */
	public Map<String, String[]> getParameters() {
		return parameters;
	}

	/** @return HTTP status code of the response, e.g. 200, 403, 500. */
	public int getStatus() {
		return status;
	}

	/**
	 * @param name
	 *            name of the response header to read.
	 * @return first value of the response header; null if not sent.
	 */
	public String getResponseHeader(String name) {
		return responseHeaders.get(name);
	}

	public String toString() {
		StringBuilder b = new StringBuilder();
		b.append(method);
		b.append(' ');
		b.append(uri);
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
