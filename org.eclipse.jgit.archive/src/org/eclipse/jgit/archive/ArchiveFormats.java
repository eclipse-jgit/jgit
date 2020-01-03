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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.api.ArchiveCommand;

/**
 * Registers all format types from the org.eclipse.jgit.archive package for use
 * via the ArchiveCommand API.
 *
 * See {@link org.eclipse.jgit.archive.FormatActivator} for an OSGi bundle
 * activator that performs the same registration automatically.
 */
public class ArchiveFormats {
	private static final List<String> myFormats = new ArrayList<>();

	private static final void register(String name, ArchiveCommand.Format<?> fmt) {
		myFormats.add(name);
		ArchiveCommand.registerFormat(name, fmt);
	}

	/**
	 * Register all included archive formats so they can be used
	 * as arguments to the ArchiveCommand.setFormat() method.
	 *
	 * Not thread-safe.
	 */
	public static void registerAll() {
		register("tar", new TarFormat()); //$NON-NLS-1$
		register("tgz", new TgzFormat()); //$NON-NLS-1$
		register("tbz2", new Tbz2Format()); //$NON-NLS-1$
		register("txz", new TxzFormat()); //$NON-NLS-1$
		register("zip", new ZipFormat()); //$NON-NLS-1$
	}

	/**
	 * Clean up by deregistering all formats that were registered
	 * by registerAll().
	 *
	 * Not thread-safe.
	 */
	public static void unregisterAll() {
		for (String name : myFormats) {
			ArchiveCommand.unregisterFormat(name);
		}
		myFormats.clear();
	}
}
