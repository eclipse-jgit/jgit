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

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.jgit.junit.http.HttpTestCase;
import org.eclipse.jgit.transport.HttpTransport;
import org.eclipse.jgit.transport.http.HttpConnectionFactory;
import org.eclipse.jgit.transport.http.JDKHttpConnectionFactory;
import org.eclipse.jgit.transport.http.apache.HttpClientConnectionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Abstract test base class for running HTTP-related tests with all connection
 * factories provided in JGit and with both protocol V0 and V2.
 */
@Disabled
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

	@ParameterizedTest(name = "{0}")
	@MethodSource("allProtocols")
	@Retention(RetentionPolicy.RUNTIME)
	protected @interface TestAllProtocols {
		// empty
	}

	public static Collection<Arguments> allProtocols() {
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
		List<Arguments> result = new ArrayList<>();
		for (HttpConnectionFactory factory : factories) {
			result.add(Arguments.of(new TestParameters(factory, false)));
			result.add(Arguments.of(new TestParameters(factory, true)));
		}
		return result;
	}

	protected boolean enableProtocolV2;

	protected void configure(TestParameters params) {
		HttpTransport.setConnectionFactory(params.factory);
		enableProtocolV2 = params.enableProtocolV2;
	}

	private static HttpConnectionFactory originalFactory;

	@BeforeAll
	public static void saveConnectionFactory() {
		originalFactory = HttpTransport.getConnectionFactory();
	}

	@AfterAll
	public static void restoreConnectionFactory() {
		HttpTransport.setConnectionFactory(originalFactory);
	}

}
