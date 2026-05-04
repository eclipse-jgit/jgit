/*
 * Copyright (C) 2018 Gabriel Couto <gmcouto@gmail.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.transport.http.apache;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static java.nio.charset.StandardCharsets.UTF_8;


import java.net.MalformedURLException;
import java.util.List;
import java.util.Locale;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.message.AbstractHttpMessage;
import org.apache.http.entity.ByteArrayEntity;
import org.junit.Test;

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

	@Test
	public void testGetErrorStreamReturnsNullWhenNoResponse()
			throws Exception {
		HttpClientConnection connection = new HttpClientConnection(
				"http://0.0.0.0/");
		// resp is null by default
		assertEquals(null, connection.getErrorStream());
	}

	@Test
	public void testGetErrorStreamReturnsNullForSuccessResponse()
			throws Exception {
		HttpClientConnection connection = new HttpClientConnection(
				"http://0.0.0.0/");
		connection.resp = new HttpResponseMock(200, null);
		assertEquals(null, connection.getErrorStream());
	}

	@Test
	public void testGetErrorStreamReturnBodyForErrorResponse()
			throws Exception {
		byte[] body = "Access denied".getBytes(UTF_8);
		HttpClientConnection connection = new HttpClientConnection(
				"http://0.0.0.0/");
		connection.resp = new HttpResponseMock(403, body);
		try (java.io.InputStream es = connection.getErrorStream()) {
			byte[] actual = es.readAllBytes();
			assertEquals("Access denied", new String(actual, UTF_8));
		}
	}

	private static class HttpResponseMock extends AbstractHttpMessage
			implements HttpResponse {

		private final int statusCode;
		private final byte[] body;

		HttpResponseMock() {
			this(0, null);
		}

		HttpResponseMock(int statusCode, byte[] body) {
			this.statusCode = statusCode;
			this.body = body;
		}

		@Override
		public StatusLine getStatusLine() {
			return new org.apache.http.message.BasicStatusLine(
					new ProtocolVersion("HTTP", 1, 1), statusCode, "");
		}
		@Override
		public void setStatusLine(StatusLine statusLine) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void setStatusLine(ProtocolVersion protocolVersion, int i) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void setStatusLine(ProtocolVersion protocolVersion, int i,
				String s) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void setStatusCode(int i) throws IllegalStateException {
			throw new UnsupportedOperationException();
		}

		@Override
		public void setReasonPhrase(String s) throws IllegalStateException {
			throw new UnsupportedOperationException();
		}

		@Override
		public HttpEntity getEntity() {
			if (body == null) {
				return null;
			}
			return new ByteArrayEntity(body);
		}
		@Override
		public void setEntity(HttpEntity httpEntity) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Locale getLocale() {
			throw new UnsupportedOperationException();
		}

		@Override
		public void setLocale(Locale locale) {
			throw new UnsupportedOperationException();
		}

		@Override
		public ProtocolVersion getProtocolVersion() {
			throw new UnsupportedOperationException();
		}
	}
}
