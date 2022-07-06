/*
 * Copyright (C) 2021, Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.transport.sshd.agent.connector;

import java.nio.charset.Charset;

import com.sun.jna.Structure;
import com.sun.jna.Structure.FieldOrder;

/**
 * Common things for socket communication.
 */
public final class Sockets {

	private Sockets() {
		// No instantiation
	}

	/**
	 * Domain for Unix domain sockets.
	 */
	public static final int AF_UNIX = 1;

	/**
	 * Socket type for duplex sockets.
	 */
	public static final int SOCK_STREAM = 1;

	/**
	 * Default protocol selector.
	 */
	public static final int DEFAULT_PROTOCOL = 0;

	/**
	 * Very simple representation of the C SockAddr type.
	 */
	@FieldOrder(value = { "sa_family", "sa_data" })
	public static class SockAddr extends Structure {
		// This is a "variable length struct" in C.

		// Why 108 is apparently lost in time. But the file path for a Unix
		// domain socket cannot be longer (including the terminating NUL).
		private static final int MAX_DATA_LENGTH = 108;

		/** Socket family */
		public short sa_family = AF_UNIX;

		/** Unix domain socket path. */
		public byte[] sa_data = new byte[MAX_DATA_LENGTH];

		/**
		 * Creates a new {@link SockAddr} for the given {@code path}.
		 *
		 * @param path
		 *            for the Socket
		 * @param encoding
		 *            to use to decode the {@code path} to a byte sequence
		 */
		public SockAddr(String path, Charset encoding) {
			byte[] bytes = path.getBytes(encoding);
			int toCopy = Math.min(sa_data.length - 1, bytes.length);
			System.arraycopy(bytes, 0, sa_data, 0, toCopy);
			sa_data[toCopy] = 0;
		}
	}
}
