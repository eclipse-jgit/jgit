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

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;

import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.InitializationError;

/**
 * This class is used when it's required to load jgit classes in separate
 * classloader for each test class. It can be needed to isolate static field
 * initialization between separate tests.
 *
 * @since 5.5
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
			String pathSeparator = System.getProperty("path.separator");
			String[] classPathEntries = System.getProperty("java.class.path")
					.split(pathSeparator);
			URL[] urls = new URL[classPathEntries.length];
			for (int i = 0; i < classPathEntries.length; i++) {
				urls[i] = Paths.get(classPathEntries[i]).toUri().toURL();
			}
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
		} catch (ClassNotFoundException | MalformedURLException e) {
			throw new InitializationError(e);
		}
	}
}
