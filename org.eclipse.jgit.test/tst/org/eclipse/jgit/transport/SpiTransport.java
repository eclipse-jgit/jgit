package org.eclipse.jgit.transport;

import java.util.Collections;
import java.util.Set;

import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.lib.Repository;

/**
 * Transport protocol contributed via service provider
 */
public class SpiTransport extends Transport {

	/**
	 * Transport protocol scheme
	 */
	public static final String SCHEME = "testspi";

	/**
	 * Instance
	 */
	public static final TransportProtocol PROTO = new TransportProtocol() {

		public String getName() {
			return "Test SPI Transport Protocol";
		}

		public Set<String> getSchemes() {
			return Collections.singleton(SCHEME);
		}

		public Transport open(URIish uri, Repository local, String remoteName)
				throws NotSupportedException, TransportException {
			throw new NotSupportedException("not supported");
		}
	};

	/**
	 * @param local
	 * @param uri
	 */
	protected SpiTransport(Repository local, URIish uri) {
		super(local, uri);
	}

	public FetchConnection openFetch() throws NotSupportedException,
			TransportException {
		throw new NotSupportedException("not supported");
	}

	public PushConnection openPush() throws NotSupportedException,
			TransportException {
		throw new NotSupportedException("not supported");
	}

	public void close() {
		// Intentionally left blank
	}
}
