/*
 * Copyright (C) 2022, Matthias Sohn <matthias.sohn@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.util.sha1;

import java.security.MessageDigest;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.MutableObjectId;
import org.eclipse.jgit.lib.ObjectId;

/**
 * SHA1 implementation using native implementation from JDK. It doesn't support
 * collision detection but is faster than the pure Java implementation.
 */
class SHA1Native extends SHA1 {

	private final MessageDigest md;

	SHA1Native() {
		md = Constants.newMessageDigest();
	}

	@Override
	public void update(byte b) {
		md.update(b);
	}

	@Override
	public void update(byte[] in) {
		md.update(in);
	}

	@Override
	public void update(byte[] in, int p, int len) {
		md.update(in, p, len);
	}

	@Override
	public byte[] digest() throws Sha1CollisionException {
		return md.digest();
	}

	@Override
	public ObjectId toObjectId() throws Sha1CollisionException {
		return ObjectId.fromRaw(md.digest());
	}

	@Override
	public void digest(MutableObjectId id) throws Sha1CollisionException {
		id.fromRaw(md.digest());
	}

	@Override
	public SHA1 reset() {
		md.reset();
		return this;
	}

	@Override
	public SHA1 setDetectCollision(boolean detect) {
		return this;
	}

	@Override
	public boolean hasCollision() {
		return false;
	}
}
