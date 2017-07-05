package org.eclipse.jgit.lib;

/**
 * Parsed information about a checkout.
 *
 * @since 3.0
 */
public interface CheckoutEntry {

	/**
	 * @return the name of the branch before checkout
	 */
	public abstract String getFromBranch();

	/**
	 * @return the name of the branch after checkout
	 */
	public abstract String getToBranch();

}
