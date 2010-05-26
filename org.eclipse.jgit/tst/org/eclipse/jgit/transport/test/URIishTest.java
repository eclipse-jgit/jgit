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

package org.eclipse.jgit.transport.test;

import java.net.URISyntaxException;

import junit.framework.TestCase;
import org.eclipse.jgit.transport.URIish;

/**
 * Tests for the URIish class.
 */
public class URIishTest extends TestCase {

	/**
	 * Test that the string constructor works.
	 * @throws Exception
	 */
	public void testStringConstructor() throws Exception {
		URIish uri = new URIish("http://some.hostname.com/foo/bar");
		assertEquals("http", uri.getScheme());
		assertEquals("some.hostname.com", uri.getHost());
		assertEquals("/foo/bar", uri.getPath());

		uri = new URIish("http://some.hostname.com:8080/foo/bar");
		assertEquals("http", uri.getScheme());
		assertEquals("some.hostname.com", uri.getHost());
		assertEquals(8080, uri.getPort());
		assertEquals("/foo/bar", uri.getPath());

		uri = new URIish("http://anonymous:secret@some.hostname.com:8080/foo/bar");
		assertEquals("http", uri.getScheme());
		assertEquals("anonymous", uri.getUser());
		assertEquals("secret", uri.getPass());
		assertEquals("some.hostname.com", uri.getHost());
		assertEquals(8080, uri.getPort());
		assertEquals("/foo/bar", uri.getPath());

		uri = new URIish("some.hostname.com:/foo/bar");
		assertEquals("some.hostname.com", uri.getHost());
		assertEquals("/foo/bar", uri.getPath());

		uri = new URIish("http://some.hostname.com:/foo/bar");
		assertEquals("http", uri.getScheme());
		assertEquals("some.hostname.com", uri.getHost());
		assertEquals("/foo/bar", uri.getPath());

	}

	/**
	 * Test for error cases.
	 * @throws Exception
	 */
	public void testStringConstructorErrorCases() throws Exception {
		try {
			URIish uri = new URIish("some.hostname.com/foo/bar");
			fail("Should have thrown a URISyntaxException for " + uri);
		} catch (URISyntaxException e) {
			// Expected
		}
	}
}
