/*
 * Copyright (C) 2010, Google, Inc.
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

package org.eclipse.jgit.util.fs;

/** A directory entry as read from the filesystem. */
public class DirEnt {
	// Do not change the values of the TYPE_ constants, as these
	// must match the native code that translates from OS specific
	// values to the portable Java values declared here.

	/** The type is unknown, a stat must be performed. */
	public static final int TYPE_UNKNOWN = 0;

	/** The entry is a subdirectory. */
	public static final int TYPE_DIRECTORY = 1;

	/** The entry is a file. */
	public static final int TYPE_FILE = 2;

	/** The entry is a symbolic link. */
	public static final int TYPE_SYMLINK = 3;

	/** The entry is a special operating system type, not one of the above. */
	public static final int TYPE_SPECIAL = 127;

	private String name;

	private int type;

	/**
	 * Create an entry with an unknown type.
	 *
	 * @param name
	 *            name of the entry in the parent directory.
	 */
	public DirEnt(String name) {
		this(name, TYPE_UNKNOWN);
	}

	/**
	 * Create an entry with a specific type.
	 *
	 * @param name
	 *            name of the entry in the parent directory.
	 * @param type
	 *            type of the entry, using one of the type constants declared in
	 *            this class.
	 */
	public DirEnt(String name, int type) {
		this.name = name;
		this.type = type;
	}

	/** @return name of this directory entry. */
	public String getName() {
		return name;
	}

	/** @return a {@code TYPE_*} constant declared by this class. */
	public int getType() {
		return type;
	}

	@Override
	public String toString() {
		return getName();
	}
}
