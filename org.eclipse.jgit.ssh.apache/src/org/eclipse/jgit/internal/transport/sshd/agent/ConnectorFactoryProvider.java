/*
 * Copyright (C) 2021, Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.transport.sshd.agent;

import java.util.Iterator;
import java.util.ServiceLoader;

import org.eclipse.jgit.transport.sshd.agent.ConnectorFactory;

/**
 * Provides a {@link ConnectorFactory} obtained via the {@link ServiceLoader}.
 */
public final class ConnectorFactoryProvider {

	private static volatile ConnectorFactory INSTANCE = loadDefaultFactory();

	private static ConnectorFactory loadDefaultFactory() {
		ServiceLoader<ConnectorFactory> loader = ServiceLoader
				.load(ConnectorFactory.class);
		Iterator<ConnectorFactory> iter = loader.iterator();
		while (iter.hasNext()) {
			ConnectorFactory candidate = iter.next();
			if (candidate.isSupported()) {
				return candidate;
			}
		}
		return null;

	}

	/**
	 * Retrieves the currently set default {@link ConnectorFactory}.
	 *
	 * @return the {@link ConnectorFactory}, or {@code null} if none.
	 */
	public static ConnectorFactory getDefaultFactory() {
		return INSTANCE;
	}

	/**
	 * Sets the default {@link ConnectorFactory}.
	 *
	 * @param factory
	 *            {@link ConnectorFactory} to use, or {@code null} to use the
	 *            factory discovered via the {@link ServiceLoader}.
	 */
	public static void setDefaultFactory(ConnectorFactory factory) {
		INSTANCE = factory == null ? loadDefaultFactory() : factory;
	}

	private ConnectorFactoryProvider() {
		// No instantiation
	}
}
