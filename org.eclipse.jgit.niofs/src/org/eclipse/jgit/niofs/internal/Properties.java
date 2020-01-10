/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.internal;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 *
 */
public class Properties extends HashMap<String, Object> {

	public Properties() {
	}

	public Properties(final Map<String, Object> original) {
		for (Entry<String, Object> e : original.entrySet()) {
			if (e.getValue() != null) {
				put(e.getKey(), e.getValue());
			}
		}
	}

	public Object put(final String key, final Object value) {
		if (value == null) {
			return remove(key);
		}
		return super.put(key, value);
	}

	public void store(final OutputStream out) {
		store(out, true);
	}

	public void store(final OutputStream out, boolean closeOnFinish) {
	}

	public void load(final InputStream in) {
		load(in, true);
	}

	public void load(final InputStream in, boolean closeOnFinish) {
	}
}
