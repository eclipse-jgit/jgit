package org.eclipse.jgit.internal;

import org.eclipse.jgit.internal.util.ShutdownHook;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

/**
 * Bundle activator to participate in OSGi bundle lifecycle
 */
public class Activator implements BundleActivator {

	@Override
	public void start(BundleContext context) throws Exception {
		// empty
	}

	@Override
	public void stop(BundleContext context) throws Exception {
		ShutdownHook.INSTANCE.cleanup();
	}

}
