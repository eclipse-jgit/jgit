/*
 * Copyright (C) 2021, Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.transport.sshd.agent;

import java.io.File;
import java.io.IOException;

import org.eclipse.jgit.annotations.NonNull;

/**
 * A factory for creating {@link Connector}s.
 *
 * @since 6.0
 */
public interface ConnectorFactory {

	/**
	 * Creates a new {@link Connector}.
	 *
	 * @param identityAgent
	 *            identifies the wanted agent connection; if {@code null}, the
	 *            factory is free to provide a {@link Connector} to a default
	 *            agent. The value will typically come from the IdentityAgent
	 *            setting in ~/.ssh/config.
	 * @param homeDir
	 *            the current local user's home directory as configured in the
	 *            {@link org.eclipse.jgit.transport.sshd.SshdSessionFactory}
	 * @return a new {@link Connector}
	 * @throws IOException
	 *             if no connector can be created
	 */
	@NonNull
	Connector create(String identityAgent, File homeDir)
			throws IOException;

	/**
	 * Tells whether this {@link ConnectorFactory} is applicable on the
	 * currently running platform.
	 *
	 * @return {@code true} if the factory can be used, {@code false} otherwise
	 */
	boolean isSupported();

	/**
	 * Retrieves a name for this factory.
	 *
	 * @return the name
	 */
	String getName();

}
