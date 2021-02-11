/*
 * Copyright (c) 2021 Qualcomm Innovation Center, Inc.
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.file;

import java.io.File;

import org.eclipse.jgit.internal.storage.pack.PackExt;

/**
 * A pack file (or pack related) File.
 *
 * Example: "pack-0123456789012345678901234567890123456789.idx"
 */
class PackFile extends File {
	private static final long serialVersionUID = 1L;
	private static final String PREFIX = "pack-"; //$NON-NLS-1$

	private final String base; // PREFIX + id i.e. pack-0123456789012345678901234567890123456789
	private final String id; // i.e. 0123456789012345678901234567890123456789
	private final boolean hasOldPrefix;
	private final PackExt packExt;

	/**
	 * Create a PackFileName for a pack or related file.
	 *
	 * @param file
	 *            File pointing to the location of the file.
	 */
	PackFile(File file) {
		this(file.getParentFile(), file.getName());
	}

	/**
	 * Create a PackFileName for a pack or related file.
	 *
	 * @param directory
	 *            Directory to create the PackFileName in.
	 * @param name
	 *            Filename (last path section) of the PackFile
	 */
	PackFile(File directory, String name) {
		super(directory, name);
		int dot = name.lastIndexOf('.');

		if (dot < 0) {
			base = name;
			hasOldPrefix = false;
			packExt = null;
		} else {
			base = name.substring(0, dot);
			String tail = name.substring(dot + 1); // ["old-"] + extension
			packExt = getPackExt(tail);
			String old = tail.substring(0, tail.length() - getExtension().length());
			hasOldPrefix = old.equals(getExtPrefix(true));
		}

		id = base.startsWith(PREFIX) ? base.substring(PREFIX.length()) : base;
	}

	/**
	 * Getter for the field <code>id</code>.
	 *
	 * @return the <code>id</code> (40 Hex char) section of the name.
	 */
	String getId() {
		return id;
	}

	/**
	 * Getter for the field <code>packExt</code>.
	 *
	 * @return the <code>packExt</code> of the name.
	 */
	PackExt getPackExt() {
		return packExt;
	}

	/**
	 * Create a new similar PackFileName with the given extension instead.
	 *
	 * @param ext
	 *            PackExt the extension to use.
	 * @return a PackFileName instance with ext.extension()
	 */
	PackFile create(PackExt ext) {
		return new PackFile(getParentFile(), getName(ext));
	}

	/**
	 * Create a new similar PackFileName in the given directory.
	 *
	 * @param directory
	 *            Directory to create the new Name in.
	 * @param isPreserved
	 *            Whether the new PackFileName should have "old-" prefixing the
	 *            extension.
	 * @return a PackFileName in the given directory
	 */
	PackFile createForDirectory(File directory, boolean isPreserved) {
		return new PackFile(directory, getName(isPreserved));
	}

	private String getName(PackExt ext) {
		return base + '.' + getExtPrefix(hasOldPrefix) + ext.getExtension();
	}

	private String getName(boolean isPreserved) {
		return base + '.' + getExtPrefix(isPreserved) + getExtension();
	}

	private String getExtension() {
		return packExt == null ? "" : packExt.getExtension(); //$NON-NLS-1$
	}

	private static String getExtPrefix(boolean isPreserved) {
		return isPreserved ? "old-" : ""; //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static PackExt getPackExt(String endsWithExtension) {
		for (PackExt ext : PackExt.values()) {
			if (endsWithExtension.endsWith(ext.getExtension())) {
				return ext;
			}
		}
		throw new IllegalArgumentException(
				"Unrecognized Pack extension: " + endsWithExtension); //$NON-NLS-1$
	}
}
