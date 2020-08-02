/*
 * Copyright (C) 2020, Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.http.test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.jgit.junit.http.HttpTestCase;
import org.eclipse.jgit.transport.HttpTransport;
import org.eclipse.jgit.transport.http.HttpConnectionFactory;
import org.eclipse.jgit.transport.http.JDKHttpConnectionFactory;
import org.eclipse.jgit.transport.http.apache.HttpClientConnectionFactory;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * Abstract test base class for running HTTP-related tests with all connection
 * factories provided in JGit and with both protocol V0 and V2.
 */
@Ignore
@RunWith(Parameterized.class)
public abstract class AllProtocolsHttpTestCase extends HttpTestCase {

	protected static class TestParameters {

		public final HttpConnectionFactory factory;

		public final boolean enableProtocolV2;

		public TestParameters(HttpConnectionFactory factory,
				boolean enableProtocolV2) {
			this.factory = factory;
			this.enableProtocolV2 = enableProtocolV2;
		}

		@Override
		public String toString() {
			return factory.toString() + " protocol "
					+ (enableProtocolV2 ? "V2" : "V0");
		}
	}

	@Parameters(name = "{0}")
	public static Collection<TestParameters> data() {
		// run all tests with both connection factories we have
		HttpConnectionFactory[] factories = new HttpConnectionFactory[] {
				new JDKHttpConnectionFactory() {

					@Override
					public String toString() {
						return this.getClass().getSuperclass().getName();
					}
				}, new HttpClientConnectionFactory() {

					@Override
					public String toString() {
						return this.getClass().getSuperclass().getName();
					}
				} };
		List<TestParameters> result = new ArrayList<>();
		for (HttpConnectionFactory factory : factories) {
			result.add(new TestParameters(factory, false));
			result.add(new TestParameters(factory, true));
		}
		return result;
	}

	protected final boolean enableProtocolV2;

	protected AllProtocolsHttpTestCase(TestParameters params) {
		HttpTransport.setConnectionFactory(params.factory);
		enableProtocolV2 = params.enableProtocolV2;
	}

	private static HttpConnectionFactory originalFactory;

	@BeforeClass
	public static void saveConnectionFactory() {
		originalFactory = HttpTransport.getConnectionFactory();
	}

	@AfterClass
	public static void restoreConnectionFactory() {
		HttpTransport.setConnectionFactory(originalFactory);
	}

}
