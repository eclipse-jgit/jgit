/*
 * Copyright (C) 2010, Christian Halstrick <christian.halstrick@sap.com> and
 * other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v1.0 which accompanies this
 * distribution, is reproduced below, and is available at
 * http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * - Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the names of its
 * contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package org.eclipse.jgit.api.errors;

import java.text.MessageFormat;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;

/**
 * Exception thrown when a command wants to update a ref but failed because
 * another process is accessing (or even also updating) the ref.
 *
 * @see org.eclipse.jgit.lib.RefUpdate.Result#LOCK_FAILURE
 */
public class ConcurrentRefUpdateException extends GitAPIException {
	private static final long serialVersionUID = 1L;
	private RefUpdate.Result rc;
	private Ref ref;

	/**
	 * Constructor for ConcurrentRefUpdateException.
	 *
	 * @param message
	 *            error message
	 * @param ref
	 *            a {@link org.eclipse.jgit.lib.Ref}
	 * @param rc
	 *            a {@link org.eclipse.jgit.lib.RefUpdate.Result}
	 * @param cause
	 *            a {@link java.lang.Throwable}
	 */
	public ConcurrentRefUpdateException(String message, Ref ref,
			RefUpdate.Result rc, Throwable cause) {
		super((rc == null) ? message : message + ". " //$NON-NLS-1$
				+ MessageFormat.format(JGitText.get().refUpdateReturnCodeWas, rc), cause);
		this.rc = rc;
		this.ref = ref;
	}

	/**
	 * Constructor for ConcurrentRefUpdateException.
	 *
	 * @param message
	 *            error message
	 * @param ref
	 *            a {@link org.eclipse.jgit.lib.Ref}
	 * @param rc
	 *            a {@link org.eclipse.jgit.lib.RefUpdate.Result}
	 */
	public ConcurrentRefUpdateException(String message, Ref ref,
			RefUpdate.Result rc) {
		super((rc == null) ? message : message + ". " //$NON-NLS-1$
				+ MessageFormat.format(JGitText.get().refUpdateReturnCodeWas, rc));
		this.rc = rc;
		this.ref = ref;
	}

	/**
	 * Get <code>Ref</code>
	 *
	 * @return the {@link org.eclipse.jgit.lib.Ref} which was tried to by
	 *         updated
	 */
	public Ref getRef() {
		return ref;
	}

	/**
	 * Get result
	 *
	 * @return the result which was returned by
	 *         {@link org.eclipse.jgit.lib.RefUpdate#update()} and which caused
	 *         this error
	 */
	public RefUpdate.Result getResult() {
		return rc;
	}
}
