/*
 * Copyright (C) 2013 Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.archive;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

/**
 * This activator registers all format types from the
 * org.eclipse.jgit.archive package for use via the ArchiveCommand
 * API.
 *
 * This registration happens automatically behind the scenes
 * when the package is loaded as an OSGi bundle (and the corresponding
 * deregistration happens when the bundle is unloaded, to avoid
 * leaks).
 */
public class FormatActivator implements BundleActivator {
	/**
	 * {@inheritDoc}
	 *
	 * Registers all included archive formats by calling
	 * {@link ArchiveFormats#registerAll()}. This method is called by the OSGi
	 * framework when the bundle is started.
	 */
	@Override
	public void start(BundleContext context) {
		ArchiveFormats.registerAll();
	}

	/**
	 * {@inheritDoc}
	 *
	 * Cleans up after {@link #start(BundleContext)} by calling
	 * {@link ArchiveFormats#unregisterAll}.
	 */
	@Override
	public void stop(BundleContext context) {
		ArchiveFormats.unregisterAll();
	}
}
