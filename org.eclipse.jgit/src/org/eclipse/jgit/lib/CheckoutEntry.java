package org.eclipse.jgit.lib;

/**
 * Parsed information about a checkout.
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