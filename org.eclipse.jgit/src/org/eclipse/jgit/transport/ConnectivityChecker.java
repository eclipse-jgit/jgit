package org.eclipse.jgit.transport;

import java.io.IOException;
import java.util.Set;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ProgressMonitor;

/**
 *
 * Checks that received pack doesn't depend on objects which user don't have
 * access to.
 *
 * @since 4.13
 */
public interface ConnectivityChecker {

	/**
	 * Checks connectivity of the commit graph after pack uploading.
	 *
	 * @param baseReceivePack
	 * @param haves
	 *            Set of references known for client.
	 * @param monitor
	 *            Monitor to publish progress to.
	 * @throws IOException
	 *             an error occurred during connectivity checking.
	 *
	 * @since 4.13
	 */
	void checkConnectivity(BaseReceivePack baseReceivePack,
			Set<ObjectId> haves, ProgressMonitor monitor)
			throws IOException;

}
