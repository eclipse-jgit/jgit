/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.internal.hook;

import java.util.HashMap;
import java.util.Map;

/**
 * Execution context for a {@link FileSystemHooks.FileSystemHook}. It contains
 * the relevant information for the Hook execution
 */
public class FileSystemHookExecutionContext {

	private String fsName;

	private Map<String, Object> params = new HashMap<>();

	public FileSystemHookExecutionContext(String fsName) {
		this.fsName = fsName;
	}

	/**
	 * Returns the fileSystem name that this hook executes
	 */
	public String getFsName() {
		return fsName;
	}

	/**
	 * Adds a param to the context
	 * 
	 * @param name  the name of the param
	 * @param value
	 */
	public void addParam(String name, Object value) {
		params.put(name, value);
	}

	/**
	 * Gets a param value
	 * 
	 * @param name the name of the param
	 * @return the param value
	 */
	public Object getParamValue(String name) {
		return params.get(name);
	}
}
