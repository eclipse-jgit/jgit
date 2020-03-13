/*
 * Copyright (C) 2018, Salesforce. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.lib;

import java.util.Iterator;
import java.util.ServiceLoader;
import org.eclipse.jgit.lib.internal.NoopGpgSigner;

/**
 *
 * @author Emmanuel Hugonnet (c) 2019 Red Hat, Inc.
 */
public class GpgSignerFactory {

	private static GpgSigner defaultSigner;

	/**
	 * Get the default signer, or <code>null</code>.
	 *
	 * @return the default signer, or <code>null</code>.
	 */
	public static GpgSigner getGpgSigner() {
		if(defaultSigner == null) {
			defaultSigner = loadDefaultGpgSigner();
		}
		return defaultSigner;
	}

	/**
	 * Set the default signer.
	 *
	 * @param signer
	 *            the new default signer, may be <code>null</code> to select no
	 *            default.
	 */
	public static void setDefault(GpgSigner signer) {
		defaultSigner = signer;
	}

	private static GpgSigner loadDefaultGpgSigner() {
		ServiceLoader<GpgSigner> loader = ServiceLoader.load(GpgSigner.class);
		Iterator<GpgSigner> iter = loader.iterator();
		if(iter.hasNext()) {
			return iter.next();
		}
		return new NoopGpgSigner();
	}
}
