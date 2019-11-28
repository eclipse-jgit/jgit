/*
 * Copyright (C) 2015 Obeo.
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
package org.eclipse.jgit.hooks;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Collection;

import org.eclipse.jgit.api.errors.AbortedByHookException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.RemoteRefUpdate;

/**
 * The <code>pre-push</code> hook implementation. The pre-push hook runs during
 * git push, after the remote refs have been updated but before any objects have
 * been transferred.
 *
 * @since 4.2
 */
public class PrePushHook extends GitHook<String> {

	/**
	 * Constant indicating the name of the pre-push hook.
	 */
	public static final String NAME = "pre-push"; //$NON-NLS-1$

	private String remoteName;

	private String remoteLocation;

	private String refs;

	/**
	 * Constructor for PrePushHook
	 * <p>
	 * This constructor will use the default error stream.
	 * </p>
	 *
	 * @param repo
	 *            The repository
	 * @param outputStream
	 *            The output stream the hook must use. {@code null} is allowed,
	 *            in which case the hook will use {@code System.out}.
	 */
	protected PrePushHook(Repository repo, PrintStream outputStream) {
		super(repo, outputStream);
	}

	/**
	 * Constructor for PrePushHook
	 *
	 * @param repo
	 *            The repository
	 * @param outputStream
	 *            The output stream the hook must use. {@code null} is allowed,
	 *            in which case the hook will use {@code System.out}.
	 * @param errorStream
	 *            The error stream the hook must use. {@code null} is allowed,
	 *            in which case the hook will use {@code System.err}.
	 * @since 5.6
	 */
	protected PrePushHook(Repository repo, PrintStream outputStream,
			PrintStream errorStream) {
		super(repo, outputStream, errorStream);
	}

	/** {@inheritDoc} */
	@Override
	protected String getStdinArgs() {
		return refs;
	}

	/** {@inheritDoc} */
	@Override
	public String call() throws IOException, AbortedByHookException {
		if (canRun()) {
			doRun();
		}
		return ""; //$NON-NLS-1$
	}

	/**
	 * @return {@code true}
	 */
	private boolean canRun() {
		return true;
	}

	/** {@inheritDoc} */
	@Override
	public String getHookName() {
		return NAME;
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * This hook receives two parameters, which is the name and the location of
	 * the remote repository.
	 */
	@Override
	protected String[] getParameters() {
		if (remoteName == null) {
			remoteName = remoteLocation;
		}
		return new String[] { remoteName, remoteLocation };
	}

	/**
	 * Set remote name
	 *
	 * @param name
	 *            remote name
	 */
	public void setRemoteName(String name) {
		remoteName = name;
	}

	/**
	 * Get remote name
	 *
	 * @return remote name or null
	 * @since 4.11
	 */
	protected String getRemoteName() {
		return remoteName;
	}

	/**
	 * Set remote location
	 *
	 * @param location
	 *            a remote location
	 */
	public void setRemoteLocation(String location) {
		remoteLocation = location;
	}

	/**
	 * Set Refs
	 *
	 * @param toRefs
	 *            a collection of {@code RemoteRefUpdate}s
	 */
	public void setRefs(Collection<RemoteRefUpdate> toRefs) {
		StringBuilder b = new StringBuilder();
		for (RemoteRefUpdate u : toRefs) {
			b.append(u.getSrcRef());
			b.append(" "); //$NON-NLS-1$
			b.append(u.getNewObjectId().getName());
			b.append(" "); //$NON-NLS-1$
			b.append(u.getRemoteName());
			b.append(" "); //$NON-NLS-1$
			ObjectId ooid = u.getExpectedOldObjectId();
			b.append((ooid == null) ? ObjectId.zeroId().getName() : ooid
					.getName());
			b.append("\n"); //$NON-NLS-1$
		}
		refs = b.toString();
	}
}
