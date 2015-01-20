/**
 *
 */
package org.eclipse.jgit.transport;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.eclipse.jgit.transport.NonceGenerator;

/**
 * The nonce generator which was first introduced to git-core.
 */
public class HMACSHA1NonceGenerator implements NonceGenerator {

	public String createNonce(String seed, String path, long secs)
			throws IllegalStateException {
		String key = path + ":" + String.valueOf(secs); //$NON-NLS-1$

		byte[] keyBytes = key.getBytes();
		SecretKeySpec signingKey = new SecretKeySpec(keyBytes, "HmacSHA1"); //$NON-NLS-1$

		Mac mac = null;
		try {
			mac = Mac.getInstance("HmacSHA1"); //$NON-NLS-1$
			mac.init(signingKey);
		} catch (InvalidKeyException e) {
			throw new IllegalStateException(e);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
		byte[] rawHmac = mac.doFinal(seed.getBytes());
		String hexString = String.format("%20X", rawHmac); //$NON-NLS-1$
		return hexString;
	}
}
