/*
 * Copyright (C) 2019, Thomas Wolf <thomas.wolf@paranor.ch> and others
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
import java.util.stream.Stream;

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
 * factories provided in JGit: the JDK {@link JDKHttpConnectionFactory} and the
 * Apache HTTP {@link HttpClientConnectionFactory}.
 */
@Disabled
public abstract class AllFactoriesHttpTestCase extends HttpTestCase {

	public static Stream<Arguments> allImplementations() {
		// run all tests with both connection factories we have
		return Stream.of(Arguments.of(new JDKHttpConnectionFactory() {
			@Override
			public String toString() {
				return this.getClass().getSuperclass().getName();
			}
		}), Arguments.of(new HttpClientConnectionFactory() {
			@Override
			public String toString() {
				return this.getClass().getSuperclass().getName();
			}
		}));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("allImplementations")
	@Retention(RetentionPolicy.RUNTIME)
	protected @interface TestAllImplementations {
		// empty
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
