/*
 * Copyright (C) 2009, Mykola Nikishov <mn@mn.com.ua>
 * Copyright (C) 2008, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
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

package org.eclipse.jgit.transport;

import java.net.URISyntaxException;

import junit.framework.TestCase;

public class URIishTest extends TestCase {

	private static final String GIT_SCHEME = "git://";

	public void testUnixFile() throws Exception {
		final String str = "/home/m y";
		URIish u = new URIish(str);
		assertNull(u.getScheme());
		assertFalse(u.isRemote());
		assertEquals(str, u.getPath());
		assertEquals(str, u.toString());
		assertEquals(u, new URIish(str));
	}

	public void testWindowsFile() throws Exception {
		final String str = "D:/m y";
		URIish u = new URIish(str);
		assertNull(u.getScheme());
		assertFalse(u.isRemote());
		assertEquals(str, u.getPath());
		assertEquals(str, u.toString());
		assertEquals(u, new URIish(str));
	}

	public void testWindowsFile2() throws Exception {
		final String str = "D:\\m y";
		URIish u = new URIish(str);
		assertNull(u.getScheme());
		assertFalse(u.isRemote());
		assertEquals("D:/m y", u.getPath());
		assertEquals("D:/m y", u.toString());
		assertEquals(u, new URIish(str));
	}

	public void testUNC() throws Exception {
		final String str = "\\\\some\\place";
		URIish u = new URIish(str);
		assertNull(u.getScheme());
		assertFalse(u.isRemote());
		assertEquals("//some/place", u.getPath());
		assertEquals("//some/place", u.toString());
		assertEquals(u, new URIish(str));
	}

	public void testFileProtoUnix() throws Exception {
		final String str = "file:///home/m y";
		URIish u = new URIish(str);
		assertEquals("file", u.getScheme());
		assertFalse(u.isRemote());
		assertEquals("/home/m y", u.getPath());
		assertEquals(str, u.toString());
		assertEquals(u, new URIish(str));
	}

	public void testFileProtoWindows() throws Exception {
		final String str = "file:///D:/m y";
		URIish u = new URIish(str);
		assertEquals("file", u.getScheme());
		assertFalse(u.isRemote());
		assertEquals("D:/m y", u.getPath());
		assertEquals(str, u.toString());
		assertEquals(u, new URIish(str));
	}

	public void testGitProtoUnix() throws Exception {
		final String str = "git://example.com/home/m y";
		URIish u = new URIish(str);
		assertEquals("git", u.getScheme());
		assertTrue(u.isRemote());
		assertEquals("example.com", u.getHost());
		assertEquals("/home/m y", u.getPath());
		assertEquals(str, u.toString());
		assertEquals(u, new URIish(str));
	}

	public void testGitProtoUnixPort() throws Exception {
		final String str = "git://example.com:333/home/m y";
		URIish u = new URIish(str);
		assertEquals("git", u.getScheme());
		assertTrue(u.isRemote());
		assertEquals("example.com", u.getHost());
		assertEquals("/home/m y", u.getPath());
		assertEquals(333, u.getPort());
		assertEquals(str, u.toString());
		assertEquals(u, new URIish(str));
	}

	public void testGitProtoWindowsPort() throws Exception {
		final String str = "git://example.com:338/D:/m y";
		URIish u = new URIish(str);
		assertEquals("git", u.getScheme());
		assertTrue(u.isRemote());
		assertEquals("D:/m y", u.getPath());
		assertEquals(338, u.getPort());
		assertEquals("example.com", u.getHost());
		assertEquals(str, u.toString());
		assertEquals(u, new URIish(str));
	}

	public void testGitProtoWindows() throws Exception {
		final String str = "git://example.com/D:/m y";
		URIish u = new URIish(str);
		assertEquals("git", u.getScheme());
		assertTrue(u.isRemote());
		assertEquals("D:/m y", u.getPath());
		assertEquals("example.com", u.getHost());
		assertEquals(-1, u.getPort());
		assertEquals(str, u.toString());
		assertEquals(u, new URIish(str));
	}

	public void testScpStyleWithoutUser() throws Exception {
		final String str = "example.com:some/p ath";
		URIish u = new URIish(str);
		assertNull(u.getScheme());
		assertTrue(u.isRemote());
		assertEquals("some/p ath", u.getPath());
		assertEquals("example.com", u.getHost());
		assertEquals(-1, u.getPort());
		assertEquals(str, u.toString());
		assertEquals(u, new URIish(str));
	}

	public void testScpStyleWithUser() throws Exception {
		final String str = "user@example.com:some/p ath";
		URIish u = new URIish(str);
		assertNull(u.getScheme());
		assertTrue(u.isRemote());
		assertEquals("some/p ath", u.getPath());
		assertEquals("user", u.getUser());
		assertEquals("example.com", u.getHost());
		assertEquals(-1, u.getPort());
		assertEquals(str, u.toString());
		assertEquals(u, new URIish(str));
	}

	public void testGitSshProto() throws Exception {
		final String str = "git+ssh://example.com/some/p ath";
		URIish u = new URIish(str);
		assertEquals("git+ssh", u.getScheme());
		assertTrue(u.isRemote());
		assertEquals("/some/p ath", u.getPath());
		assertEquals("example.com", u.getHost());
		assertEquals(-1, u.getPort());
		assertEquals(str, u.toString());
		assertEquals(u, new URIish(str));
	}

	public void testSshGitProto() throws Exception {
		final String str = "ssh+git://example.com/some/p ath";
		URIish u = new URIish(str);
		assertEquals("ssh+git", u.getScheme());
		assertTrue(u.isRemote());
		assertEquals("/some/p ath", u.getPath());
		assertEquals("example.com", u.getHost());
		assertEquals(-1, u.getPort());
		assertEquals(str, u.toString());
		assertEquals(u, new URIish(str));
	}

	public void testSshProto() throws Exception {
		final String str = "ssh://example.com/some/p ath";
		URIish u = new URIish(str);
		assertEquals("ssh", u.getScheme());
		assertTrue(u.isRemote());
		assertEquals("/some/p ath", u.getPath());
		assertEquals("example.com", u.getHost());
		assertEquals(-1, u.getPort());
		assertEquals(str, u.toString());
		assertEquals(u, new URIish(str));
	}

	public void testSshProtoWithUserAndPort() throws Exception {
		final String str = "ssh://user@example.com:33/some/p ath";
		URIish u = new URIish(str);
		assertEquals("ssh", u.getScheme());
		assertTrue(u.isRemote());
		assertEquals("/some/p ath", u.getPath());
		assertEquals("example.com", u.getHost());
		assertEquals("user", u.getUser());
		assertNull(u.getPass());
		assertEquals(33, u.getPort());
		assertEquals(str, u.toString());
		assertEquals(u, new URIish(str));
	}

	public void testSshProtoWithUserPassAndPort() throws Exception {
		final String str = "ssh://user:pass@example.com:33/some/p ath";
		URIish u = new URIish(str);
		assertEquals("ssh", u.getScheme());
		assertTrue(u.isRemote());
		assertEquals("/some/p ath", u.getPath());
		assertEquals("example.com", u.getHost());
		assertEquals("user", u.getUser());
		assertEquals("pass", u.getPass());
		assertEquals(33, u.getPort());
		assertEquals(str, u.toPrivateString());
		assertEquals(u.setPass(null).toPrivateString(), u.toString());
		assertEquals(u, new URIish(str));
	}

	public void testGitWithUserHome() throws Exception {
		final String str = "git://example.com/~some/p ath";
		URIish u = new URIish(str);
		assertEquals("git", u.getScheme());
		assertTrue(u.isRemote());
		assertEquals("~some/p ath", u.getPath());
		assertEquals("example.com", u.getHost());
		assertNull(u.getUser());
		assertNull(u.getPass());
		assertEquals(-1, u.getPort());
		assertEquals(str, u.toPrivateString());
		assertEquals(u.setPass(null).toPrivateString(), u.toString());
		assertEquals(u, new URIish(str));
	}

	/* Resolving ~user is beyond standard Java API and need more support
	public void testFileWithUserHome() throws Exception {
		final String str = "~some/p ath";
		URIish u = new URIish(str);
		assertEquals("git", u.getScheme());
		assertTrue(u.isRemote());
		assertEquals("~some/p ath", u.getPath());
		assertEquals("example.com", u.getHost());
		assertNull(u.getUser());
		assertNull(u.getPass());
		assertEquals(-1, u.getPort());
		assertEquals(str, u.toPrivateString());
		assertEquals(u.setPass(null).toPrivateString(), u.toString());
		assertEquals(u, new URIish(str));
	}
	*/

	public void testFileWithNoneUserHomeWithTilde() throws Exception {
		final String str = "/~some/p ath";
		URIish u = new URIish(str);
		assertNull(u.getScheme());
		assertFalse(u.isRemote());
		assertEquals("/~some/p ath", u.getPath());
		assertNull(u.getHost());
		assertNull(u.getUser());
		assertNull(u.getPass());
		assertEquals(-1, u.getPort());
		assertEquals(str, u.toPrivateString());
		assertEquals(u.setPass(null).toPrivateString(), u.toString());
		assertEquals(u, new URIish(str));
	}

	public void testGetNullHumanishName() {
		try {
			new URIish().getHumanishName();
			fail("path must be not null");
		} catch (IllegalArgumentException e) {
			// expected
		}
	}

	public void testGetEmptyHumanishName() throws URISyntaxException {
		try {
			new URIish(GIT_SCHEME).getHumanishName();
			fail("empty path is useless");
		} catch (IllegalArgumentException e) {
			// expected
		}
	}

	public void testGetAbsEmptyHumanishName() {
		try {
			new URIish().getHumanishName();
			fail("empty path is useless");
		} catch (IllegalArgumentException e) {
			// expected
		}
	}

	public void testGetValidWithEmptySlashDotGitHumanishName()
			throws IllegalArgumentException, URISyntaxException {
		String humanishName = new URIish("/a/b/.git").getHumanishName();
		assertEquals("b", humanishName);
	}

	public void testGetWithSlashDotGitHumanishName() throws URISyntaxException {
		assertEquals("", new URIish("/.git").getHumanishName());
	}

	public void testGetTwoSlashesDotGitHumanishName() throws URISyntaxException {
		assertEquals("", new URIish("/.git").getHumanishName());
	}

	public void testGetValidHumanishName() throws IllegalArgumentException,
			URISyntaxException {
		String humanishName = new URIish(GIT_SCHEME + "abc").getHumanishName();
		assertEquals("abc", humanishName);
	}

	public void testGetValidSlashHumanishName()
			throws IllegalArgumentException, URISyntaxException {
		String humanishName = new URIish(GIT_SCHEME + "abc/").getHumanishName();
		assertEquals("abc", humanishName);
	}

	public void testGetSlashValidSlashHumanishName()
			throws IllegalArgumentException, URISyntaxException {
		String humanishName = new URIish("/abc/").getHumanishName();
		assertEquals("abc", humanishName);
	}

	public void testGetSlashValidSlashDotGitSlashHumanishName()
			throws IllegalArgumentException, URISyntaxException {
		String humanishName = new URIish("/abc/.git").getHumanishName();
		assertEquals("abc", humanishName);
	}

	public void testGetSlashSlashDotGitSlashHumanishName()
			throws IllegalArgumentException, URISyntaxException {
		final String humanishName = new URIish(GIT_SCHEME + "/abc//.git")
				.getHumanishName();
		assertEquals("may return an empty humanish name", "", humanishName);
	}

	public void testGetSlashesValidSlashHumanishName()
			throws IllegalArgumentException, URISyntaxException {
		String humanishName = new URIish("/a/b/c/").getHumanishName();
		assertEquals("c", humanishName);
	}

	public void testGetValidDotGitHumanishName()
			throws IllegalArgumentException, URISyntaxException {
		String humanishName = new URIish(GIT_SCHEME + "abc.git")
				.getHumanishName();
		assertEquals("abc", humanishName);
	}

	public void testGetValidDotGitSlashHumanishName()
			throws IllegalArgumentException, URISyntaxException {
		String humanishName = new URIish(GIT_SCHEME + "abc.git/")
				.getHumanishName();
		assertEquals("abc", humanishName);
	}

	public void testGetValidWithSlashDotGitHumanishName()
			throws IllegalArgumentException, URISyntaxException {
		String humanishName = new URIish("/abc.git").getHumanishName();
		assertEquals("abc", humanishName);
	}

	public void testGetValidWithSlashDotGitSlashHumanishName()
			throws IllegalArgumentException, URISyntaxException {
		String humanishName = new URIish("/abc.git/").getHumanishName();
		assertEquals("abc", humanishName);
	}

	public void testGetValidWithSlashesDotGitHumanishName()
			throws IllegalArgumentException, URISyntaxException {
		String humanishName = new URIish("/a/b/c.git").getHumanishName();
		assertEquals("c", humanishName);
	}

	public void testGetValidWithSlashesDotGitSlashHumanishName()
			throws IllegalArgumentException, URISyntaxException {
		String humanishName = new URIish("/a/b/c.git/").getHumanishName();
		assertEquals("c", humanishName);
	}

}
