/*
 * Copyright (C) 2017, Two Sigma Open Source and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
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
