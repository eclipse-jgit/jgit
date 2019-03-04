/*
 * Copyright (C) 2018-2019, Andre Bossert <andre.bossert@siemens.com>
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

package org.eclipse.jgit.diffmergetool;

/**
 * The pre-defined merge tool.
 *
 */
public class PreDefinedMergeTool extends UserDefinedMergeTool {

	/**
	 * Creates the pre-defined merge tool
	 *
	 * @param name
	 *            the name
	 * @param path
	 *            the path
	 * @param parameters
	 *            the tool parameters that are used together with path as
	 *            command
	 * @param trustExitCode
	 *            the "trust exit code" option
	 */
	public PreDefinedMergeTool(final String name, final String path,
			final String parameters, final BooleanOption trustExitCode) {
		super(name, path, parameters, trustExitCode);
	}

	/**
	 * @param path
	 */
	public void setPath(String path) {
		// handling of spaces in path
		if (path.contains(" ")) { //$NON-NLS-1$
			// add quotes before if needed
			if (!path.startsWith("\"")) { //$NON-NLS-1$
				path = "\"" + path; //$NON-NLS-1$
			}
			// add quotes after if needed
			if (!path.endsWith("\"")) { //$NON-NLS-1$
				path = path + "\""; //$NON-NLS-1$
			}
		}
		this.path = path;
	}

	/**
	 * @param parameters
	 *            the parameters that are added to the tool path (stored as cmd
	 *            in extended class)
	 */
	public void setParameters(String parameters) {
		this.cmd = parameters;
	}

	/**
	 * @param trustExitCode
	 *            the "trust exit code" option
	 */
	public void setTrustExitCode(BooleanOption trustExitCode) {
		this.trustExitCode = trustExitCode;
	}

	/**
	 * @return the tool command
	 */
	@Override
	public String getCommand() {
		return path + " " + cmd; //$NON-NLS-1$
	}

}
