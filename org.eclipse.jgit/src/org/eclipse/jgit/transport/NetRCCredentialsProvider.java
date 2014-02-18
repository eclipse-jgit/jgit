package org.eclipse.jgit.transport;

import org.eclipse.jgit.errors.UnsupportedCredentialItem;
import org.eclipse.jgit.transport.NetRC.NetRCEntry;

/**
 * Simple .netrc credentials provider. It can lookup for first maching entry
 * from your .netrc file.
 *
 * @author Alexey Kuznetsov <axet@me.com>
 *
 */
public class NetRCCredentialsProvider extends CredentialsProvider {

	NetRC netrc = new NetRC();

	/**
	 * Default constroctor
	 */
	public NetRCCredentialsProvider() {
	}

	/**
	 * install default provider for the .netrc parser.
	 */
	public static void install() {
		final NetRCCredentialsProvider c = new NetRCCredentialsProvider();
		CredentialsProvider.setDefault(c);
	}

	@Override
	public boolean supports(CredentialItem... items) {
		for (CredentialItem i : items) {
			if (i instanceof CredentialItem.Username)
				continue;
			else if (i instanceof CredentialItem.Password)
				continue;
			else
				return false;
		}
		return true;
	}

	@Override
	public boolean get(URIish uri, CredentialItem... items)
			throws UnsupportedCredentialItem {
		NetRCEntry cc = netrc.entry(uri.getHost());

		for (CredentialItem i : items) {
			if (i instanceof CredentialItem.Username) {
				((CredentialItem.Username) i).setValue(cc.login);
				continue;
			}
			if (i instanceof CredentialItem.Password) {
				((CredentialItem.Password) i).setValue(cc.password
						.toCharArray());
				continue;
			}
			if (i instanceof CredentialItem.StringType) {
				if (i.getPromptText().equals("Password: ")) { //$NON-NLS-1$
					((CredentialItem.StringType) i).setValue(new String(
							cc.password));
					continue;
				}
			}
			throw new UnsupportedCredentialItem(uri, i.getClass().getName()
					+ ":" + i.getPromptText()); //$NON-NLS-1$
		}
		return true;
	}

	@Override
	public boolean isInteractive() {
		return false;
	}

}
