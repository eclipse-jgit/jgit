/*
 * Copyright (C) 2008-2009, Google Inc.
 * Copyright (C) 2009, Robin Rosenberg <robin.rosenberg@dewire.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.transport;

import java.io.IOException;
import java.util.Collection;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Config.SectionParser;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;

/**
 * A service exposed by {@link org.eclipse.jgit.transport.Daemon} over anonymous
 * <code>git://</code>.
 */
public abstract class DaemonService {
	private final String command;

	private final SectionParser<ServiceConfig> configKey;

	private boolean enabled;

	private boolean overridable;

	DaemonService(String cmdName, String cfgName) {
		command = cmdName.startsWith("git-") ? cmdName : "git-" + cmdName; //$NON-NLS-1$ //$NON-NLS-2$
		configKey = cfg -> new ServiceConfig(DaemonService.this, cfg, cfgName);
		overridable = true;
	}

	private static class ServiceConfig {
		final boolean enabled;

		ServiceConfig(final DaemonService service, final Config cfg,
				final String name) {
			enabled = cfg.getBoolean("daemon", name, service.isEnabled()); //$NON-NLS-1$
		}
	}

	/**
	 * Whether this service is enabled for invocation.
	 *
	 * @return whether this service is enabled for invocation.
	 */
	public boolean isEnabled() {
		return enabled;
	}

	/**
	 * Set if it is allowed to use this service
	 *
	 * @param on
	 *            {@code true} to allow this service to be used; {@code false}
	 *            to deny it.
	 */
	public void setEnabled(boolean on) {
		enabled = on;
	}

	/** @return can this service be configured in the repository config file? */
	/**
	 * Whether this service can be configured in the repository config file
	 *
	 * @return whether this service can be configured in the repository config
	 *         file
	 */
	public boolean isOverridable() {
		return overridable;
	}

	/**
	 * Whether to permit repositories to override this service's enabled state
	 * with the <code>daemon.servicename</code> config setting.
	 *
	 * @param on
	 *            {@code true} to permit repositories to override this service's
	 *            enabled state with the <code>daemon.servicename</code> config
	 *            setting.
	 */
	public void setOverridable(boolean on) {
		overridable = on;
	}

	/**
	 * Get name of the command requested by clients.
	 *
	 * @return name of the command requested by clients.
	 */
	public String getCommandName() {
		return command;
	}

	/**
	 * Determine if this service can handle the requested command.
	 *
	 * @param commandLine
	 *            input line from the client.
	 * @return true if this command can accept the given command line.
	 */
	public boolean handles(String commandLine) {
		return command.length() + 1 < commandLine.length()
				&& commandLine.charAt(command.length()) == ' '
				&& commandLine.startsWith(command);
	}

	void execute(DaemonClient client, String commandLine,
			@Nullable Collection<String> extraParameters)
			throws IOException, ServiceNotEnabledException,
			ServiceNotAuthorizedException {
		final String name = commandLine.substring(command.length() + 1);
		try (Repository db = client.getDaemon().openRepository(client, name)) {
			if (isEnabledFor(db)) {
				execute(client, db, extraParameters);
			}
		} catch (ServiceMayNotContinueException e) {
			// An error when opening the repo means the client is expecting a ref
			// advertisement, so use that style of error.
			PacketLineOut pktOut = new PacketLineOut(client.getOutputStream());
			pktOut.writeString("ERR " + e.getMessage() + "\n"); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}

	private boolean isEnabledFor(Repository db) {
		if (isOverridable())
			return db.getConfig().get(configKey).enabled;
		return isEnabled();
	}

	abstract void execute(DaemonClient client, Repository db,
			@Nullable Collection<String> extraParameters)
			throws IOException, ServiceNotEnabledException,
			ServiceNotAuthorizedException;
}
