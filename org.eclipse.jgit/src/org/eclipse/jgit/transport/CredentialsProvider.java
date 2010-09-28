package org.eclipse.jgit.transport;

/**
 * Implementors can provide credentials for use in connecting to Git
 * repositories.
 */
public abstract class CredentialsProvider {
	/**
	 * Get credentials for a given URI
	 *
	 * @param uri
	 *            the URI for which credentials are desired
	 *
	 * @return the credentials, or null if there are none.
	 * @see UsernamePasswordCredentials
	 */
	public abstract Credentials getCredentials(URIish uri);
}
