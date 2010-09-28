package org.eclipse.jgit.transport;

/**
 * a credentials object that consists of a username and password
 *
 * @see CredentialsProvider
 */
public class UsernamePasswordCredentials extends Credentials {
	private String username;

	private String password;

	/**
	 * the username that identifies a user
	 *
	 * @return the username, or null if it is not known
	 */
	public String getUsername() {
		return username;
	}

	/**
	 * the username that identifies a user
	 *
	 * @param username
	 *            the username, or null if it is not known
	 */
	public void setUsername(String username) {
		this.username = username;
	}

	/**
	 * the password, which is a shared secret between the user and a service
	 *
	 * @return the password, or null if it is not known
	 */
	public String getPassword() {
		return password;
	}

	/**
	 * the password, which is a shared secret between the user and a service
	 *
	 * @param password
	 *            the password, or null if it is not known
	 */
	public void setPassword(String password) {
		this.password = password;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((password == null) ? 0 : password.hashCode());
		result = prime * result
				+ ((username == null) ? 0 : username.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		UsernamePasswordCredentials other = (UsernamePasswordCredentials) obj;
		if (password == null) {
			if (other.password != null)
				return false;
		} else if (!password.equals(other.password))
			return false;
		if (username == null) {
			if (other.username != null)
				return false;
		} else if (!username.equals(other.username))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return String.format(
				"UsernamePasswordCredentials [username=%s, password=%s]",
				username, password == null ? null : "PROTECTED");
	}

}
