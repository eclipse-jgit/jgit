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

package org.eclipse.jgit.iplog;

import java.io.IOException;
import java.net.CookieHandler;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dumb implementation of a CookieManager for the JRE.
 * <p>
 * Cookies are keyed only by the host name in the URI. Cookie attributes like
 * domain and path are ignored to simplify the implementation.
 * <p>
 * If we are running on Java 6 or later we should favor using the standard
 * {@code java.net.CookieManager} class instead.
 */
public class SimpleCookieManager extends CookieHandler {
	private Map<String, Map<String, String>> byHost = new HashMap<String, Map<String, String>>();

	@Override
	public Map<String, List<String>> get(URI uri,
			Map<String, List<String>> requestHeaders) throws IOException {
		String host = hostOf(uri);

		Map<String, String> map = byHost.get(host);
		if (map == null || map.isEmpty())
			return requestHeaders;

		Map<String, List<String>> r = new HashMap<String, List<String>>();
		r.putAll(requestHeaders);
		StringBuilder buf = new StringBuilder();
		for (Map.Entry<String, String> e : map.entrySet()) {
			if (buf.length() > 0)
				buf.append("; ");
			buf.append(e.getKey());
			buf.append('=');
			buf.append(e.getValue());
		}
		r.put("Cookie", Collections.singletonList(buf.toString()));
		return Collections.unmodifiableMap(r);
	}

	@Override
	public void put(URI uri, Map<String, List<String>> responseHeaders)
			throws IOException {
		List<String> list = responseHeaders.get("Set-Cookie");
		if (list == null || list.isEmpty()) {
			return;
		}

		String host = hostOf(uri);
		Map<String, String> map = byHost.get(host);
		if (map == null) {
			map = new HashMap<String, String>();
			byHost.put(host, map);
		}

		for (String hdr : list) {
			String attributes[] = hdr.split(";");
			String nameValue = attributes[0].trim();
			int eq = nameValue.indexOf('=');
			String name = nameValue.substring(0, eq);
			String value = nameValue.substring(eq + 1);

			map.put(name, value);
		}
	}

	private String hostOf(URI uri) {
		StringBuilder key = new StringBuilder();
		key.append(uri.getScheme());
		key.append(':');
		key.append(uri.getHost());
		if (0 < uri.getPort())
			key.append(':' + uri.getPort());
		return key.toString();
	}
}
