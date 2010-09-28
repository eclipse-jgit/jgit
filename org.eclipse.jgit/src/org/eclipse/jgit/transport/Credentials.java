package org.eclipse.jgit.transport;

/**
 * credentials for establishing connections
 *
 * @see UsernamePasswordCredentials
 */
public abstract class Credentials {

	/**
	 * provide a reasonable representation of the credentials
	 */
	public abstract String toString();

	/**
	 * subclasses must implement meaningful hash code
	 */
	@Override
	public abstract int hashCode();

	/**
	 * subclasses must implement meaningful equality
	 */
	@Override
	public abstract boolean equals(Object obj);
}
