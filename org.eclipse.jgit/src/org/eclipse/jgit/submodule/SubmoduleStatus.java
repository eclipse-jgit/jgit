/*
 * Copyright (C) 2011, GitHub Inc.
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
package org.eclipse.jgit.submodule;

import org.eclipse.jgit.lib.ObjectId;

/**
 * Status class containing the type, path, and commit id of the submodule.
 */
public class SubmoduleStatus {

	private final SubmoduleStatusType type;

	private final String path;

	private final ObjectId id;

	/**
	 * Create submodule status
	 *
	 * @param type
	 * @param path
	 * @param id
	 */
	public SubmoduleStatus(SubmoduleStatusType type, String path, ObjectId id) {
		this.type = type;
		this.path = path;
		this.id = id;
	}

	/**
	 * @return type
	 */
	public SubmoduleStatusType getType() {
		return type;
	}

	/**
	 * @return path
	 */
	public String getPath() {
		return path;
	}

	/**
	 * @return id
	 */
	public ObjectId getId() {
		return id;
	}

	public String toString() {
		StringBuilder buffer = new StringBuilder();
		if (type == SubmoduleStatusType.UNINITIALIZED)
			buffer.append('-');
		else if (type == SubmoduleStatusType.REV_CHECKED_OUT)
			buffer.append('+');
		else
			buffer.append(' ');
		buffer.append(id.name());
		buffer.append(' ');
		buffer.append(path);
		if (type == SubmoduleStatusType.MISSING)
			buffer.append(" (missing)");
		return buffer.toString();
	}
}
