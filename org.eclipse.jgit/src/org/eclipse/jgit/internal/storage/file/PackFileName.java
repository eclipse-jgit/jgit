/*
 * Copyright (c) 2018, The Linux Foundation
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.eclipse.jgit.internal.storage.file;

import java.io.File;

import org.eclipse.jgit.internal.storage.pack.PackExt;

/**
 * A pack file (or pack related) File.
 *
 * Example: "pack-0123456789012345678901234567890123456789.idx"
 */
class PackFileName extends File {
	private static final long serialVersionUID = 1L;
	private static final String PREFIX = "pack-"; //$NON-NLS-1$

	private final String base; // PREFIX + id i.e. pack-0123456789012345678901234567890123456789
	private final String id; // i.e. 0123456789012345678901234567890123456789
	private final boolean isOld;
	private final PackExt packExt;

	/**
	 * Create a PackFileName for a pack or related file.
	 *
	 * @param file
	 *            File pointing to the location of the file.
	 */
	PackFileName(File file) {
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
	PackFileName(File directory, String name) {
		super(directory, name);
		int dot = name.lastIndexOf('.');

		if (dot < 0) {
			base = name;
			isOld = false;
			packExt = null;
		} else {
			base = name.substring(0, dot);
			String tail = name.substring(dot + 1); // ["old-"] + extension
			packExt = getPackExt(tail);
			String old = tail.substring(0, tail.length() - getExtension().length());
			isOld = old.equals(getOld(true));
		}

		id = base.startsWith(PREFIX) ? base.substring(PREFIX.length()) : base;
	}

	/**
	 * <p>Getter for the field <code>id</code>.</p>
	 *
	 * @return the <code>id</code> (40 Hex char) section of the name.
	 */
	String getId() {
		return id;
	}

	/**
	 * <p>Getter for the field <code>packExt</code>.</p>
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
	PackFileName create(PackExt ext) {
		return new PackFileName(getParentFile(), getName(ext));
	}

	/**
	 * Create a new similar PackFileName in the given directory.
	 *
	 * @param directory
	 *            Directory to create the new Name in.
	 * @param isPreserved
	 *            Whether the new PackFileName should have "old-" after the dot.
	 * @return a PackFileName in the given directory
	 */
	PackFileName createForDirectory(File directory, boolean isPreserved) {
		return new PackFileName(directory, getName(isPreserved));
	}

	private String getName(PackExt ext) {
		return base + '.' + getOld(isOld) + ext.getExtension();
	}

	private String getName(boolean isPreserved) {
		return base + '.' + getOld(isPreserved) + getExtension();
	}

	private String getExtension() {
		return packExt == null ? "" : packExt.getExtension(); //$NON-NLS-1$
	}

	private static String getOld(boolean isPreserved) {
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
