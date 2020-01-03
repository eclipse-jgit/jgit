/*
 * Copyright (C) 2019, Google LLC and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.lib;

/** Assertion methods. */
public class MoreAsserts {
	/**
	 * Simple version of assertThrows that will be introduced in JUnit 4.13.
	 *
	 * @param expected
	 *            Expected throwable class
	 * @param r
	 *            Runnable that is expected to throw an exception.
	 * @return The thrown exception.
	 */
	public static <T extends Throwable> T assertThrows(Class<T> expected,
			ThrowingRunnable r) {
		try {
			r.run();
		} catch (Throwable actual) {
			if (expected.isAssignableFrom(actual.getClass())) {
				@SuppressWarnings("unchecked")
				T toReturn = (T) actual;
				return toReturn;
			}
			throw new AssertionError("Expected " + expected.getSimpleName()
					+ ", but got " + actual.getClass().getSimpleName(), actual);
		}
		throw new AssertionError(
				"Expected " + expected.getSimpleName() + " to be thrown");
	}

	public interface ThrowingRunnable {
		void run() throws Throwable;
	}

	private MoreAsserts() {
	}
}
