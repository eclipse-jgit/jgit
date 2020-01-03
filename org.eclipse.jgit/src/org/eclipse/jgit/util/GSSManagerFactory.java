/*
 * Copyright (C) 2014 Laurent Goujon <lgoujon@twitter.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.util;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;

import org.ietf.jgss.GSSManager;

/**
 * Factory to detect which GSSManager implementation should be used.
 *
 * @since 3.4
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
			} catch (InstantiationException | IllegalAccessException
					| IllegalArgumentException | InvocationTargetException e) {
				throw new Error(e);
			}
		}
	}
}
