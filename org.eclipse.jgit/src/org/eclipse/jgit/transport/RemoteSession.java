package org.eclipse.jgit.transport;

import java.io.IOException;

/**
 * Create a remote "session" for executing remote commands.
 * <p>
 * Clients should subclass RemoteSession to create an alternate way for JGit to
 * execute remote commands. (The client application may already have this
 * functionality available.) Note that this class is just a factory for creating
 * remote processes. If the application already has a persistent connection to
 * the remote machine, RemoteSession may do nothing more than return a new
 * RemoteProcess when exec is called.
 */
public interface RemoteSession {
	/**
	 * Generate a new remote process to execute the given command. This function
	 * should also start execution and may need to create the streams prior to
	 * execution.
	 *
	 * @param commandName
	 *            command to execute
	 * @param timeout
	 *            timeout value, in seconds, for command execution
	 * @return a new remote process
	 * @throws IOException
	 *             may be thrown in several cases. For example, on problems
	 *             opening input or output streams or on problems connecting or
	 *             communicating with the remote host. For the latter two cases,
	 *             a TransportException may be thrown (a subclass of
	 *             IOException).
	 */
	public Process exec(String commandName, int timeout) throws IOException;

	/**
	 * Disconnect the remote session
	 */
	public void disconnect();
}