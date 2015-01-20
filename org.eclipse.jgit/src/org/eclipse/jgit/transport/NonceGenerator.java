/**
 *
 */
package org.eclipse.jgit.transport;

/**
 * A NonceGenerator is used to crate a nonce to be sent out to the pusher who
 * will sign the nonce to prove it is not a replay attack on the push
 * certificate.
 */
public interface NonceGenerator {
	/**
	 * @param seed
	 *            The seed for the server which must be kept private.
	 * @param prefix
	 *            The directoy path on the serving side for this repository
	 *            according to the documentation in git-core. Though any unique
	 *            identifier for the repository is fine.
	 * @param time
	 *            Seconds since the epoch
	 * @return The nonce to be signed by the pusher
	 * @throws IllegalStateException
	 */
	public String createNonce(String seed, String prefix, long time)
			throws IllegalStateException;
}
