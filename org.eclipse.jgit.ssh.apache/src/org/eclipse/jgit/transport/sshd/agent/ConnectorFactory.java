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
import java.util.Collection;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.internal.transport.sshd.agent.ConnectorFactoryProvider;

/**
 * A factory for creating {@link Connector}s. This is a service provider
 * interface; implementations are discovered via the
 * {@link java.util.ServiceLoader}, or can be set explicitly on a
 * {@link org.eclipse.jgit.transport.sshd.SshdSessionFactory}.
 *
 * @since 6.0
 */
public interface ConnectorFactory {

	/**
	 * Retrieves the currently set default {@link ConnectorFactory}. This is the
	 * factory that is used unless overridden by the
	 * {@link org.eclipse.jgit.transport.sshd.SshdSessionFactory}.
	 *
	 * @return the current default factory; may be {@code null} if none is set
	 *         and the {@link java.util.ServiceLoader} cannot find any suitable
	 *         implementation
	 */
	static ConnectorFactory getDefault() {
		return ConnectorFactoryProvider.getDefaultFactory();
	}

	/**
	 * Sets a default {@link ConnectorFactory}. This is the factory that is used
	 * unless overridden by the
	 * {@link org.eclipse.jgit.transport.sshd.SshdSessionFactory}.
	 * <p>
	 * If no default factory is set programmatically, an implementation is
	 * discovered via the {@link java.util.ServiceLoader}.
	 * </p>
	 *
	 * @param factory
	 *            {@link ConnectorFactory} to set, or {@code null} to revert to
	 *            the default behavior of using the
	 *            {@link java.util.ServiceLoader}.
	 */
	static void setDefault(ConnectorFactory factory) {
		ConnectorFactoryProvider.setDefaultFactory(factory);
	}

	/**
	 * Creates a new {@link Connector}.
	 *
	 * @param identityAgent
	 *            identifies the wanted agent connection; if {@code null}, the
	 *            factory is free to provide a {@link Connector} to a default
	 *            agent. The value will typically come from the
	 *            {@code IdentityAgent} setting in {@code ~/.ssh/config}.
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

	/**
	 * {@link ConnectorDescriptor}s describe available {@link Connector}s a
	 * {@link ConnectorFactory} may provide.
	 * <p>
	 * A {@link ConnectorFactory} may support connecting to different SSH
	 * agents. Agents are identified by name; a user can choose a specific agent
	 * for instance via the {@code IdentityAgent} setting in
	 * {@code ~/.ssh/config}.
	 * </p>
	 * <p>
	 * OpenSSH knows two built-in names: "none" for not using any agent, and
	 * "SSH_AUTH_SOCK" for using an agent that communicates over a Unix domain
	 * socket given by the value of environment variable {@code SSH_AUTH_SOCK}.
	 * Other agents can be specified in OpenSSH by specifying the socket file
	 * directly. (The "standard" OpenBSD OpenSSH knows only this communication
	 * mechanism.) "SSH_AUTH_SOCK" is also the default in OpenBSD OpenSSH if
	 * nothing is configured.
	 * </p>
	 * <p>
	 * A particular {@link ConnectorFactory} may support more communication
	 * mechanisms or different agents. For instance, a factory on Windows might
	 * support Pageant, Win32-OpenSSH, or even git bash ssh-agent, and might
	 * accept internal names like "pageant", "openssh", "SSH_AUTH_SOCK" in
	 * {@link ConnectorFactory#create(String, File)} to choose among them.
	 * </p>
	 * The {@link ConnectorDescriptor} interface and the
	 * {@link ConnectorFactory#getSupportedConnectors()} and
	 * {@link ConnectorFactory#getDefaultConnector()} methods provide a way for
	 * code using a {@link ConnectorFactory} to learn what the factory supports
	 * and thus implement some way by which a user can influence the default
	 * behavior if {@code IdentityAgent} is not set or
	 * {@link ConnectorFactory#create(String, File)} is called with
	 * {@code identityAgent == null}.
	 */
	interface ConnectorDescriptor {

		/**
		 * Retrieves the internal name of a supported {@link Connector}. The
		 * internal name is the one a user can specify for instance in the
		 * {@code IdentityAgent} setting in {@code ~/.ssh/config} to select the
		 * connector.
		 *
		 * @return the internal name; not empty
		 */
		@NonNull
		String getIdentityAgent();

		/**
		 * Retrieves a display name for a {@link Connector}, suitable for
		 * showing in a UI.
		 *
		 * @return the display name; properly localized and not empty
		 */
		@NonNull
		String getDisplayName();
	}

	/**
	 * Tells which kinds of SSH agents this {@link ConnectorFactory} supports.
	 * <p>
	 * An implementation of this method should document the possible values it
	 * returns.
	 * </p>
	 *
	 * @return an immutable collection of {@link ConnectorDescriptor}s,
	 *         including {@link #getDefaultConnector()} and not including a
	 *         descriptor for internal name "none"
	 */
	@NonNull
	Collection<ConnectorDescriptor> getSupportedConnectors();

	/**
	 * Tells what kind of {@link Connector} this {@link ConnectorFactory}
	 * creates if {@link ConnectorFactory#create(String, File)} is called with
	 * {@code identityAgent == null}.
	 *
	 * @return a {@link ConnectorDescriptor} for the default connector
	 */
	ConnectorDescriptor getDefaultConnector();
}
