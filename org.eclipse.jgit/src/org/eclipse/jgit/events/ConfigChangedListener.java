/*
 * Copyright (C) 2008, Robin Rosenberg <robin.rosenberg@dewire.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.events;

/**
 * Receives {@link org.eclipse.jgit.events.ConfigChangedEvent}s.
 */
public interface ConfigChangedListener extends RepositoryListener {
	/**
	 * Invoked when any change is made to the configuration.
	 *
	 * @param event
	 *            information about the changes.
	 */
	void onConfigChanged(ConfigChangedEvent event);
}
