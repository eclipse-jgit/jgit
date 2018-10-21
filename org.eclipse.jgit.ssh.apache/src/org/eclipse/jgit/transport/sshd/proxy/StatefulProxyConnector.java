package org.eclipse.jgit.transport.sshd.proxy;

import java.util.concurrent.Callable;

import org.apache.sshd.client.session.ClientProxyConnector;
import org.apache.sshd.common.io.IoSession;
import org.apache.sshd.common.util.Readable;

/**
 * Some proxy connections are stateful and require the exchange of multiple
 * request-reply messages. The default {@link ClientProxyConnector} has only
 * support for sending a message; replies get routed through the Ssh session,
 * and don't get back to this proxy connector. Augment the interface so that the
 * session can know when to route messages received to the proxy connector, and
 * when to start handling them itself.
 */
public interface StatefulProxyConnector extends ClientProxyConnector {

	/**
	 * A property key for a session property defining the timeout for setting up
	 * the proxy connection.
	 */
	static final String TIMEOUT_PROPERTY = StatefulProxyConnector.class
			.getName() + "-timeout"; //$NON-NLS-1$

	/**
	 * Handle a received message.
	 *
	 * @param session
	 *            to use for writing data
	 * @param buffer
	 *            received data
	 * @throws Exception
	 *             if data cannot be read, or the connection attempt fails
	 */
	void messageReceived(IoSession session, Readable buffer) throws Exception;

	/**
	 * Runs {@code startSsh} once the proxy connection is established.
	 *
	 * @param startSsh
	 *            operation to run
	 * @throws Exception
	 *             if the operation is run synchronously and throws an exception
	 */
	void runWhenDone(Callable<Void> startSsh) throws Exception;
}
