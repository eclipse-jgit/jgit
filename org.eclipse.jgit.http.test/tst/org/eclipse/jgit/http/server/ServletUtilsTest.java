/*
 * Copyright (C) 2011, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.http.server;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ServletUtilsTest {
	@Test
	public void testAcceptGzip() {
		assertFalse(ServletUtils.acceptsGzipEncoding((String) null));
		assertFalse(ServletUtils.acceptsGzipEncoding(""));

		assertTrue(ServletUtils.acceptsGzipEncoding("gzip"));
		assertTrue(ServletUtils.acceptsGzipEncoding("deflate,gzip"));
		assertTrue(ServletUtils.acceptsGzipEncoding("gzip,deflate"));

		assertFalse(ServletUtils.acceptsGzipEncoding("gzip(proxy)"));
		assertFalse(ServletUtils.acceptsGzipEncoding("proxy-gzip"));
	}
}
