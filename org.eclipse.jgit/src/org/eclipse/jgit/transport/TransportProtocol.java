/*
 * Copyright (C) 2011, Google Inc.
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

package org.eclipse.jgit.transport;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Repository;

/**
 * Describes a way to connect to another Git repository.
 * <p>
 * Implementations of this class are typically immutable singletons held by
 * static class members, for example:
 *
 * <pre>
 * package com.example.my_transport;
 *
 * class MyTransport extends Transport {
 * 	public static final TransportProtocol PROTO = new TransportProtocol() {
 * 		public String getName() {
 * 			return &quot;My Protocol&quot;;
 * 		}
 * 	};
 * }
 * </pre>
 *
 * <p>
 * Applications may register additional protocols for use by JGit by calling
 * {@link org.eclipse.jgit.transport.Transport#register(TransportProtocol)}.
 * Because that API holds onto the protocol object by a WeakReference,
 * applications must ensure their own ClassLoader retains the TransportProtocol
 * for the life of the application. Using a static singleton pattern as above
 * will ensure the protocol is valid so long as the ClassLoader that defines it
 * remains valid.
 * <p>
 * Applications may automatically register additional protocols by filling in
 * the names of their TransportProtocol defining classes using the services file
 * {@code META-INF/services/org.eclipse.jgit.transport.Transport}. For each
 * class name listed in the services file, any static fields of type
 * {@code TransportProtocol} will be automatically registered. For the above
 * example the string {@code com.example.my_transport.MyTransport} should be
 * listed in the file, as that is the name of the class that defines the static
 * PROTO singleton.
 */
public abstract class TransportProtocol {
	/** Fields within a {@link URIish} that a transport uses. */
	public static enum URIishField {
		/** the user field */
		USER,
		/** the pass (aka password) field */
		PASS,
		/** the host field */
		HOST,
		/** the port field */
		PORT,
		/** the path field */
		PATH,
	}

	/**
	 * Get text name of the protocol suitable for display to a user.
	 *
	 * @return text name of the protocol suitable for display to a user.
	 */
	public abstract String getName();

	/**
	 * Get immutable set of schemes supported by this protocol.
	 *
	 * @return immutable set of schemes supported by this protocol.
	 */
	public Set<String> getSchemes() {
		return Collections.emptySet();
	}

	/**
	 * Get immutable set of URIishFields that must be filled in.
	 *
	 * @return immutable set of URIishFields that must be filled in.
	 */
	public Set<URIishField> getRequiredFields() {
		return Collections.unmodifiableSet(EnumSet.of(URIishField.PATH));
	}

	/**
	 * Get immutable set of URIishFields that may be filled in.
	 *
	 * @return immutable set of URIishFields that may be filled in.
	 */
	public Set<URIishField> getOptionalFields() {
		return Collections.emptySet();
	}

	/**
	 * Get the default port if the protocol supports a port, else -1.
	 *
	 * @return the default port if the protocol supports a port, else -1.
	 */
	public int getDefaultPort() {
		return -1;
	}

	/**
	 * Determine if this protocol can handle a particular URI.
	 * <p>
	 * Implementations should try to avoid looking at the local filesystem, but
	 * may look at implementation specific configuration options in the remote
	 * block of {@code local.getConfig()} using {@code remoteName} if the name
	 * is non-null.
	 * <p>
	 * The default implementation of this method matches the scheme against
	 * {@link #getSchemes()}, required fields against
	 * {@link #getRequiredFields()}, and optional fields against
	 * {@link #getOptionalFields()}, returning true only if all of the fields
	 * match the specification.
	 *
	 * @param uri
	 *            address of the Git repository; never null.
	 * @return true if this protocol can handle this URI; false otherwise.
	 */
	public boolean canHandle(URIish uri) {
		return canHandle(uri, null, null);
	}

	/**
	 * Determine if this protocol can handle a particular URI.
	 * <p>
	 * Implementations should try to avoid looking at the local filesystem, but
	 * may look at implementation specific configuration options in the remote
	 * block of {@code local.getConfig()} using {@code remoteName} if the name
	 * is non-null.
	 * <p>
	 * The default implementation of this method matches the scheme against
	 * {@link #getSchemes()}, required fields against
	 * {@link #getRequiredFields()}, and optional fields against
	 * {@link #getOptionalFields()}, returning true only if all of the fields
	 * match the specification.
	 *
	 * @param uri
	 *            address of the Git repository; never null.
	 * @param local
	 *            the local repository that will communicate with the other Git
	 *            repository. May be null if the caller is only asking about a
	 *            specific URI and does not have a local Repository.
	 * @param remoteName
	 *            name of the remote, if the remote as configured in
	 *            {@code local}; otherwise null.
	 * @return true if this protocol can handle this URI; false otherwise.
	 */
	public boolean canHandle(URIish uri, Repository local, String remoteName) {
		if (!getSchemes().isEmpty() && !getSchemes().contains(uri.getScheme()))
			return false;

		for (URIishField field : getRequiredFields()) {
			switch (field) {
			case USER:
				if (uri.getUser() == null || uri.getUser().length() == 0)
					return false;
				break;

			case PASS:
				if (uri.getPass() == null || uri.getPass().length() == 0)
					return false;
				break;

			case HOST:
				if (uri.getHost() == null || uri.getHost().length() == 0)
					return false;
				break;

			case PORT:
				if (uri.getPort() <= 0)
					return false;
				break;

			case PATH:
				if (uri.getPath() == null || uri.getPath().length() == 0)
					return false;
				break;

			default:
				return false;
			}
		}

		Set<URIishField> canHave = EnumSet.copyOf(getRequiredFields());
		canHave.addAll(getOptionalFields());

		if (uri.getUser() != null && !canHave.contains(URIishField.USER))
			return false;
		if (uri.getPass() != null && !canHave.contains(URIishField.PASS))
			return false;
		if (uri.getHost() != null && !canHave.contains(URIishField.HOST))
			return false;
		if (uri.getPort() > 0 && !canHave.contains(URIishField.PORT))
			return false;
		if (uri.getPath() != null && !canHave.contains(URIishField.PATH))
			return false;

		return true;
	}

	/**
	 * Open a Transport instance to the other repository.
	 * <p>
	 * Implementations should avoid making remote connections until an operation
	 * on the returned Transport is invoked, however they may fail fast here if
	 * they know a connection is impossible, such as when using the local
	 * filesystem and the target path does not exist.
	 * <p>
	 * Implementations may access implementation-specific configuration options
	 * within {@code local.getConfig()} using the remote block named by the
	 * {@code remoteName}, if the name is non-null.
	 *
	 * @param uri
	 *            address of the Git repository.
	 * @param local
	 *            the local repository that will communicate with the other Git
	 *            repository.
	 * @param remoteName
	 *            name of the remote, if the remote as configured in
	 *            {@code local}; otherwise null.
	 * @return the transport.
	 * @throws org.eclipse.jgit.errors.NotSupportedException
	 *             this protocol does not support the URI.
	 * @throws org.eclipse.jgit.errors.TransportException
	 *             the transport cannot open this URI.
	 */
	public abstract Transport open(URIish uri, Repository local,
			String remoteName)
			throws NotSupportedException, TransportException;

	/**
	 * Open a new transport instance to the remote repository. Use default
	 * configuration instead of reading from configuration files.
	 *
	 * @param uri
	 *            a {@link org.eclipse.jgit.transport.URIish} object.
	 * @return new Transport
	 * @throws org.eclipse.jgit.errors.NotSupportedException
	 * @throws org.eclipse.jgit.errors.TransportException
	 */
	public Transport open(URIish uri)
			throws NotSupportedException, TransportException {
		throw new NotSupportedException(JGitText
				.get().transportNeedsRepository);
	}
}
