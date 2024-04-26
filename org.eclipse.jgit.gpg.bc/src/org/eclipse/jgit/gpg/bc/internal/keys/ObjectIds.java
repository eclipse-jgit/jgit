/*
 * Copyright (C) 2024, Thomas Wolf <twolf@apache.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.gpg.bc.internal.keys;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.x9.ECNamedCurveTable;

/**
 * Some OIDs apparently unknown to Bouncy Castle.
 */
public final class ObjectIds {

	/**
	 * Legacy OID for ed25519 used in OpenPGP.
	 */
	public static final ASN1ObjectIdentifier OID_OPENPGP_ED25519 =
			new ASN1ObjectIdentifier("1.3.6.1.4.1.11591.15.1"); //$NON-NLS-1$

	/**
	 * OID for ed25519 according to RFC 8410.
	 */
	public static final ASN1ObjectIdentifier OID_RFC8410_ED25519 =
			new ASN1ObjectIdentifier("1.3.101.112"); //$NON-NLS-1$

	/**
	 * Legacy OID for curve25519 used in OpenPGP.
	 */
	public static final ASN1ObjectIdentifier OID_OPENPGP_CURVE25519 =
			ECNamedCurveTable.getOID("curve25519"); //$NON-NLS-1$
	// This is 1.3.6.1.4.1.3029.1.5.1

	/**
	 * OID for curve25519 according to RFC 8410.
	 */
	public static final ASN1ObjectIdentifier OID_RFC8410_CURVE25519 = new ASN1ObjectIdentifier(
			"1.3.101.110"); //$NON-NLS-1$

	/**
	 * Determines whether the given {@code oid} denoted ed25519.
	 *
	 * @param oid
	 *            to test
	 * @return {@code true} if it is ed25519, {@code false} otherwise
	 */
	public static boolean isEd25519(ASN1ObjectIdentifier oid) {
		return OID_OPENPGP_ED25519.equals(oid)
				|| OID_RFC8410_ED25519.equals(oid);
	}

	/**
	 * Determines whether the given {@code oid} denoted curve25519.
	 *
	 * @param oid
	 *            to test
	 * @return {@code true} if it is curve25519, {@code false} otherwise
	 */
	public static boolean isCurve25519(ASN1ObjectIdentifier oid) {
		return OID_RFC8410_CURVE25519.equals(oid)
				|| OID_OPENPGP_CURVE25519.equals(oid);
	}

	/**
	 * Retrieves an OID by name.
	 *
	 * @param name
	 *            to get the OID for
	 * @return the OID, or {@code null} if unknown.
	 */
	public static ASN1ObjectIdentifier getByName(String name) {
		if (name != null) {
			switch (name) {
			case "Ed25519": //$NON-NLS-1$
				return OID_OPENPGP_ED25519;
			case "Curve25519": //$NON-NLS-1$
				return OID_OPENPGP_CURVE25519;
			default:
				break;
			}
		}
		return null;
	}

	/**
	 * Checks whether two OIDs denote the same algorithm.
	 *
	 * @param oid1
	 *            First OID to test
	 * @param oid2
	 *            Second OID to test
	 * @return {@code true} if the two OIDs match, {@code false} otherwise
	 */
	public static boolean match(ASN1ObjectIdentifier oid1,
			ASN1ObjectIdentifier oid2) {
		if (isEd25519(oid1)) {
			return isEd25519(oid2);
		}
		if (isCurve25519(oid1)) {
			return isCurve25519(oid2);
		}
		return oid1 != null && oid1.equals(oid2);
	}
}
