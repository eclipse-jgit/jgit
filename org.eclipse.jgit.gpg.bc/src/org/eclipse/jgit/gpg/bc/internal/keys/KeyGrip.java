/*
 * Copyright (C) 2021, Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.gpg.bc.internal.keys;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Arrays;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.x9.ECNamedCurveTable;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.bcpg.DSAPublicBCPGKey;
import org.bouncycastle.bcpg.ECPublicBCPGKey;
import org.bouncycastle.bcpg.ElGamalPublicBCPGKey;
import org.bouncycastle.bcpg.PublicKeyAlgorithmTags;
import org.bouncycastle.bcpg.RSAPublicBCPGKey;
import org.bouncycastle.crypto.ec.CustomNamedCurves;
import org.bouncycastle.math.ec.ECAlgorithms;
import org.bouncycastle.math.field.FiniteField;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.util.encoders.Hex;
import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.gpg.bc.internal.BCText;
import org.eclipse.jgit.util.sha1.SHA1;

/**
 * Utilities to compute the <em>keygrip</em> of a key. A keygrip is a SHA1 hash
 * over the public key parameters and is used internally by the gpg-agent to
 * find the secret key belonging to a public key: the secret key is stored in a
 * file under ~/.gnupg/private-keys-v1.d/ with a name "&lt;keygrip&gt;.key".
 * While this storage organization is an implementation detail of GPG, the way
 * keygrips are computed is not; they are computed by libgcrypt and their
 * definition is stable.
 */
public final class KeyGrip {

	// Some OIDs apparently unknown to BouncyCastle.

	private static String OID_OPENPGP_ED25519 = "1.3.6.1.4.1.11591.15.1"; //$NON-NLS-1$

	private static String OID_RFC8410_CURVE25519 = "1.3.101.110"; //$NON-NLS-1$

	private static String OID_RFC8410_ED25519 = "1.3.101.112"; //$NON-NLS-1$

	private static ASN1ObjectIdentifier CURVE25519 = ECNamedCurveTable
			.getOID("curve25519"); //$NON-NLS-1$

	private KeyGrip() {
		// No instantiation
	}

	/**
	 * Computes the keygrip for a {@link PGPPublicKey}.
	 *
	 * @param publicKey
	 *            to get the keygrip of
	 * @return the keygrip
	 * @throws PGPException
	 *             if an unknown key type is encountered.
	 */
	@NonNull
	public static byte[] getKeyGrip(PGPPublicKey publicKey)
			throws PGPException {
		SHA1 grip = SHA1.newInstance();
		grip.setDetectCollision(false);

		switch (publicKey.getAlgorithm()) {
		case PublicKeyAlgorithmTags.RSA_GENERAL:
		case PublicKeyAlgorithmTags.RSA_ENCRYPT:
		case PublicKeyAlgorithmTags.RSA_SIGN:
			BigInteger modulus = ((RSAPublicBCPGKey) publicKey
					.getPublicKeyPacket().getKey()).getModulus();
			hash(grip, modulus.toByteArray());
			break;
		case PublicKeyAlgorithmTags.DSA:
			DSAPublicBCPGKey dsa = (DSAPublicBCPGKey) publicKey
					.getPublicKeyPacket().getKey();
			hash(grip, dsa.getP().toByteArray(), 'p', true);
			hash(grip, dsa.getQ().toByteArray(), 'q', true);
			hash(grip, dsa.getG().toByteArray(), 'g', true);
			hash(grip, dsa.getY().toByteArray(), 'y', true);
			break;
		case PublicKeyAlgorithmTags.ELGAMAL_GENERAL:
		case PublicKeyAlgorithmTags.ELGAMAL_ENCRYPT:
			ElGamalPublicBCPGKey eg = (ElGamalPublicBCPGKey) publicKey
					.getPublicKeyPacket().getKey();
			hash(grip, eg.getP().toByteArray(), 'p', true);
			hash(grip, eg.getG().toByteArray(), 'g', true);
			hash(grip, eg.getY().toByteArray(), 'y', true);
			break;
		case PublicKeyAlgorithmTags.ECDH:
		case PublicKeyAlgorithmTags.ECDSA:
		case PublicKeyAlgorithmTags.EDDSA:
			ECPublicBCPGKey ec = (ECPublicBCPGKey) publicKey
					.getPublicKeyPacket().getKey();
			ASN1ObjectIdentifier curveOID = ec.getCurveOID();
			// BC doesn't know these OIDs.
			if (OID_OPENPGP_ED25519.equals(curveOID.getId())
					|| OID_RFC8410_ED25519.equals(curveOID.getId())) {
				return hashEd25519(grip, ec.getEncodedPoint());
			} else if (CURVE25519.equals(curveOID)
					|| OID_RFC8410_CURVE25519.equals(curveOID.getId())) {
				// curvey25519 actually is the OpenPGP OID for Curve25519 and is
				// known to BC, but the parameters are for the short Weierstrass
				// form. See https://github.com/bcgit/bc-java/issues/399 .
				// libgcrypt uses Montgomery form.
				return hashCurve25519(grip, ec.getEncodedPoint());
			}
			X9ECParameters params = getX9Parameters(curveOID);
			if (params == null) {
				throw new PGPException(MessageFormat
						.format(BCText.get().unknownCurve, curveOID.getId()));
			}
			// Need to write p, a, b, g, n, q
			BigInteger q = ec.getEncodedPoint();
			byte[] g = params.getG().getEncoded(false);
			BigInteger a = params.getCurve().getA().toBigInteger();
			BigInteger b = params.getCurve().getB().toBigInteger();
			BigInteger n = params.getN();
			BigInteger p = null;
			FiniteField field = params.getCurve().getField();
			if (ECAlgorithms.isFpField(field)) {
				p = field.getCharacteristic();
			}
			if (p == null) {
				// Don't know...
				throw new PGPException(MessageFormat.format(
						BCText.get().unknownCurveParameters, curveOID.getId()));
			}
			hash(grip, p.toByteArray(), 'p', false);
			hash(grip, a.toByteArray(), 'a', false);
			hash(grip, b.toByteArray(), 'b', false);
			hash(grip, g, 'g', false);
			hash(grip, n.toByteArray(), 'n', false);
			if (publicKey.getAlgorithm() == PublicKeyAlgorithmTags.EDDSA) {
				hashQ25519(grip, q);
			} else {
				hash(grip, q.toByteArray(), 'q', false);
			}
			break;
		default:
			throw new PGPException(
					MessageFormat.format(BCText.get().unknownKeyType,
							Integer.toString(publicKey.getAlgorithm())));
		}
		return grip.digest();
	}

	private static void hash(SHA1 grip, byte[] data) {
		// Need to skip leading zero bytes
		int i = 0;
		while (i < data.length && data[i] == 0) {
			i++;
		}
		int length = data.length - i;
		if (i < data.length) {
			if ((data[i] & 0x80) != 0) {
				grip.update((byte) 0);
			}
			grip.update(data, i, length);
		}
	}

	private static void hash(SHA1 grip, byte[] data, char id, boolean zeroPad) {
		// Need to skip leading zero bytes
		int i = 0;
		while (i < data.length && data[i] == 0) {
			i++;
		}
		int length = data.length - i;
		boolean addZero = false;
		if (i < data.length && zeroPad && (data[i] & 0x80) != 0) {
			addZero = true;
		}
		// libgcrypt includes an SExp in the hash
		String prefix = "(1:" + id + (addZero ? length + 1 : length) + ':'; //$NON-NLS-1$
		grip.update(prefix.getBytes(StandardCharsets.US_ASCII));
		// For some items, gcrypt prepends a zero byte if the high bit is set
		if (addZero) {
			grip.update((byte) 0);
		}
		if (i < data.length) {
			grip.update(data, i, length);
		}
		grip.update((byte) ')');
	}

	private static void hashQ25519(SHA1 grip, BigInteger q)
			throws PGPException {
		byte[] data = q.toByteArray();
		switch (data[0]) {
		case 0x04:
			if (data.length != 65) {
				throw new PGPException(MessageFormat.format(
						BCText.get().corrupt25519Key, Hex.toHexString(data)));
			}
			// Uncompressed: should not occur with ed25519 or curve25519
			throw new PGPException(MessageFormat.format(
					BCText.get().uncompressed25519Key, Hex.toHexString(data)));
		case 0x40:
			if (data.length != 33) {
				throw new PGPException(MessageFormat.format(
						BCText.get().corrupt25519Key, Hex.toHexString(data)));
			}
			// Compressed; normal case. Skip prefix.
			hash(grip, Arrays.copyOfRange(data, 1, data.length), 'q', false);
			break;
		default:
			if (data.length != 32) {
				throw new PGPException(MessageFormat.format(
						BCText.get().corrupt25519Key, Hex.toHexString(data)));
			}
			// Compressed format without prefix. Should not occur?
			hash(grip, data, 'q', false);
			break;
		}
	}

	/**
	 * Computes the keygrip for an ed25519 public key.
	 * <p>
	 * Package-visible for tests only.
	 * </p>
	 *
	 * @param grip
	 *            initialized {@link SHA1}
	 * @param q
	 *            the public key's EC point
	 * @return the keygrip
	 * @throws PGPException
	 *             if q indicates uncompressed format
	 */
	@SuppressWarnings("nls")
	static byte[] hashEd25519(SHA1 grip, BigInteger q) throws PGPException {
		// For the values, see RFC 7748: https://tools.ietf.org/html/rfc7748
		// p = 2^255 - 19
		hash(grip, Hex.decodeStrict(
				"7FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFED"),
				'p', false);
		// Field: a = 1
		hash(grip, new byte[] { 0x01 }, 'a', false);
		// Field: b = 121665/121666 (mod p)
		// See Berstein et.al., "Twisted Edwards Curves",
		// https://doi.org/10.1007/978-3-540-68164-9_26
		hash(grip, Hex.decodeStrict(
				"2DFC9311D490018C7338BF8688861767FF8FF5B2BEBE27548A14B235ECA6874A"),
				'b', false);
		// Generator point with affine X,Y
		// @formatter:off
		// X(P) = 15112221349535400772501151409588531511454012693041857206046113283949847762202
		// Y(P) = 46316835694926478169428394003475163141307993866256225615783033603165251855960
		// the "04" signifies uncompressed format.
		// @formatter:on
		hash(grip, Hex.decodeStrict("04"
				+ "216936D3CD6E53FEC0A4E231FDD6DC5C692CC7609525A7B2C9562D608F25D51A"
				+ "6666666666666666666666666666666666666666666666666666666666666658"),
				'g', false);
		// order = 2^252 + 0x14def9dea2f79cd65812631a5cf5d3ed
		hash(grip, Hex.decodeStrict(
				"1000000000000000000000000000000014DEF9DEA2F79CD65812631A5CF5D3ED"),
				'n', false);
		hashQ25519(grip, q);
		return grip.digest();
	}

	/**
	 * Computes the keygrip for a curve25519 public key.
	 * <p>
	 * Package-visible for tests only.
	 * </p>
	 *
	 * @param grip
	 *            initialized {@link SHA1}
	 * @param q
	 *            the public key's EC point
	 * @return the keygrip
	 * @throws PGPException
	 *             if q indicates uncompressed format
	 */
	@SuppressWarnings("nls")
	static byte[] hashCurve25519(SHA1 grip, BigInteger q) throws PGPException {
		hash(grip, Hex.decodeStrict(
				"7FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFED"),
				'p', false);
		// Unclear: RFC 7748 says A = 486662. This value here is (A-2)/4 =
		// 121665. Compare ecc-curves.c in libgcrypt:
		// https://github.com/gpg/libgcrypt/blob/361a058/cipher/ecc-curves.c#L146
		hash(grip, new byte[] { 0x01, (byte) 0xDB, 0x41 }, 'a', false);
		hash(grip, new byte[] { 0x01 }, 'b', false);
		// libgcrypt uses the old g.y value before the erratum to RFC 7748 for
		// the keygrip. The new value would be
		// 5F51E65E475F794B1FE122D388B72EB36DC2B28192839E4DD6163A5D81312C14. See
		// https://www.rfc-editor.org/errata/eid4730 and
		// https://github.com/gpg/libgcrypt/commit/f67b6492e0b0
		hash(grip, Hex.decodeStrict("04"
				+ "0000000000000000000000000000000000000000000000000000000000000009"
				+ "20AE19A1B8A086B4E01EDD2C7748D14C923D4D7E6D7C61B229E9C5A27ECED3D9"),
				'g', false);
		hash(grip, Hex.decodeStrict(
				"1000000000000000000000000000000014DEF9DEA2F79CD65812631A5CF5D3ED"),
				'n', false);
		hashQ25519(grip, q);
		return grip.digest();
	}

	private static X9ECParameters getX9Parameters(
			ASN1ObjectIdentifier curveOID) {
		X9ECParameters params = CustomNamedCurves.getByOID(curveOID);
		if (params == null) {
			params = ECNamedCurveTable.getByOID(curveOID);
		}
		return params;
	}

}
