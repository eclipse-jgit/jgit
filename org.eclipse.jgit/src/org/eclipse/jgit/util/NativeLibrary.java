/*
 * Copyright (C) 2010, Google, Inc.
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

package org.eclipse.jgit.util;

import java.text.MessageFormat;

import org.eclipse.jgit.internal.JGitText;

/** Loads the JGit native library, when available. */
public final class NativeLibrary {
	private static final String SYSTEM_PROPERTY = "jgit.native.skip";

	private static final String NAME = "jgit_native";

	private static final Throwable state;

	static {
		Throwable myState;
		try {
			if (Boolean.getBoolean(SYSTEM_PROPERTY)) {
				myState = new Disabled();
			} else {
				System.loadLibrary(NAME);
				myState = new Loaded();
			}
		} catch (UnsatisfiedLinkError linkError) {
			// Try to wrap the error to also include the system path.
			try {
				final String propName = "java.library.path";
				final String path = System.getProperty(propName);
				myState = new UnsatisfiedLinkError(MessageFormat.format(
						JGitText.get().noNativeLibraryIn, NAME, propName, path));
				myState.initCause(linkError);
			} catch (SecurityException noPath) {
				myState = linkError;
			}
		} catch (Throwable cannotLink) {
			myState = cannotLink;
		}
		state = myState;
	}

	/** @return true if the native library is loaded and functioning. */
	public static boolean isLoaded() {
		return state instanceof Loaded;
	}

	/** @return true if the native library was manually disabled. */
	public static boolean isDisabled() {
		return state instanceof Disabled;
	}

	/** @return if {@link #isLoaded()} is false, the reason why it failed. */
	public static Throwable getFailure() {
		if (state instanceof Loaded)
			return null;
		else
			return state;
	}

	/**
	 * Assert the native library was loaded successfully.
	 *
	 * @throws UnsatisfiedLinkError
	 *             if the library was not loaded.
	 */
	public static void assertLoaded() throws UnsatisfiedLinkError {
		if (!isLoaded()) {
			UnsatisfiedLinkError e = new UnsatisfiedLinkError(NAME);
			e.initCause(getFailure());
			throw e;
		}
	}

	private NativeLibrary() {
		// No instances are permitted.
	}

	private static class Loaded extends Exception {
		private static final long serialVersionUID = 1L;
	}

	private static class Disabled extends Exception {
		private static final long serialVersionUID = 1L;

		Disabled() {
			super(SYSTEM_PROPERTY + "=true");
		}
	}
}
