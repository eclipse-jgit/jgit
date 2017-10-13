/*
 * Copyright (C) 2017, Two Sigma Open Source
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
package org.eclipse.jgit.api;

/**
 * The result of a submodule deinit command for a particular path
 *
 * @since 4.10
 */
public class SubmoduleDeinitResult {
	private String path;

	private SubmoduleDeinitCommand.SubmoduleDeinitStatus status;

	/**
	 * Constructor for SubmoduleDeinitResult
	 *
	 * @param path
	 *            path of the submodule
	 * @param status
	 */
	public SubmoduleDeinitResult(String path,
			SubmoduleDeinitCommand.SubmoduleDeinitStatus status) {
		this.path = path;
		this.status = status;
	}

	/**
	 * Get the path of the submodule
	 *
	 * @return path of the submodule
	 */
	public String getPath() {
		return path;
	}

	/**
	 * Set the path of the submodule
	 *
	 * @param path
	 *            path of the submodule
	 */
	public void setPath(String path) {
		this.path = path;
	}

	/**
	 * Get the status of the command
	 *
	 * @return the status of the command
	 */
	public SubmoduleDeinitCommand.SubmoduleDeinitStatus getStatus() {
		return status;
	}

	/**
	 * Set the status of the command
	 *
	 * @param status
	 *            the status of the command
	 */
	public void setStatus(SubmoduleDeinitCommand.SubmoduleDeinitStatus status) {
		this.status = status;
	}
}
