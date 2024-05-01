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

import com.sun.jna.LastErrorException;
import com.sun.jna.Library;
import com.sun.jna.Structure;
import com.sun.jna.platform.unix.LibCAPI;

/**
 * Low-level Unix/Linux JNA socket API.
 */
interface UnixSockets extends LibCAPI, Library {

	/**
	 * Library to load. These functions live in libc.
	 */
	String LIBRARY_NAME = "c"; //$NON-NLS-1$

	/**
	 * Command to set the close-on-exec flag on a file descriptor via
	 * {@link #fcntl(int, int, int)}.
	 */
	int F_SETFD = 2;

	/**
	 * Specifies that a file descriptor shall not be inherited by child
	 * processes.
	 */
	int FD_CLOEXEC = 1;

	/**
	 * Creates a socket and returns a file descriptor for it.
	 *
	 * @param domain
	 *            socket domain; use {@link Sockets#AF_UNIX}
	 * @param type
	 *            socket type; use {@link Sockets#SOCK_STREAM}
	 * @param protocol
	 *            socket communication protocol; use
	 *            {@link Sockets#DEFAULT_PROTOCOL}.
	 * @return file descriptor for the socket; should be closed eventually, or
	 *         -1 on error.
	 * @throws LastErrorException
	 *             on errors
	 * @see LibCAPI#close(int)
	 */
	int socket(int domain, int type, int protocol) throws LastErrorException;

	/**
	 * Simple binding to fcntl; used to set the FD_CLOEXEC flag. On OS X, we
	 * cannot include SOCK_CLOEXEC in the socket() call.
	 *
	 * @param fd
	 *            file descriptor to operate on
	 * @param command
	 *            set to {@link #F_SETFD}
	 * @param flag
	 *            zero to clear the close-on-exec flag, {@link #FD_CLOEXEC} to
	 *            set it
	 * @return -1 on error, otherwise a value >= 0
	 * @throws LastErrorException
	 *             represents non-zero error code for the last error
	 */
	int fcntl(int fd, int command, int flag) throws LastErrorException;

	/**
	 * Connects a file descriptor, which must refer to a socket, to a
	 * {@link Sockets.SockAddr}.
	 *
	 * @param fd
	 *            file descriptor of the socket, as returned by
	 *            {@link #socket(int, int, int)}
	 * @param addr
	 *            address to connect to
	 * @param addrLen
	 *            Length of {@code addr}, use {@link Structure#size()}
	 * @return 0 on success; -1 otherwise
	 * @throws LastErrorException
	 *             on errors
	 */
	int connect(int fd, Sockets.SockAddr addr, int addrLen)
			throws LastErrorException;

	/**
	 * Read data from a file descriptor.
	 *
	 * @param fd
	 *            file descriptor to read from
	 * @param buf
	 *            buffer to read into
	 * @param bufLen
	 *            maximum number of bytes to read; at most length of {@code buf}
	 * @return number of bytes actually read; zero for EOF, -1 on error
	 * @throws LastErrorException
	 *             on errors
	 */
	LibCAPI.ssize_t read(int fd, byte[] buf, LibCAPI.size_t bufLen)
			throws LastErrorException;

	/**
	 * Write data to a file descriptor.
	 *
	 * @param fd
	 *            file descriptor to write to
	 * @param data
	 *            data to write
	 * @param dataLen
	 *            number of bytes to write
	 * @return number of bytes actually written; -1 on error
	 * @throws LastErrorException
	 *             on errors
	 */
	LibCAPI.ssize_t write(int fd, byte[] data, LibCAPI.size_t dataLen)
			throws LastErrorException;
}
