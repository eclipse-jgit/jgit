/*
 * Copyright (C) 2009, Mykola Nikishov <mn@mn.com.ua>
 * Copyright (C) 2008, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
 * Copyright (C) 2013, Robin Stocker <robin@nibor.org>
 * Copyright (C) 2015, Patrick Steinhardt <ps@pks.im> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.transport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;

import org.junit.Ignore;
import org.junit.Test;

public class URIishTest {

	private static final String GIT_SCHEME = "git://";

	@SuppressWarnings("unused")
	@Test(expected = URISyntaxException.class)
	public void shouldRaiseErrorOnEmptyURI() throws Exception {
		new URIish("");
	}

	@SuppressWarnings("unused")
	@Test(expected = URISyntaxException.class)
	public void shouldRaiseErrorOnNullURI() throws Exception {
		new URIish((String) null);
	}

	@Test
	public void testUnixFile() throws Exception {
		final String str = "/home/m y";
		URIish u = new URIish(str);
		assertNull(u.getScheme());
		assertFalse(u.isRemote());
		assertEquals(str, u.getRawPath());
		assertEquals(str, u.getPath());
		assertEquals(str, u.toString());
		assertEquals(str, u.toASCIIString());
		assertEquals(u, new URIish(str));
	}

	@Test
	public void testWindowsFile() throws Exception {
		final String str = "D:/m y";
		URIish u = new URIish(str);
		assertNull(u.getScheme());
		assertFalse(u.isRemote());
		assertEquals(str, u.getRawPath());
		assertEquals(str, u.getPath());
		assertEquals(str, u.toString());
		assertEquals(str, u.toASCIIString());
		assertEquals(u, new URIish(str));
	}

	@Test
	public void testWindowsFile2() throws Exception {
		final String str = "D:\\m y";
		URIish u = new URIish(str);
		assertNull(u.getScheme());
		assertFalse(u.isRemote());
		assertEquals("D:\\m y", u.getRawPath());
		assertEquals("D:\\m y", u.getPath());
		assertEquals("D:\\m y", u.toString());
		assertEquals("D:\\m y", u.toASCIIString());
		assertEquals(u, new URIish(str));
	}

	@Test
	public void testRelativePath() throws Exception {
		final String str = "../../foo/bar";
		URIish u = new URIish(str);
		assertNull(u.getScheme());
		assertFalse(u.isRemote());
		assertEquals(str, u.getRawPath());
		assertEquals(str, u.getPath());
		assertEquals(str, u.toString());
		assertEquals(str, u.toASCIIString());
		assertEquals(u, new URIish(str));
	}

	@Test
	public void testUNC() throws Exception {
		final String str = "\\\\some\\place";
		URIish u = new URIish(str);
		assertNull(u.getScheme());
		assertFalse(u.isRemote());
		assertEquals("\\\\some\\place", u.getRawPath());
		assertEquals("\\\\some\\place", u.getPath());
		assertEquals("\\\\some\\place", u.toString());
		assertEquals("\\\\some\\place", u.toASCIIString());
		assertEquals(u, new URIish(str));
	}

	@Test
	public void testFileProtoUnix() throws Exception {
		final String str = "file:///home/m y";
		URIish u = new URIish(str);
		assertEquals("file", u.getScheme());
		assertFalse(u.isRemote());
		assertEquals("/home/m y", u.getRawPath());
		assertEquals("/home/m y", u.getPath());
		assertEquals("file:///home/m y", u.toString());
		assertEquals("file:///home/m%20y", u.toASCIIString());
		assertEquals(u, new URIish(str));
	}

	@Test
	public void testURIEncode_00() throws Exception {
		final String str = "file:///home/m%00y";
		URIish u = new URIish(str);
		assertEquals("file", u.getScheme());
		assertFalse(u.isRemote());
		assertEquals("/home/m%00y", u.getRawPath());
		assertEquals("/home/m\u0000y", u.getPath());
		assertEquals("file:///home/m%00y", u.toString());
		assertEquals("file:///home/m%00y", u.toASCIIString());
		assertEquals(u, new URIish(str));
	}

	@Test
	public void testURIEncode_0a() throws Exception {
		final String str = "file:///home/m%0ay";
		URIish u = new URIish(str);
		assertEquals("file", u.getScheme());
		assertFalse(u.isRemote());
		assertEquals("/home/m%0ay", u.getRawPath());
		assertEquals("/home/m\ny", u.getPath());
		assertEquals("file:///home/m%0ay", u.toString());
		assertEquals("file:///home/m%0ay", u.toASCIIString());
		assertEquals(u, new URIish(str));
	}

	@Test
	public void testURIEncode_unicode() throws Exception {
		final String str = "file:///home/m%c3%a5y";
		URIish u = new URIish(str);
		assertEquals("file", u.getScheme());
		assertFalse(u.isRemote());
		assertEquals("/home/m%c3%a5y", u.getRawPath());
		assertEquals("/home/m\u00e5y", u.getPath());
		assertEquals("file:///home/m%c3%a5y", u.toString());
		assertEquals("file:///home/m%c3%a5y", u.toASCIIString());
		assertEquals(u, new URIish(str));
	}

	@Test
	public void testFileProtoWindows() throws Exception {
		final String str = "file:///D:/m y";
		URIish u = new URIish(str);
		assertEquals("file", u.getScheme());
		assertFalse(u.isRemote());
		assertEquals(null, u.getHost());
		assertEquals(-1, u.getPort());
		assertEquals(null, u.getUser());
		assertEquals(null, u.getPass());
		assertEquals("D:/m y", u.getRawPath());
		assertEquals("D:/m y", u.getPath());
		assertEquals("file:///D:/m y", u.toString());
		assertEquals("file:///D:/m%20y", u.toASCIIString());
		assertEquals(u, new URIish(str));
	}

	@Test
	public void testFileProtoWindowsWithHost() throws Exception {
		final String str = "file://localhost/D:/m y";
		URIish u = new URIish(str);
		assertEquals("file", u.getScheme());
		assertTrue(u.isRemote());
		assertEquals("localhost", u.getHost());
		assertEquals(-1, u.getPort());
		assertEquals(null, u.getUser());
		assertEquals(null, u.getPass());
		assertEquals("D:/m y", u.getRawPath());
		assertEquals("D:/m y", u.getPath());
		assertEquals("file://localhost/D:/m y", u.toString());
		assertEquals("file://localhost/D:/m%20y", u.toASCIIString());
		assertEquals(u, new URIish(str));
	}

	@Test
	public void testFileProtoWindowsWithHostAndPort() throws Exception {
		final String str = "file://localhost:80/D:/m y";
		URIish u = new URIish(str);
		assertEquals("file", u.getScheme());
		assertTrue(u.isRemote());
		assertEquals("localhost", u.getHost());
		assertEquals(80, u.getPort());
		assertEquals(null, u.getUser());
		assertEquals(null, u.getPass());
		assertEquals("D:/m y", u.getRawPath());
		assertEquals("D:/m y", u.getPath());
		assertEquals("file://localhost:80/D:/m y", u.toString());
		assertEquals("file://localhost:80/D:/m%20y", u.toASCIIString());
		assertEquals(u, new URIish(str));
	}

	@Test
	public void testFileProtoWindowsWithHostAndEmptyPortIsAmbiguous()
			throws Exception {
		final String str = "file://localhost:/D:/m y";
		URIish u = new URIish(str);
		assertEquals("file", u.getScheme());
		assertFalse(u.isRemote());
		assertEquals(null, u.getHost());
		assertEquals(-1, u.getPort());
		assertEquals(null, u.getUser());
		assertEquals(null, u.getPass());
		assertEquals("localhost:/D:/m y", u.getRawPath());
		assertEquals("localhost:/D:/m y", u.getPath());
		assertEquals("file:///localhost:/D:/m y", u.toString());
		assertEquals("file:///localhost:/D:/m%20y", u.toASCIIString());
		assertEquals(u, new URIish(str));
	}

	@Test
	public void testFileProtoWindowsMissingHostSlash() throws Exception {
		final String str = "file://D:/m y";
		URIish u = new URIish(str);
		assertEquals("file", u.getScheme());
		assertFalse(u.isRemote());
		assertEquals("D:/m y", u.getRawPath());
		assertEquals("D:/m y", u.getPath());
		assertEquals("file:///D:/m y", u.toString());
		assertEquals("file:///D:/m%20y", u.toASCIIString());
		assertEquals(u, new URIish(str));
	}

	@Test
	public void testFileProtoWindowsMissingHostSlash2() throws Exception {
		final String str = "file://D: /m y";
		URIish u = new URIish(str);
		assertEquals("file", u.getScheme());
		assertFalse(u.isRemote());
		assertEquals("D: /m y", u.getRawPath());
		assertEquals("D: /m y", u.getPath());
		assertEquals("file:///D: /m y", u.toString());
		assertEquals("file:///D:%20/m%20y", u.toASCIIString());
		assertEquals(u, new URIish(str));
	}

	@Test
	public void testGitProtoUnix() throws Exception {
		final String str = "git://example.com/home/m y";
		URIish u = new URIish(str);
		assertEquals("git", u.getScheme());
		assertTrue(u.isRemote());
		assertEquals("example.com", u.getHost());
		assertEquals("/home/m y", u.getRawPath());
		assertEquals("/home/m y", u.getPath());
		assertEquals("git://example.com/home/m y", u.toString());
		assertEquals("git://example.com/home/m%20y", u.toASCIIString());
		assertEquals(u, new URIish(str));
	}

	@Test
	public void testGitProtoUnixPort() throws Exception {
		final String str = "git://example.com:333/home/m y";
		URIish u = new URIish(str);
		assertEquals("git", u.getScheme());
		assertTrue(u.isRemote());
		assertEquals("example.com", u.getHost());
		assertEquals("/home/m y", u.getRawPath());
		assertEquals("/home/m y", u.getPath());
		assertEquals(333, u.getPort());
		assertEquals("git://example.com:333/home/m y", u.toString());
		assertEquals("git://example.com:333/home/m%20y", u.toASCIIString());
		assertEquals(u, new URIish(str));
	}

	@Test
	public void testGitProtoWindowsPort() throws Exception {
		final String str = "git://example.com:338/D:/m y";
		URIish u = new URIish(str);
		assertEquals("git", u.getScheme());
		assertTrue(u.isRemote());
		assertEquals("D:/m y", u.getRawPath());
		assertEquals("D:/m y", u.getPath());
		assertEquals(338, u.getPort());
		assertEquals("example.com", u.getHost());
		assertEquals("git://example.com:338/D:/m y", u.toString());
		assertEquals("git://example.com:338/D:/m%20y", u.toASCIIString());
		assertEquals(u, new URIish(str));
	}

	@Test
	public void testGitProtoWindows() throws Exception {
		final String str = "git://example.com/D:/m y";
		URIish u = new URIish(str);
		assertEquals("git", u.getScheme());
		assertTrue(u.isRemote());
		assertEquals("D:/m y", u.getRawPath());
		assertEquals("D:/m y", u.getPath());
		assertEquals("example.com", u.getHost());
		assertEquals(-1, u.getPort());
		assertEquals("git://example.com/D:/m y", u.toString());
		assertEquals("git://example.com/D:/m%20y", u.toASCIIString());
		assertEquals(u, new URIish(str));
	}

	@Test
	public void testScpStyleNoURIDecoding() throws Exception {
		final String str = "example.com:some/p%20ath";
		URIish u = new URIish(str);
		assertNull(u.getScheme());
		assertTrue(u.isRemote());
		assertEquals("some/p%20ath", u.getRawPath());
		assertEquals("some/p%20ath", u.getPath());
		assertEquals("example.com", u.getHost());
		assertEquals(-1, u.getPort());
		assertEquals(str, u.toString());
		assertEquals(str, u.toASCIIString());
		assertEquals(u, new URIish(str));
	}

	@Test
	public void testScpStyleWithoutUserRelativePath() throws Exception {
		final String str = "example.com:some/p ath";
		URIish u = new URIish(str);
		assertNull(u.getScheme());
		assertTrue(u.isRemote());
		assertEquals("some/p ath", u.getRawPath());
		assertEquals("some/p ath", u.getPath());
		assertEquals("example.com", u.getHost());
		assertEquals(-1, u.getPort());
		assertEquals(str, u.toString());
		assertEquals(str, u.toASCIIString());
		assertEquals(u, new URIish(str));
	}

	@Test
	public void testScpStyleWithoutUserAbsolutePath() throws Exception {
		final String str = "example.com:/some/p ath";
		URIish u = new URIish(str);
		assertNull(u.getScheme());
		assertTrue(u.isRemote());
		assertEquals("/some/p ath", u.getRawPath());
		assertEquals("/some/p ath", u.getPath());
		assertEquals("example.com", u.getHost());
		assertEquals(-1, u.getPort());
		assertEquals(str, u.toString());
		assertEquals(str, u.toASCIIString());
		assertEquals(u, new URIish(str));
	}

	@Test
	public void testScpStyleWithUser() throws Exception {
		final String str = "user@example.com:some/p ath";
		URIish u = new URIish(str);
		assertNull(u.getScheme());
		assertTrue(u.isRemote());
		assertEquals("some/p ath", u.getRawPath());
		assertEquals("some/p ath", u.getPath());
		assertEquals("user", u.getUser());
		assertEquals("example.com", u.getHost());
		assertEquals(-1, u.getPort());
		assertEquals(str, u.toString());
		assertEquals(str, u.toASCIIString());
		assertEquals(u, new URIish(str));
	}

	@Test
	public void testGitSshProto() throws Exception {
		final String str = "git+ssh://example.com/some/p ath";
		URIish u = new URIish(str);
		assertEquals("git+ssh", u.getScheme());
		assertTrue(u.isRemote());
		assertEquals("/some/p ath", u.getRawPath());
		assertEquals("/some/p ath", u.getPath());
		assertEquals("example.com", u.getHost());
		assertEquals(-1, u.getPort());
		assertEquals("git+ssh://example.com/some/p ath", u.toString());
		assertEquals("git+ssh://example.com/some/p%20ath", u.toASCIIString());
		assertEquals(u, new URIish(str));
	}

	@Test
	public void testSshGitProto() throws Exception {
		final String str = "ssh+git://example.com/some/p ath";
		URIish u = new URIish(str);
		assertEquals("ssh+git", u.getScheme());
		assertTrue(u.isRemote());
		assertEquals("/some/p ath", u.getRawPath());
		assertEquals("/some/p ath", u.getPath());
		assertEquals("example.com", u.getHost());
		assertEquals(-1, u.getPort());
		assertEquals("ssh+git://example.com/some/p ath", u.toString());
		assertEquals("ssh+git://example.com/some/p%20ath", u.toASCIIString());
		assertEquals(u, new URIish(str));
	}

	@Test
	public void testSshProto() throws Exception {
		final String str = "ssh://example.com/some/p ath";
		URIish u = new URIish(str);
		assertEquals("ssh", u.getScheme());
		assertTrue(u.isRemote());
		assertEquals("/some/p ath", u.getRawPath());
		assertEquals("/some/p ath", u.getPath());
		assertEquals("example.com", u.getHost());
		assertEquals(-1, u.getPort());
		assertEquals("ssh://example.com/some/p ath", u.toString());
		assertEquals("ssh://example.com/some/p%20ath", u.toASCIIString());
		assertEquals(u, new URIish(str));
	}

	@Test
	public void testSshProtoHostOnly() throws Exception {
		final String str = "ssh://example.com/";
		URIish u = new URIish(str);
		assertEquals("ssh", u.getScheme());
		assertTrue(u.isRemote());
		assertEquals("/", u.getRawPath());
		assertEquals("/", u.getPath());
		assertEquals("example.com", u.getHost());
		assertEquals(-1, u.getPort());
		assertEquals("ssh://example.com/", u.toString());
		assertEquals("ssh://example.com/", u.toASCIIString());
		assertEquals("example.com", u.getHumanishName());
		assertEquals(u, new URIish(str));
	}

	@Test
	public void testSshProtoHostWithAuthentication() throws Exception {
		final String str = "ssh://user:secret@pass@example.com/";
		URIish u = new URIish(str);
		assertEquals("ssh", u.getScheme());
		assertTrue(u.isRemote());
		assertEquals("/", u.getRawPath());
		assertEquals("/", u.getPath());
		assertEquals("example.com", u.getHost());
		assertEquals(-1, u.getPort());
		assertEquals("ssh://user@example.com/", u.toString());
		assertEquals("ssh://user@example.com/", u.toASCIIString());
		assertEquals("example.com", u.getHumanishName());
		assertEquals("user", u.getUser());
		assertEquals("secret@pass", u.getPass());
		assertEquals(u, new URIish(str));
	}

	@Test
	public void testSshProtoHostWithPort() throws Exception {
		final String str = "ssh://example.com:2222/";
		URIish u = new URIish(str);
		assertEquals("ssh", u.getScheme());
		assertTrue(u.isRemote());
		assertEquals("/", u.getRawPath());
		assertEquals("/", u.getPath());
		assertEquals("example.com", u.getHost());
		assertEquals(2222, u.getPort());
		assertEquals("ssh://example.com:2222/", u.toString());
		assertEquals("ssh://example.com:2222/", u.toASCIIString());
		assertEquals("example.com", u.getHumanishName());
		assertEquals(u, new URIish(str));
	}

	@Test
	public void testSshProtoHostWithEmptyPortAndPath() throws Exception {
		final String str = "ssh://example.com:/path";
		URIish u = new URIish(str);
		assertEquals("ssh", u.getScheme());
		assertTrue(u.isRemote());
		assertEquals("/path", u.getRawPath());
		assertEquals("/path", u.getPath());
		assertEquals("example.com", u.getHost());
		assertEquals(-1, u.getPort());
		assertEquals("ssh://example.com/path", u.toString());
		assertEquals("ssh://example.com/path", u.toASCIIString());
		assertEquals(u, new URIish(str));
		assertEquals(u, new URIish("ssh://example.com/path"));
	}

	@Test
	public void testSshProtoWithUserAndPort() throws Exception {
		final String str = "ssh://user@example.com:33/some/p ath";
		URIish u = new URIish(str);
		assertEquals("ssh", u.getScheme());
		assertTrue(u.isRemote());
		assertEquals("/some/p ath", u.getRawPath());
		assertEquals("/some/p ath", u.getPath());
		assertEquals("example.com", u.getHost());
		assertEquals("user", u.getUser());
		assertNull(u.getPass());
		assertEquals(33, u.getPort());
		assertEquals("ssh://user@example.com:33/some/p ath", u.toString());
		assertEquals("ssh://user@example.com:33/some/p%20ath",
				u.toASCIIString());
		assertEquals(u, new URIish(str));
	}

	@Test
	public void testSshProtoWithUserPassAndPort() throws Exception {
		final String str = "ssh://user:pass@example.com:33/some/p ath";
		URIish u = new URIish(str);
		assertEquals("ssh", u.getScheme());
		assertTrue(u.isRemote());
		assertEquals("/some/p ath", u.getRawPath());
		assertEquals("/some/p ath", u.getPath());
		assertEquals("example.com", u.getHost());
		assertEquals("user", u.getUser());
		assertEquals("pass", u.getPass());
		assertEquals(33, u.getPort());
		assertEquals("ssh://user:pass@example.com:33/some/p ath",
				u.toPrivateString());
		assertEquals("ssh://user:pass@example.com:33/some/p%20ath",
				u.toPrivateASCIIString());
		assertEquals(u.setPass(null).toPrivateString(), u.toString());
		assertEquals(u.setPass(null).toPrivateASCIIString(), u.toASCIIString());
		assertEquals(u, new URIish(str));
	}

	@Test
	public void testSshProtoWithEmailUserAndPort() throws Exception {
		final String str = "ssh://user.name@email.com@example.com:33/some/p ath";
		URIish u = new URIish(str);
		assertEquals("ssh", u.getScheme());
		assertTrue(u.isRemote());
		assertEquals("/some/p ath", u.getRawPath());
		assertEquals("/some/p ath", u.getPath());
		assertEquals("example.com", u.getHost());
		assertEquals("user.name@email.com", u.getUser());
		assertNull(u.getPass());
		assertEquals(33, u.getPort());
		assertEquals("ssh://user.name%40email.com@example.com:33/some/p ath",
				u.toPrivateString());
		assertEquals("ssh://user.name%40email.com@example.com:33/some/p%20ath",
				u.toPrivateASCIIString());
		assertEquals(u.setPass(null).toPrivateString(), u.toString());
		assertEquals(u.setPass(null).toPrivateASCIIString(), u.toASCIIString());
		assertEquals(u, new URIish(str));
	}

	@Test
	public void testSshProtoWithEmailUserPassAndPort() throws Exception {
		final String str = "ssh://user.name@email.com:pass@wor:d@example.com:33/some/p ath";
		URIish u = new URIish(str);
		assertEquals("ssh", u.getScheme());
		assertTrue(u.isRemote());
		assertEquals("/some/p ath", u.getRawPath());
		assertEquals("/some/p ath", u.getPath());
		assertEquals("example.com", u.getHost());
		assertEquals("user.name@email.com", u.getUser());
		assertEquals("pass@wor:d", u.getPass());
		assertEquals(33, u.getPort());
		assertEquals("ssh://user.name%40email.com:pass%40wor%3ad@example.com:33/some/p ath",
				u.toPrivateString());
		assertEquals("ssh://user.name%40email.com:pass%40wor%3ad@example.com:33/some/p%20ath",
				u.toPrivateASCIIString());
		assertEquals(u.setPass(null).toPrivateString(), u.toString());
		assertEquals(u.setPass(null).toPrivateASCIIString(), u.toASCIIString());
		assertEquals(u, new URIish(str));
	}

	@Test
	public void testSshProtoWithADUserPassAndPort() throws Exception {
		final String str = "ssh://DOMAIN\\user:pass@example.com:33/some/p ath";
		URIish u = new URIish(str);
		assertEquals("ssh", u.getScheme());
		assertTrue(u.isRemote());
		assertEquals("/some/p ath", u.getRawPath());
		assertEquals("/some/p ath", u.getPath());
		assertEquals("example.com", u.getHost());
		assertEquals("DOMAIN\\user", u.getUser());
		assertEquals("pass", u.getPass());
		assertEquals(33, u.getPort());
		assertEquals("ssh://DOMAIN%5cuser:pass@example.com:33/some/p ath",
				u.toPrivateString());
		assertEquals("ssh://DOMAIN%5cuser:pass@example.com:33/some/p%20ath",
				u.toPrivateASCIIString());
		assertEquals(u.setPass(null).toPrivateString(), u.toString());
		assertEquals(u.setPass(null).toPrivateASCIIString(), u.toASCIIString());
		assertEquals(u, new URIish(str));
	}

	@Test
	public void testSshProtoWithEscapedADUserPassAndPort() throws Exception {
		final String str = "ssh://DOMAIN%5c\u00fcser:pass@example.com:33/some/p ath";
		URIish u = new URIish(str);
		assertEquals("ssh", u.getScheme());
		assertTrue(u.isRemote());
		assertEquals("/some/p ath", u.getRawPath());
		assertEquals("/some/p ath", u.getPath());
		assertEquals("example.com", u.getHost());
		assertEquals("DOMAIN\\\u00fcser", u.getUser());
		assertEquals("pass", u.getPass());
		assertEquals(33, u.getPort());
		assertEquals("ssh://DOMAIN%5c%c3%bcser:pass@example.com:33/some/p ath",
				u.toPrivateString());
		assertEquals(
				"ssh://DOMAIN%5c%c3%bcser:pass@example.com:33/some/p%20ath",
				u.toPrivateASCIIString());
		assertEquals(u.setPass(null).toPrivateString(), u.toString());
		assertEquals(u.setPass(null).toPrivateASCIIString(), u.toASCIIString());
		assertEquals(u, new URIish(str));
	}

	@Test
	public void testURIEncodeDecode() throws Exception {
		final String str = "ssh://%3ax%25:%40%41x@example.com:33/some%c3%a5/p%20a th";
		URIish u = new URIish(str);
		assertEquals("ssh", u.getScheme());
		assertTrue(u.isRemote());
		assertEquals("/some%c3%a5/p%20a th", u.getRawPath());
		assertEquals("/some\u00e5/p a th", u.getPath());
		assertEquals("example.com", u.getHost());
		assertEquals(":x%", u.getUser());
		assertEquals("@Ax", u.getPass());
		assertEquals(33, u.getPort());
		assertEquals("ssh://%3ax%25:%40Ax@example.com:33/some%c3%a5/p%20a th",
				u.toPrivateString());
		assertEquals(
				"ssh://%3ax%25:%40Ax@example.com:33/some%c3%a5/p%20a%20th",
				u.toPrivateASCIIString());
		assertEquals(u.setPass(null).toPrivateString(), u.toString());
		assertEquals(u.setPass(null).toPrivateASCIIString(), u.toASCIIString());
		assertEquals(u, new URIish(str));
	}

	@Test
	public void testGitWithUserHome() throws Exception {
		final String str = "git://example.com/~some/p ath";
		URIish u = new URIish(str);
		assertEquals("git", u.getScheme());
		assertTrue(u.isRemote());
		assertEquals("~some/p ath", u.getRawPath());
		assertEquals("~some/p ath", u.getPath());
		assertEquals("example.com", u.getHost());
		assertNull(u.getUser());
		assertNull(u.getPass());
		assertEquals(-1, u.getPort());
		assertEquals("git://example.com/~some/p ath", u.toPrivateString());
		assertEquals("git://example.com/~some/p%20ath",
				u.toPrivateASCIIString());
		assertEquals(u.setPass(null).toPrivateString(), u.toString());
		assertEquals(u.setPass(null).toPrivateASCIIString(), u.toASCIIString());
		assertEquals(u, new URIish(str));
	}

	@Test
	@Ignore("Resolving ~user is beyond standard Java API and need more support")
	public void testFileWithUserHome() throws Exception {
		final String str = "~some/p ath";
		URIish u = new URIish(str);
		assertEquals("git", u.getScheme());
		assertTrue(u.isRemote());
		assertEquals("~some/p ath", u.getRawPath());
		assertEquals("~some/p ath", u.getPath());
		assertEquals("example.com", u.getHost());
		assertNull(u.getUser());
		assertNull(u.getPass());
		assertEquals(-1, u.getPort());
		assertEquals(str, u.toPrivateString());
		assertEquals(str, u.toPrivateASCIIString());
		assertEquals(u.setPass(null).toPrivateString(), u.toString());
		assertEquals(u.setPass(null).toPrivateASCIIString(), u.toASCIIString());
		assertEquals(u, new URIish(str));
	}

	@Test
	public void testFileWithNoneUserHomeWithTilde() throws Exception {
		final String str = "/~some/p ath";
		URIish u = new URIish(str);
		assertNull(u.getScheme());
		assertFalse(u.isRemote());
		assertEquals("/~some/p ath", u.getRawPath());
		assertEquals("/~some/p ath", u.getPath());
		assertNull(u.getHost());
		assertNull(u.getUser());
		assertNull(u.getPass());
		assertEquals(-1, u.getPort());
		assertEquals(str, u.toPrivateString());
		assertEquals(str, u.toPrivateASCIIString());
		assertEquals(u.setPass(null).toPrivateString(), u.toString());
		assertEquals(u.setPass(null).toPrivateASCIIString(), u.toASCIIString());
		assertEquals(u, new URIish(str));
	}

	@Test(expected = IllegalArgumentException.class)
	public void testGetNullHumanishName() {
		new URIish().getHumanishName();
	}

	@Test(expected = IllegalArgumentException.class)
	public void testGetEmptyHumanishName() throws URISyntaxException {
		new URIish(GIT_SCHEME).getHumanishName();
	}

	@Test(expected = IllegalArgumentException.class)
	public void testGetAbsEmptyHumanishName() {
		new URIish().getHumanishName();
	}

	@Test
	public void testGetSet() throws Exception {
		final String str = "ssh://DOMAIN\\user:pass@example.com:33/some/p ath%20";
		URIish u = new URIish(str);
		u = u.setHost(u.getHost());
		u = u.setPass(u.getPass());
		u = u.setPort(u.getPort());
		assertEquals("ssh", u.getScheme());
		assertTrue(u.isRemote());
		u = u.setRawPath(u.getRawPath());
		assertEquals("/some/p ath%20", u.getRawPath());
		u = u.setPath(u.getPath());
		assertEquals("/some/p ath ", u.getRawPath());
		assertEquals("/some/p ath ", u.getPath());
		assertEquals("example.com", u.getHost());
		assertEquals("DOMAIN\\user", u.getUser());
		assertEquals("pass", u.getPass());
		assertEquals(33, u.getPort());
		assertEquals("ssh://DOMAIN%5cuser:pass@example.com:33/some/p ath ",
				u.toPrivateString());
		assertEquals("ssh://DOMAIN%5cuser:pass@example.com:33/some/p%20ath%20",
				u.toPrivateASCIIString());
		assertEquals(u.setPass(null).toPrivateString(), u.toString());
		assertEquals(u.setPass(null).toPrivateASCIIString(), u.toASCIIString());
		assertEquals(u, new URIish(str));
	}

	@Test
	public void testGetValidWithEmptySlashDotGitHumanishName()
			throws IllegalArgumentException, URISyntaxException {
		String humanishName = new URIish("/a/b/.git").getHumanishName();
		assertEquals("b", humanishName);
	}

	@Test
	public void testGetWithSlashDotGitHumanishName() throws URISyntaxException {
		assertEquals("", new URIish("/.git").getHumanishName());
	}

	@Test
	public void testGetTwoSlashesDotGitHumanishName() throws URISyntaxException {
		assertEquals("", new URIish("//.git").getHumanishName());
	}

	@Test
	public void testGetValidHumanishName() throws IllegalArgumentException,
			URISyntaxException {
		String humanishName = new URIish(GIT_SCHEME + "abc").getHumanishName();
		assertEquals("abc", humanishName);
	}

	@Test
	public void testGetEmptyHumanishNameWithAuthorityOnly() throws IllegalArgumentException,
			URISyntaxException {
		String humanishName = new URIish(GIT_SCHEME + "abc").getHumanishName();
		assertEquals("abc", humanishName);
	}

	@Test
	public void testGetValidSlashHumanishName()
			throws IllegalArgumentException, URISyntaxException {
		String humanishName = new URIish(GIT_SCHEME + "host/abc/")
				.getHumanishName();
		assertEquals("abc", humanishName);
	}

	@Test
	public void testGetSlashValidSlashHumanishName()
			throws IllegalArgumentException, URISyntaxException {
		String humanishName = new URIish("/abc/").getHumanishName();
		assertEquals("abc", humanishName);
	}

	@Test
	public void testGetSlashValidSlashDotGitSlashHumanishName()
			throws IllegalArgumentException, URISyntaxException {
		String humanishName = new URIish("/abc/.git").getHumanishName();
		assertEquals("abc", humanishName);
	}

	@Test
	public void testGetSlashSlashDotGitSlashHumanishName()
			throws IllegalArgumentException, URISyntaxException {
		final String humanishName = new URIish(GIT_SCHEME + "/.git")
				.getHumanishName();
		assertEquals("may return an empty humanish name", "", humanishName);
	}

	@Test
	public void testGetSlashesValidSlashHumanishName()
			throws IllegalArgumentException, URISyntaxException {
		String humanishName = new URIish("/a/b/c/").getHumanishName();
		assertEquals("c", humanishName);
	}

	@Test
	public void testGetValidDotGitHumanishName()
			throws IllegalArgumentException, URISyntaxException {
		String humanishName = new URIish(GIT_SCHEME + "abc.git")
				.getHumanishName();
		assertEquals("abc", humanishName);
	}

	@Test
	public void testGetValidDotGitSlashHumanishName()
			throws IllegalArgumentException, URISyntaxException {
		String humanishName = new URIish(GIT_SCHEME + "host.xy/abc.git/")
				.getHumanishName();
		assertEquals("abc", humanishName);
	}

	@Test
	public void testGetValidWithSlashDotGitHumanishName()
			throws IllegalArgumentException, URISyntaxException {
		String humanishName = new URIish("/abc.git").getHumanishName();
		assertEquals("abc", humanishName);
	}

	@Test
	public void testGetValidWithSlashDotGitSlashHumanishName()
			throws IllegalArgumentException, URISyntaxException {
		String humanishName = new URIish("/abc.git/").getHumanishName();
		assertEquals("abc", humanishName);
	}

	@Test
	public void testGetValidWithSlashesDotGitHumanishName()
			throws IllegalArgumentException, URISyntaxException {
		String humanishName = new URIish("/a/b/c.git").getHumanishName();
		assertEquals("c", humanishName);
	}

	@Test
	public void testGetValidWithSlashesDotGitSlashHumanishName()
			throws IllegalArgumentException, URISyntaxException {
		String humanishName = new URIish("/a/b/c.git/").getHumanishName();
		assertEquals("c", humanishName);
	}

	@Test
	public void testGetValidLocalWithTwoSlashesHumanishName()
			throws IllegalArgumentException, URISyntaxException {
		String humanishName = new URIish("/a/b/c//").getHumanishName();
		assertEquals("c", humanishName);
	}

	@Test
	public void testGetValidGitSchemeWithTwoSlashesHumanishName()
			throws IllegalArgumentException, URISyntaxException {
		String humanishName = new URIish(GIT_SCHEME + "/a/b/c//")
				.getHumanishName();
		assertEquals("c", humanishName);
	}

	@Test
	public void testGetWindowsPathHumanishName()
			throws IllegalArgumentException,
			URISyntaxException {
		if (File.separatorChar == '\\') {
			String humanishName = new URIish("file:///C\\a\\b\\c.git/")
					.getHumanishName();
			assertEquals("c", humanishName);
		}
	}

	@Test
	public void testUserPasswordAndPort() throws URISyntaxException {
		String str = "http://user:secret@host.xy:80/some/path";
		URIish u = new URIish(str);
		assertEquals("http", u.getScheme());
		assertTrue(u.isRemote());
		assertEquals("/some/path", u.getRawPath());
		assertEquals("/some/path", u.getPath());
		assertEquals("host.xy", u.getHost());
		assertEquals(80, u.getPort());
		assertEquals("user", u.getUser());
		assertEquals("secret", u.getPass());
		assertEquals(u, new URIish(str));

		str = "http://user:secret@pass@host.xy:80/some/path";
		u = new URIish(str);
		assertEquals("http", u.getScheme());
		assertTrue(u.isRemote());
		assertEquals("/some/path", u.getPath());
		assertEquals("host.xy", u.getHost());
		assertEquals(80, u.getPort());
		assertEquals("user", u.getUser());
		assertEquals("secret@pass", u.getPass());
		assertEquals(u, new URIish(str));
	}

	/**
	 * Exemplify what happens with the special case of encoding '/' as %2F. Web
	 * services in general parse path components before decoding the characters.
	 *
	 * @throws URISyntaxException
	 */
	@Test
	public void testPathSeparator() throws URISyntaxException {
		String str = "http://user:secret@host.xy:80/some%2Fpath";
		URIish u = new URIish(str);
		assertEquals("http", u.getScheme());
		assertTrue(u.isRemote());
		assertEquals("/some%2Fpath", u.getRawPath());
		assertEquals("/some/path", u.getPath());
		assertEquals("host.xy", u.getHost());
		assertEquals(80, u.getPort());
		assertEquals("user", u.getUser());
		assertEquals("secret", u.getPass());
		assertEquals(u, new URIish(str));
	}

	@Test
	public void testFileProtocol() throws IllegalArgumentException,
			URISyntaxException, IOException {
		// as defined by git docu
		URIish u = new URIish("file:///a/b.txt");
		assertEquals("file", u.getScheme());
		assertFalse(u.isRemote());
		assertNull(u.getHost());
		assertNull(u.getPass());
		assertEquals("/a/b.txt", u.getRawPath());
		assertEquals("/a/b.txt", u.getPath());
		assertEquals(-1, u.getPort());
		assertNull(u.getUser());
		assertEquals("b.txt", u.getHumanishName());

		File tmp = File.createTempFile("jgitUnitTest", ".tmp");
		u = new URIish(tmp.toURI().toString());
		assertEquals("file", u.getScheme());
		assertFalse(u.isRemote());
		assertNull(u.getHost());
		assertNull(u.getPass());
		assertTrue(u.getPath().contains("jgitUnitTest"));
		assertEquals(-1, u.getPort());
		assertNull(u.getUser());
		assertTrue(u.getHumanishName().startsWith("jgitUnitTest"));

		u = new URIish("file:/a/b.txt");
		assertEquals("file", u.getScheme());
		assertFalse(u.isRemote());
		assertNull(u.getHost());
		assertNull(u.getPass());
		assertEquals("/a/b.txt", u.getRawPath());
		assertEquals("/a/b.txt", u.getPath());
		assertEquals(-1, u.getPort());
		assertNull(u.getUser());
		assertEquals("b.txt", u.getHumanishName());

		u = new URIish("file:/a/test.bundle");
		assertEquals("file", u.getScheme());
		assertFalse(u.isRemote());
		assertNull(u.getHost());
		assertNull(u.getPass());
		assertEquals("/a/test.bundle", u.getRawPath());
		assertEquals("/a/test.bundle", u.getPath());
		assertEquals(-1, u.getPort());
		assertNull(u.getUser());
		assertEquals("test", u.getHumanishName());
	}

	@Test
	public void testALot() throws URISyntaxException {
		// user pass host port path
		// 1 2 3 4 5
		String[][] tests = {
				new String[] { "%1$s://%2$s:%3$s@%4$s:%5$s/%6$s", "%1$s",
						"%2$s", "%3$s", "%4$s", "%5$s", "%6$s" },
				new String[] { "%1$s://%2$s@%4$s:%5$s/%6$s", "%1$s", "%2$s",
						null, "%4$s", "%5$s", "%6$s" },
				new String[] { "%1$s://%2$s@%4$s/%6$s", "%1$s", "%2$s", null,
						"%4$s", null, "%6$s" },
				new String[] { "%1$s://%4$s/%6$s", "%1$s", null, null, "%4$s",
						null, "%6$s" }, };
		String[] schemes = new String[] { "ssh", "ssh+git", "http", "https" };
		String[] users = new String[] { "me", "l usr\\example.com",
				"lusr\\example" };
		String[] passes = new String[] { "wtf", };
		String[] hosts = new String[] { "example.com", "1.2.3.4", "[::1]" };
		String[] ports = new String[] { "1234", "80" };
		String[] paths = new String[] { "/", "/abc", "D:/x", "D:\\x" };
		for (String[] test : tests) {
			String fmt = test[0];
			for (String scheme : schemes) {
				for (String user : users) {
					for (String pass : passes) {
						for (String host : hosts) {
							for (String port : ports) {
								for (String path : paths) {
									String url = String.format(fmt, scheme,
											user, pass, host, port, path);
									String[] expect = new String[test.length];
									for (int i = 1; i < expect.length; ++i)
										if (test[i] != null)
											expect[i] = String.format(test[i],
													scheme, user, pass, host,
													port, path);
									URIish urIish = new URIish(url);
									assertEquals(url, expect[1],
											urIish.getScheme());
									assertEquals(url, expect[2],
											urIish.getUser());
								}
							}
						}
					}
				}
			}
		}
	}

	@Test
	public void testStringConstructor() throws Exception {
		String str = "http://example.com/";
		URIish u = new URIish(str);
		assertEquals("example.com", u.getHost());
		assertEquals("/", u.getPath());
		assertEquals(str, u.toString());

		str = "http://example.com";
		u = new URIish(str);
		assertEquals("example.com", u.getHost());
		assertEquals("", u.getPath());
		assertEquals(str, u.toString());
	}

	@Test
	public void testEqualsHashcode() throws Exception
	{
		String[] urls = { "http://user:pass@example.com:8081/path", "../x",
				"ssh://x.y:23/z", "ssh://example.com:/path", "D:\\m y",
				"\\\\some\\place", "http://localhost:1234",
				"user@example.com:some/p ath", "a",
				"http://user:pwd@example.com:8081/path",
				"http://user:pass@another.com:8081/path",
				"http://user:pass@example.com:8083/path" };
		URIish w = new URIish("http://user:pass@example.com:8081/path/x");
		for (String s : urls) {
			URIish u = new URIish(s);
			URIish v = new URIish(s);
			assertTrue(u.equals(v));
			assertTrue(v.equals(u));

			assertFalse(u.equals(null));
			assertFalse(u.equals(new Object()));
			assertFalse(new Object().equals(u));
			assertFalse(u.equals(w));
			assertFalse(w.equals(u));

			assertTrue(u.hashCode() == v.hashCode());
			assertFalse(u.hashCode() == new Object().hashCode());
		}
	}

	@Test
	public void testReservedCharacterInUsername() throws URISyntaxException, MalformedURLException {
		URIish u = new URIish("https://repo.sample/test.git");
		u = u.setUser("ex\\test");
		URI.create(u.toString()).toURL();
	}
}
