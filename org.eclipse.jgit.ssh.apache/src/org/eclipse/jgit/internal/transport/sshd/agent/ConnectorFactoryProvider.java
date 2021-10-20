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

	private static final ConnectorFactory FACTORY = loadDefaultFactory();

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

	private ConnectorFactoryProvider() {
		// No instantiation
	}

	/**
	 * Retrieves the default {@link ConnectorFactory} obtained via the
	 * {@link ServiceLoader}.
	 *
	 * @return the {@link ConnectorFactory}, or {@code null} if none.
	 */
	public static ConnectorFactory getDefaultFactory() {
		return FACTORY;
	}
}
