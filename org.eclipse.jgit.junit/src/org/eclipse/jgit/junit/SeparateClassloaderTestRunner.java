/*
 * Copyright (C) 2019 Nail Samatov <sanail@yandex.ru> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.junit;

import static java.lang.ClassLoader.getSystemClassLoader;

import java.net.URL;
import java.net.URLClassLoader;

import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.InitializationError;

/**
 * This class is used when it's required to load jgit classes in separate
 * classloader for each test class. It can be needed to isolate static field
 * initialization between separate tests.
 */
public class SeparateClassloaderTestRunner extends BlockJUnit4ClassRunner {

	/**
	 * Creates a SeparateClassloaderTestRunner to run {@code klass}.
	 *
	 * @param klass
	 *            test class to run.
	 * @throws InitializationError
	 *             if the test class is malformed or can't be found.
	 */
	public SeparateClassloaderTestRunner(Class<?> klass)
			throws InitializationError {
		super(loadNewClass(klass));
	}

	private static Class<?> loadNewClass(Class<?> klass)
			throws InitializationError {
		try {
			URL[] urls = ((URLClassLoader) getSystemClassLoader()).getURLs();
			ClassLoader testClassLoader = new URLClassLoader(urls) {

				@Override
				public Class<?> loadClass(String name)
						throws ClassNotFoundException {
					if (name.startsWith("org.eclipse.jgit.")) {
						return super.findClass(name);
					}

					return super.loadClass(name);
				}
			};
			return Class.forName(klass.getName(), true, testClassLoader);
		} catch (ClassNotFoundException e) {
			throw new InitializationError(e);
		}
	}
}
