/*
 * Copyright (C) 2018 Gabriel Couto <gmcouto@gmail.com>
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
package org.eclipse.jgit.transport.http.apache;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.message.AbstractHttpMessage;
import org.junit.Test;

import java.net.MalformedURLException;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HttpClientConnectionTest {
	@Test
	public void testGetHeaderFieldsAllowMultipleValues()
			throws MalformedURLException {
		HttpResponse responseMock = new HttpResponseMock();
		String headerField = "WWW-Authenticate";
		responseMock.addHeader(headerField, "Basic");
		responseMock.addHeader(headerField, "Digest");
		responseMock.addHeader(headerField, "NTLM");
		HttpClientConnection connection = new HttpClientConnection(
				"http://0.0.0.0/");
		connection.resp = responseMock;
		List<String> headerValues = connection.getHeaderFields()
				.get(headerField);
		assertEquals(3, headerValues.size());
		assertTrue(headerValues.contains("Basic"));
		assertTrue(headerValues.contains("Digest"));
		assertTrue(headerValues.contains("NTLM"));
	}

	private class HttpResponseMock extends AbstractHttpMessage
			implements HttpResponse {
		@Override
		public StatusLine getStatusLine() {
			return null;
		}

		@Override
		public void setStatusLine(StatusLine statusLine) {

		}

		@Override
		public void setStatusLine(ProtocolVersion protocolVersion, int i) {

		}

		@Override
		public void setStatusLine(ProtocolVersion protocolVersion, int i,
				String s) {

		}

		@Override
		public void setStatusCode(int i) throws IllegalStateException {

		}

		@Override
		public void setReasonPhrase(String s) throws IllegalStateException {

		}

		@Override
		public HttpEntity getEntity() {
			return null;
		}

		@Override
		public void setEntity(HttpEntity httpEntity) {

		}

		@Override
		public Locale getLocale() {
			return null;
		}

		@Override
		public void setLocale(Locale locale) {

		}

		@Override
		public ProtocolVersion getProtocolVersion() {
			return null;
		}
	}
}
