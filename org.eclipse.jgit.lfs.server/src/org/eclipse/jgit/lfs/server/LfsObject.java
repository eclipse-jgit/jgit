/*
 * Copyright (C) 2015, Sasa Zivkov <sasa.zivkov@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.lfs.server;

/**
 * LFS object.
 *
 * @since 4.5
 */
public class LfsObject {
	String oid;
	long size;

	/**
	 * Get the <code>oid</code> of this object.
	 *
	 * @return the object ID.
	 */
	public String getOid() {
		return oid;
	}

	/**
	 * Get the <code>size</code> of this object.
	 *
	 * @return the object size.
	 */
	public long getSize() {
		return size;
	}
}
