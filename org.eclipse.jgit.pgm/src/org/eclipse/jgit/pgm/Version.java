/*
 * Copyright (C) 2008, Google Inc.
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
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
		if (version == null)
			version = getBundleVersion();

		// if both Implementation-Version and Bundle-Version are not available
		// then throw an exception
		if (version == null)
			throw die(CLIText.get().cannotReadPackageInformation);

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
