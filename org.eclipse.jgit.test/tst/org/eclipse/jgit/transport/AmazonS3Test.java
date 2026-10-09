/*
 * Copyright (C) 2026, Weiqing Zhou
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.transport;

import static org.junit.Assert.assertEquals;

import java.net.HttpURLConnection;
import java.util.Collections;
import java.util.Properties;

import org.junit.Test;

public class AmazonS3Test {

	private static Properties properties(boolean pathStyle, String signature) {
		Properties props = new Properties();
		props.setProperty(AmazonS3.Keys.ACCESS_KEY, "access");
		props.setProperty(AmazonS3.Keys.SECRET_KEY, "secret");
		props.setProperty(AmazonS3.Keys.DOMAIN, "s3.example.test:9000");
		props.setProperty(AmazonS3.Keys.AWS_API_SIGNATURE_VERSION, signature);
		props.setProperty(AmazonS3.Keys.REGION, "us-east-1");
		props.setProperty(AmazonS3.Keys.PATH_STYLE_ACCESS,
				Boolean.toString(pathStyle));
		return props;
	}

	@Test
	public void testVirtualHostStyleRemainsDefault() throws Exception {
		AmazonS3 s3 = new AmazonS3(properties(false, "4"));
		HttpURLConnection connection = s3.open("GET", "bucket", "repo/key",
				Collections.emptyMap());
		assertEquals("http://bucket.s3.example.test:9000/repo/key",
				connection.getURL().toString());
	}

	@Test
	public void testPathStyleUrl() throws Exception {
		AmazonS3 s3 = new AmazonS3(properties(true, "4"));
		HttpURLConnection connection = s3.open("GET", "bucket", "repo/key",
				Collections.emptyMap());
		assertEquals("http://s3.example.test:9000/bucket/repo/key",
				connection.getURL().toString());
	}

}
