package org.eclipse.jgit.lfs.internal;

import static org.eclipse.jgit.util.HttpSupport.HDR_ACCEPT;
import static org.eclipse.jgit.util.HttpSupport.HDR_CONTENT_TYPE;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ProxySelector;
import java.net.URL;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;

import org.eclipse.jgit.lfs.LfsPointer;
import org.eclipse.jgit.lfs.Protocol;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.HttpTransport;
import org.eclipse.jgit.transport.RemoteSession;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.http.HttpConnection;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.HttpSupport;
import org.eclipse.jgit.util.io.MessageWriter;
import org.eclipse.jgit.util.io.StreamCopyThread;

import com.google.gson.Gson;

/**
 * Provides means to get a valid LFS connection for a given repository.
 */
public class LfsConnectionHelper {

	/**
	 * Determine URL of LFS server by looking into config parameters lfs.url,
	 * lfs.<remote>.url or remote.<remote>.url. The LFS server URL is computed
	 * from remote.<remote>.url by appending "/info/lfs"
	 *
	 * @param db
	 *            the repository to work with
	 * @param method
	 *            the method (GET,PUT,...) of the request this connection will
	 *            be used for
	 * @param purpose
	 *            the action, e.g. Protocol.OPERATION_DOWNLOAD
	 * @return the url for the lfs server. e.g.
	 *         "https://github.com/github/git-lfs.git/info/lfs"
	 * @throws IOException
	 */
	public static HttpConnection getLfsConnection(Repository db, String method,
			String purpose)
			throws IOException {
		StoredConfig config = db.getConfig();
		String lfsEndpoint = config.getString("lfs", null, "url"); //$NON-NLS-1$ //$NON-NLS-2$
		Map<String, String> additionalHeaders = new TreeMap<>();
		if (lfsEndpoint == null) {
			String remoteUrl = null;
			for (String remote : db.getRemoteNames()) {
				lfsEndpoint = config.getString("lfs", remote, "url"); //$NON-NLS-1$ //$NON-NLS-2$
				// TODO: only works for origin?
				if (lfsEndpoint == null && (remote.equals("origin"))) { //$NON-NLS-1$
					remoteUrl = config.getString("remote", remote, "url"); //$NON-NLS-1$ //$NON-NLS-2$
				}
				break;
			}
			if (lfsEndpoint == null && remoteUrl != null) {
				try {
					URIish u = new URIish(remoteUrl);

					if ("ssh".equals(u.getScheme())) { //$NON-NLS-1$
						// discover and authenticate; git-lfs does "ssh -p
						// <port> -- <host> git-lfs-authenticate <project>
						// <upload/download>"
						String json = runSshCommand(u.setPath(""), db.getFS(), //$NON-NLS-1$
								"git-lfs-authenticate " + extractProjectName(u) //$NON-NLS-1$
										+ " " + purpose); //$NON-NLS-1$

						Protocol.Action action = new Gson().fromJson(json,
								Protocol.Action.class);
						additionalHeaders.putAll(action.header);
						lfsEndpoint = action.href;
					} else {
						lfsEndpoint = remoteUrl + "/info/lfs"; //$NON-NLS-1$
					}
				} catch (Exception e) {
					lfsEndpoint = null; // could not discover
				}
			} else {
				lfsEndpoint = lfsEndpoint + "/info/lfs"; //$NON-NLS-1$
			}
		}
		if (lfsEndpoint == null) {
			// TODO: throw specific exception
			throw new IOException(
					"need to download object from lfs server but couldn't determine lfs server url"); //$NON-NLS-1$
		}
		URL url = new URL(lfsEndpoint + "/objects/batch"); //$NON-NLS-1$
		HttpConnection connection = HttpTransport.getConnectionFactory().create(
				url, HttpSupport.proxyFor(ProxySelector.getDefault(), url));
		connection.setDoOutput(true);
		if (url.getProtocol().equals("https") //$NON-NLS-1$
				&& !config.getBoolean("http", "sslVerify", true)) { //$NON-NLS-1$ //$NON-NLS-2$
			HttpSupport.disableSslVerify(connection);
		}
		connection.setRequestMethod(method);
		connection.setRequestProperty(HDR_ACCEPT,
				Protocol.CONTENTTYPE_VND_GIT_LFS_JSON);
		connection.setRequestProperty(HDR_CONTENT_TYPE,
				Protocol.CONTENTTYPE_VND_GIT_LFS_JSON);
		additionalHeaders
				.forEach((k, v) -> connection.setRequestProperty(k, v));
		return connection;
	}

	private static String extractProjectName(URIish u) {
		String path = u.getPath().substring(1);
		if (path.endsWith(".git")) {
			return path.substring(0, path.length() - 4);
		} else {
			return path;
		}
	}

	private static String runSshCommand(URIish sshUri, FS fs, String command)
			throws IOException {
		RemoteSession session = null;
		Process process = null;
		StreamCopyThread errorThread = null;
		try (MessageWriter stderr = new MessageWriter()) {
			session = SshSessionFactory.getInstance().getSession(sshUri, null,
					fs, 5_000);
			process = session.exec(command, 0);
			errorThread = new StreamCopyThread(process.getErrorStream(),
					stderr.getRawStream());
			errorThread.start();
			try (BufferedReader reader = new BufferedReader(
					new InputStreamReader(process.getInputStream(),
							Constants.CHARSET))) {
				return reader.readLine();
			}
		} finally {
			if (errorThread != null) {
				try {
					errorThread.halt();
				} catch (InterruptedException e) {
					// Stop waiting and return anyway.
				} finally {
					errorThread = null;
				}
			}
			if (process != null) {
				process.destroy();
			}
			if (session != null) {
				SshSessionFactory.getInstance().releaseSession(session);
			}
		}
	}

	/**
	 * @param operation
	 *            the operation to perform, e.g. Protocol.OPERATION_DOWNLOAD
	 * @param resources
	 *            the LFS resources affected
	 * @return a request that can be serialized to JSON
	 */
	public static Protocol.Request toRequest(String operation,
			LfsPointer... resources) {
		Protocol.Request req = new Protocol.Request();
		req.operation = operation;
		if (resources != null) {
			req.objects = new LinkedList<>();
			for (LfsPointer res : resources) {
				Protocol.ObjectSpec o = new Protocol.ObjectSpec();
				o.oid = res.getOid().getName();
				o.size = res.getSize();
				req.objects.add(o);
			}
		}
		return req;
	}

}
