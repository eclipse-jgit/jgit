/*
 * Copyright (C) 2008, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.pgm;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.text.MessageFormat;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.eclipse.jgit.pgm.internal.CLIText;

@Command(common = true, usage = "usage_DisplayTheVersionOfJgit")
class Version extends TextBuiltin {
	/** {@inheritDoc} */
	@Override
	protected void run() {
		// read the Implementation-Version from Manifest
		String version = getImplementationVersion();

		// if Implementation-Version is not available then try reading
		// Bundle-Version
		if (version == null) {
			version = getBundleVersion();
		}

		// if both Implementation-Version and Bundle-Version are not available
		// then throw an exception
		if (version == null) {
			throw die(CLIText.get().cannotReadPackageInformation);
		}

		try {
			outw.println(
					MessageFormat.format(CLIText.get().jgitVersion, version));
		} catch (IOException e) {
			throw die(e.getMessage(), e);
		}
	}

	/** {@inheritDoc} */
	@Override
	protected final boolean requiresRepository() {
		return false;
	}

	private String getImplementationVersion() {
		Package pkg = getClass().getPackage();
		return (pkg == null) ? null : pkg.getImplementationVersion();
	}

	private String getBundleVersion() {
		ClassLoader cl = getClass().getClassLoader();
		if (cl instanceof URLClassLoader) {
			URL url = ((URLClassLoader) cl).findResource(JarFile.MANIFEST_NAME);
			if (url != null)
				return getBundleVersion(url);
		}
		return null;
	}

	private static String getBundleVersion(URL url) {
		try (InputStream is = url.openStream()) {
			Manifest manifest = new Manifest(is);
			return manifest.getMainAttributes().getValue("Bundle-Version"); //$NON-NLS-1$
		} catch (IOException e) {
			// do nothing - will return null
		}
		return null;
	}
}
