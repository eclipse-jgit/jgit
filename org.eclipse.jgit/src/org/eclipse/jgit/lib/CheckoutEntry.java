package org.eclipse.jgit.lib;

/**
 * Parsed information about a checkout.
 *
 * @since 3.0
 */
public interface CheckoutEntry {

	/**
	 * Get the name of the branch before checkout
	 *
	 * @return the name of the branch before checkout
	 */
	public abstract String getFromBranch();

	/**
	 * Get the name of the branch after checkout
	 *
	 * @return the name of the branch after checkout
	 */
	public abstract String getToBranch();

}
