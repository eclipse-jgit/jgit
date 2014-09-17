package org.eclipse.jgit.transport;

import java.util.LinkedList;

import org.eclipse.jgit.errors.UnsupportedCredentialItem;

/**
 * A credentials provider chaining multiple credentials providers
 *
 * @since 3.5
 */
public class ChainingCredentialsProvider extends CredentialsProvider {

	private LinkedList<CredentialsProvider> credentialProviders;

	/**
	 * Create a new chaining credential provider. This provider tries to
	 * retrieve credentials from the chained credential providers in the order
	 * they are given here. If multiple providers support the requested items
	 * and have non-null credentials the first of them will be used.
	 *
	 * @param providers
	 *            credential providers asked for credentials in the order given
	 *            here
	 */
	public ChainingCredentialsProvider(CredentialsProvider... providers) {
		this.credentialProviders = new LinkedList<CredentialsProvider>();
		for (CredentialsProvider p : providers)
			credentialProviders.add(p);
	}

	/**
	 * @return {@code true} if any of the credential providers in the list is
	 *         interactive, otherwise {@code false}
	 * @see org.eclipse.jgit.transport.CredentialsProvider#isInteractive()
	 */
	@Override
	public boolean isInteractive() {
		for (CredentialsProvider p : credentialProviders)
			if (p.isInteractive())
				return true;
		return false;
	}

	/**
	 * @return {@code true} if any of the credential providers in the list
	 *         supports the requested items, otherwise {@code false}
	 * @see org.eclipse.jgit.transport.CredentialsProvider#supports(org.eclipse.jgit.transport.CredentialItem[])
	 */
	@Override
	public boolean supports(CredentialItem... items) {
		for (CredentialsProvider p : credentialProviders)
			if (p.supports(items))
				return true;
		return false;
	}

	/**
	 * Populates the credential items with the credentials provided by the first
	 * credential provider in the list which populates them with non-null values
	 *
	 * @return {@code true} if any of the credential providers in the list
	 *         supports the requested items, otherwise {@code false}
	 * @see org.eclipse.jgit.transport.CredentialsProvider#supports(org.eclipse.jgit.transport.CredentialItem[])
	 */
	@Override
	public boolean get(URIish uri, CredentialItem... items)
			throws UnsupportedCredentialItem {
		for (CredentialsProvider p : credentialProviders) {
			if (p.supports(items)) {
				p.get(uri, items);
				if (isAnyNull(items))
					continue;
				return true;
			}
		}
		return false;
	}

	private boolean isAnyNull(CredentialItem... items) {
		for (CredentialItem i : items)
			if (i == null)
				return true;
		return false;
	}
}
