/*
 * Copyright (C) 2010, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.events;

/**
 * Describes a change to one or more keys in the configuration.
 */
public class ConfigChangedEvent extends RepositoryEvent<ConfigChangedListener> {
	/** {@inheritDoc} */
	@Override
	public Class<ConfigChangedListener> getListenerType() {
		return ConfigChangedListener.class;
	}

	/** {@inheritDoc} */
	@Override
	public void dispatch(ConfigChangedListener listener) {
		listener.onConfigChanged(this);
	}
}
