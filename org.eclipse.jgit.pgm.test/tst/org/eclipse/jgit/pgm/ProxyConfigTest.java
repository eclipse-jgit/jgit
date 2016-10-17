/*
 * Copyright (C) 2016, Chrisian Halstrick <christian.halstrick@sap.com> and
 * other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v1.0 which accompanies this
 * distribution, is reproduced below, and is available at
 * http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * - Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the names of its
 * contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package org.eclipse.jgit.pgm;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

/**
 * Test how the content of the environment variables http[s]_proxy (upper- and
 * lowercase) influence the setting of the system properties
 * http[s].proxy[Host|Port]
 */
public class ProxyConfigTest {
	private ProcessBuilder processBuilder;

	private Map<String, String> environment;

	@Before
	public void setUp() {
		String separator = System.getProperty("file.separator");
		String classpath = System.getProperty("java.class.path");
		String path = System.getProperty("java.home") + separator + "bin"
				+ separator + "java";
		processBuilder = new ProcessBuilder(path, "-cp", classpath,
				ProxyPropertiesDumper.class.getName());
		environment = processBuilder.environment();
		environment.remove("http_proxy");
		environment.remove("https_proxy");
		environment.remove("HTTP_PROXY");
		environment.remove("HTTPS_PROXY");
	}

	@Test
	public void testNoSetting() throws Exception {
		Process start = processBuilder.start();
		start.waitFor();
		assertEquals(
				"http.proxyHost: null, http.proxyPort: null, https.proxyHost: null, https.proxyPort: null",
				getOutput(start));
	}

	@Test
	public void testHttpProxy_lowerCase() throws Exception {
		environment.put("http_proxy", "http://xx:1234");
		Process start = processBuilder.start();
		start.waitFor();
		assertEquals(
				"http.proxyHost: xx, http.proxyPort: 1234, https.proxyHost: null, https.proxyPort: null",
				getOutput(start));
	}

	@Test
	public void testHttpProxy_upperCase() throws Exception {
		environment.put("HTTP_PROXY", "http://XX:1234");
		Process start = processBuilder.start();
		start.waitFor();
		assertEquals(
				"http.proxyHost: null, http.proxyPort: null, https.proxyHost: null, https.proxyPort: null",
				getOutput(start));
	}

	@Test
	public void testHttpProxy_bothCases() throws Exception {
		environment.put("http_proxy", "http://xx:1234");
		environment.put("HTTP_PROXY", "http://XX:1234");
		Process start = processBuilder.start();
		start.waitFor();
		assertEquals(
				"http.proxyHost: xx, http.proxyPort: 1234, https.proxyHost: null, https.proxyPort: null",
				getOutput(start));
	}

	@Test
	public void testHttpsProxy_lowerCase() throws Exception {
		environment.put("https_proxy", "http://xx:1234");
		Process start = processBuilder.start();
		start.waitFor();
		assertEquals(
				"http.proxyHost: null, http.proxyPort: null, https.proxyHost: xx, https.proxyPort: 1234",
				getOutput(start));
	}

	@Test
	public void testHttpsProxy_upperCase() throws Exception {
		environment.put("HTTPS_PROXY", "http://XX:1234");
		Process start = processBuilder.start();
		start.waitFor();
		assertEquals(
				"http.proxyHost: null, http.proxyPort: null, https.proxyHost: XX, https.proxyPort: 1234",
				getOutput(start));
	}

	@Test
	public void testHttpsProxy_bothCases() throws Exception {
		environment.put("https_proxy", "http://xx:1234");
		environment.put("HTTPS_PROXY", "http://XX:1234");
		Process start = processBuilder.start();
		start.waitFor();
		assertEquals(
				"http.proxyHost: null, http.proxyPort: null, https.proxyHost: xx, https.proxyPort: 1234",
				getOutput(start));
	}

	@Test
	public void testAll() throws Exception {
		environment.put("http_proxy", "http://xx:1234");
		environment.put("HTTP_PROXY", "http://XX:1234");
		environment.put("https_proxy", "http://yy:1234");
		environment.put("HTTPS_PROXY", "http://YY:1234");
		Process start = processBuilder.start();
		start.waitFor();
		assertEquals(
				"http.proxyHost: xx, http.proxyPort: 1234, https.proxyHost: yy, https.proxyPort: 1234",
				getOutput(start));
	}

	@Test
	public void testDontOverwriteHttp()
			throws IOException, InterruptedException {
		environment.put("http_proxy", "http://xx:1234");
		environment.put("HTTP_PROXY", "http://XX:1234");
		environment.put("https_proxy", "http://yy:1234");
		environment.put("HTTPS_PROXY", "http://YY:1234");
		List<String> command = processBuilder.command();
		command.add(1, "-Dhttp.proxyHost=gondola");
		command.add(2, "-Dhttp.proxyPort=5678");
		command.add("dontClearProperties");
		Process start = processBuilder.start();
		start.waitFor();
		assertEquals(
				"http.proxyHost: gondola, http.proxyPort: 5678, https.proxyHost: yy, https.proxyPort: 1234",
				getOutput(start));
	}

	@Test
	public void testOverwriteHttpPort()
			throws IOException, InterruptedException {
		environment.put("http_proxy", "http://xx:1234");
		environment.put("HTTP_PROXY", "http://XX:1234");
		environment.put("https_proxy", "http://yy:1234");
		environment.put("HTTPS_PROXY", "http://YY:1234");
		List<String> command = processBuilder.command();
		command.add(1, "-Dhttp.proxyPort=5678");
		command.add("dontClearProperties");
		Process start = processBuilder.start();
		start.waitFor();
		assertEquals(
				"http.proxyHost: xx, http.proxyPort: 1234, https.proxyHost: yy, https.proxyPort: 1234",
				getOutput(start));
	}

	private static String getOutput(Process p)
			throws IOException, UnsupportedEncodingException {
		try (InputStream inputStream = p.getInputStream()) {
			ByteArrayOutputStream result = new ByteArrayOutputStream();
			byte[] buffer = new byte[1024];
			int length;
			while ((length = inputStream.read(buffer)) != -1) {
				result.write(buffer, 0, length);
			}
			return result.toString("UTF-8");
		}
	}
}

class ProxyPropertiesDumper {
	public static void main(String args[]) {
		try {
			if (args.length == 0 || !args[0].equals("dontClearProperties")) {
				System.clearProperty("http.proxyHost");
				System.clearProperty("http.proxyPort");
				System.clearProperty("https.proxyHost");
				System.clearProperty("https.proxyPort");
			}
			Main.configureHttpProxy();
			System.out.printf(
					"http.proxyHost: %s, http.proxyPort: %s, https.proxyHost: %s, https.proxyPort: %s",
					System.getProperty("http.proxyHost"),
					System.getProperty("http.proxyPort"),
					System.getProperty("https.proxyHost"),
					System.getProperty("https.proxyPort"));
			System.out.flush();
		} catch (MalformedURLException e) {
			System.out.println("exception: " + e);
		}
	}
}
