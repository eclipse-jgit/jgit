/*
 * Copyright (C) 2019 Nail Samatov <sanail@yandex.ru>
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
