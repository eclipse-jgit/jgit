/*
 * Copyright (C) 2018, Thomas Wolf <thomas.wolf@paranor.ch>
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
package org.eclipse.jgit.internal.transport.sshd;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.jgit.annotations.NonNull;
import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.GSSName;
import org.ietf.jgss.Oid;

/**
 * Global repository of GSS-API mechanisms that we can use.
 */
public class GssApiMechanisms {

	private GssApiMechanisms() {
		// No instantiation
	}

	/** Prefix to use with {@link GSSName#NT_HOSTBASED_SERVICE}. */
	public static final String GSSAPI_HOST_PREFIX = "host@"; //$NON-NLS-1$

	/** The {@link Oid} of Kerberos 5. */
	public static final Oid KERBEROS_5 = createOid("1.2.840.113554.1.2.2"); //$NON-NLS-1$

	/** SGNEGO is not to be used with ssh. */
	public static final Oid SPNEGO = createOid("1.3.6.1.5.5.2"); //$NON-NLS-1$

	/** Protects {@link #supportedMechanisms}. */
	private static final Object LOCK = new Object();

	/**
	 * The {@link AtomicBoolean} is set to {@code true} when the mechanism could
	 * be initialized successfully at least once.
	 */
	private static Map<Oid, Boolean> supportedMechanisms;

	/**
	 * Retrieves an immutable collection of the supported mechanisms.
	 *
	 * @return the supported mechanisms
	 */
	@NonNull
	public static Collection<Oid> getSupportedMechanisms() {
		synchronized (LOCK) {
			if (supportedMechanisms == null) {
				GSSManager manager = GSSManager.getInstance();
				Oid[] mechs = manager.getMechs();
				Map<Oid, Boolean> mechanisms = new LinkedHashMap<>();
				if (mechs != null) {
					for (Oid oid : mechs) {
						mechanisms.put(oid, Boolean.FALSE);
					}
				}
				supportedMechanisms = mechanisms;
			}
			return Collections.unmodifiableSet(supportedMechanisms.keySet());
		}
	}

	/**
	 * Report that this mechanism was used successfully.
	 *
	 * @param mechanism
	 *            that worked
	 */
	public static void worked(@NonNull Oid mechanism) {
		synchronized (LOCK) {
			supportedMechanisms.put(mechanism, Boolean.TRUE);
		}
	}

	/**
	 * Mark the mechanisms as failed.
	 *
	 * @param mechanism
	 *            to mark
	 */
	public static void failed(@NonNull Oid mechanism) {
		synchronized (LOCK) {
			Boolean worked = supportedMechanisms.get(mechanism);
			if (worked != null && !worked.booleanValue()) {
				// If it never worked, remove it
				supportedMechanisms.remove(mechanism);
			}
		}
	}

	/**
	 * Resolves an {@link InetSocketAddress}.
	 *
	 * @param remote
	 *            to resolve
	 * @return the resolved {@link InetAddress}, or {@code null} if unresolved.
	 */
	public static InetAddress resolve(@NonNull InetSocketAddress remote) {
		InetAddress address = remote.getAddress();
		if (address == null) {
			try {
				address = InetAddress.getByName(remote.getHostString());
			} catch (UnknownHostException e) {
				return null;
			}
		}
		return address;
	}

	/**
	 * Determines a canonical host name for use use with GSS-API.
	 *
	 * @param remote
	 *            to get the host name from
	 * @return the canonical host name, if it can be determined, otherwise the
	 *         {@link InetSocketAddress#getHostString() unprocessed host name}.
	 */
	@NonNull
	public static String getCanonicalName(@NonNull InetSocketAddress remote) {
		InetAddress address = resolve(remote);
		if (address == null) {
			return remote.getHostString();
		}
		return address.getCanonicalHostName();
	}

	/**
	 * Creates a {@link GSSContext} for the given mechanism to authenticate with
	 * the host given by {@code fqdn}.
	 *
	 * @param mechanism
	 *            {@link Oid} of the mechanism to use
	 * @param fqdn
	 *            fully qualified domain name of the host to authenticate with
	 * @return the context, if the mechanism is available and the context could
	 *         be created, or {@code null} otherwise
	 */
	public static GSSContext createContext(@NonNull Oid mechanism,
			@NonNull String fqdn) {
		GSSContext context = null;
		try {
			GSSManager manager = GSSManager.getInstance();
			context = manager.createContext(
					manager.createName(
							GssApiMechanisms.GSSAPI_HOST_PREFIX + fqdn,
							GSSName.NT_HOSTBASED_SERVICE),
					mechanism, null, GSSContext.DEFAULT_LIFETIME);
		} catch (GSSException e) {
			closeContextSilently(context);
			failed(mechanism);
			return null;
		}
		worked(mechanism);
		return context;
	}

	/**
	 * Closes (disposes of) a {@link GSSContext} ignoring any
	 * {@link GSSException}s.
	 *
	 * @param context
	 *            to dispose
	 */
	public static void closeContextSilently(GSSContext context) {
		if (context != null) {
			try {
				context.dispose();
			} catch (GSSException e) {
				// Ignore
			}
		}
	}

	private static Oid createOid(String rep) {
		try {
			return new Oid(rep);
		} catch (GSSException e) {
			// Does not occur
			return null;
		}
	}

}
