/*
 * Copyright (C) 2014 Laurent Goujon <lgoujon@twitter.com>
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

package org.eclipse.jgit.util;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;

import org.ietf.jgss.GSSManager;

/**
 * Factory to detect which GSSManager implementation should be used.
 *
 */
public abstract class GSSManagerFactory {
	/**
	 * Auto-detects the GSSManager factory to use based on system.
	 *
	 * @return detected GSSManager factory
	 */
	public static GSSManagerFactory detect() {
		return (SunGSSManagerFactory.isSupported()) ? new SunGSSManagerFactory()
				: new DefaultGSSManagerFactory();
	}

	/**
	 * Returns a GSS Manager instance for the provided url
	 *
	 * @param url
	 *            the repository url
	 * @return a GSSManager instance
	 */
	public abstract GSSManager newInstance(URL url);

	/**
	 * DefaultGSSManagerFactory uses @link {@link GSSManager#getInstance()} but
	 * you might need to set
	 * <code>javax.security.auth.useSubjectCredsOnly</code> system property to
	 * <code>false</code> for authentication to work.
	 */
	private static class DefaultGSSManagerFactory extends GSSManagerFactory {
		private static final GSSManager INSTANCE = GSSManager.getInstance();

		@Override
		public GSSManager newInstance(URL url) {
			return INSTANCE;
		}
	}

	private static class SunGSSManagerFactory extends GSSManagerFactory {
		private static boolean IS_SUPPORTED;
		private static Constructor<?> HTTP_CALLER_INFO_CONSTRUCTOR;
		private static Constructor<?> HTTP_CALLER_CONSTRUCTOR;

		private static Constructor<?> GSS_MANAGER_IMPL_CONSTRUCTOR;

		static {
			try {
				init();
				IS_SUPPORTED = true;
			} catch (Exception e) {
				IS_SUPPORTED = false;
			}
		}

		private static void init() throws ClassNotFoundException,
				NoSuchMethodException {
			Class<?> httpCallerInfoClazz = Class
					.forName("sun.net.www.protocol.http.HttpCallerInfo"); //$NON-NLS-1$
			HTTP_CALLER_INFO_CONSTRUCTOR = httpCallerInfoClazz
					.getConstructor(URL.class);

			Class<?> httpCallerClazz = Class
					.forName("sun.security.jgss.HttpCaller"); //$NON-NLS-1$
			HTTP_CALLER_CONSTRUCTOR = httpCallerClazz
					.getConstructor(httpCallerInfoClazz);

			Class<?> gssCallerClazz = Class
					.forName("sun.security.jgss.GSSCaller"); //$NON-NLS-1$
			Class<?> gssManagerImplClazz = Class
					.forName("sun.security.jgss.GSSManagerImpl"); //$NON-NLS-1$
			GSS_MANAGER_IMPL_CONSTRUCTOR = gssManagerImplClazz
					.getConstructor(gssCallerClazz);

		}

		/**
		 * Detects if SunGSSManagerProvider is supported by the system
		 *
		 * @return true if it is supported
		 */
		public static boolean isSupported() {
			return IS_SUPPORTED;
		}

		@Override
		public GSSManager newInstance(URL url) {
			try {
				Object httpCallerInfo = HTTP_CALLER_INFO_CONSTRUCTOR
						.newInstance(url);
				Object httpCaller = HTTP_CALLER_CONSTRUCTOR
						.newInstance(httpCallerInfo);

				return (GSSManager) GSS_MANAGER_IMPL_CONSTRUCTOR
						.newInstance(httpCaller);
			} catch (InstantiationException e) {
				throw new Error(e);
			} catch (IllegalAccessException e) {
				throw new Error(e);
			} catch (IllegalArgumentException e) {
				throw new Error(e);
			} catch (InvocationTargetException e) {
				throw new Error(e);
			}
		}
	}
}
