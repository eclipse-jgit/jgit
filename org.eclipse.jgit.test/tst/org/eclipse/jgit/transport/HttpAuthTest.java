/*
 * Copyright (C) 2013, Microsoft Corporation
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

package org.eclipse.jgit.transport;

import static org.junit.Assert.fail;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.transport.http.JDKHttpConnection;
import org.junit.Test;

public class HttpAuthTest {
	private static String digestHeader = "WWW-Authenticate: Digest qop=\"auth\",algorithm=MD5-sess,nonce=\"+Upgraded+v1b9...ba\",charset=utf-8,realm=\"Digest\"";

	private static String basicHeader = "WWW-Authenticate: Basic realm=\"everyones.loves.git\"";

	private static String ntlmHeader = "WWW-Authenticate: NTLM";

	private static String bearerHeader = "WWW-Authenticate: Bearer";

	private static String URL_SAMPLE = "http://everyones.loves.git/u/2";

	private static String BASIC = "Basic";

	private static String DIGEST = "Digest";

	@Test
	public void testHttpAuthScanResponse() {
		checkResponse(new String[] { basicHeader }, BASIC);
		checkResponse(new String[] { digestHeader }, DIGEST);
		checkResponse(new String[] { basicHeader, digestHeader }, DIGEST);
		checkResponse(new String[] { digestHeader, basicHeader }, DIGEST);
		checkResponse(new String[] { ntlmHeader, basicHeader, digestHeader,
				bearerHeader }, DIGEST);
		checkResponse(new String[] { ntlmHeader, basicHeader, bearerHeader },
				BASIC);
	}

	private static void checkResponse(String[] headers,
			String expectedAuthMethod) {

		AuthHeadersResponse response = null;
		try {
			response = new AuthHeadersResponse(headers);
		} catch (IOException e) {
			fail("Couldn't instantiate AuthHeadersResponse: " + e.toString());
		}
		HttpAuthMethod authMethod = HttpAuthMethod.scanResponse(response);

		if (!expectedAuthMethod.equals(getAuthMethodName(authMethod))) {
			fail("Wrong authentication method: expected " + expectedAuthMethod
					+ ", but received " + getAuthMethodName(authMethod));
		}
	}

	private static String getAuthMethodName(HttpAuthMethod authMethod) {
		return authMethod.getClass().getSimpleName();
	}

	private static class AuthHeadersResponse extends JDKHttpConnection {
		Map<String, List<String>> headerFields = new HashMap<String, List<String>>();

		public AuthHeadersResponse(String[] authHeaders)
				throws MalformedURLException, IOException {
			super(new URL(URL_SAMPLE));
			parseHeaders(authHeaders);
		}

		@Override
		public void disconnect() {
			fail("The disconnect method shouldn't be invoked");
		}

		@Override
		public boolean usingProxy() {
			return false;
		}

		@Override
		public void connect() throws IOException {
			fail("The connect method shouldn't be invoked");
		}

		@Override
		public String getHeaderField(String name) {
			if (!headerFields.containsKey(name))
				return null;
			else {
				int n = headerFields.get(name).size();

				if (n > 0)
					return headerFields.get(name).get(n - 1);
				else
					return null;
			}
		}

		@Override
		public Map<String, List<String>> getHeaderFields() {
			return headerFields;
		}

		private void parseHeaders(String[] headers) {
			for (String header : headers) {
				int i = header.indexOf(':');

				if (i < 0)
					continue;

				String key = header.substring(0, i);
				String value = header.substring(i + 1).trim();

				if (!headerFields.containsKey(key))
					headerFields.put(key, new ArrayList<String>());

				List<String> values = headerFields.get(key);
				values.add(value);
			}
		}
	}
}
