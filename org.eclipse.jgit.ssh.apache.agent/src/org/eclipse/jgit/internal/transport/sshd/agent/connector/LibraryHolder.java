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

import java.text.MessageFormat;

import com.sun.jna.LastErrorException;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.Kernel32Util;
import com.sun.jna.platform.win32.User32;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Delay loading the native libraries until needed.
 */
class LibraryHolder {

	private static final Logger LOG = LoggerFactory
			.getLogger(LibraryHolder.class);

	private static LibraryHolder INSTANCE;

	private static boolean libraryLoaded = false;

	public static synchronized LibraryHolder getLibrary() {
		if (!libraryLoaded) {
			libraryLoaded = true;
			try {
				INSTANCE = new LibraryHolder();
			} catch (Exception | UnsatisfiedLinkError
					| NoClassDefFoundError e) {
				LOG.error(Texts.get().logErrorLoadLibrary, e);
			}
		}
		return INSTANCE;
	}

	User32 user;

	Kernel32 kernel;

	private LibraryHolder() {
		user = User32.INSTANCE;
		kernel = Kernel32.INSTANCE;
	}

	String systemError() {
		return systemError("[{0}] - {1}"); //$NON-NLS-1$
	}

	String systemError(String pattern) {
		int lastError = kernel.GetLastError();
		String msg;
		try {
			msg = Kernel32Util.formatMessageFromLastErrorCode(lastError);
		} catch (Exception e) {
			String err = e instanceof LastErrorException
					? Integer.toString(((LastErrorException) e).getErrorCode())
					: Texts.get().errUnknown;
			msg = MessageFormat.format(Texts.get().errLastError,
					Integer.toString(lastError), err);
			LOG.error(msg, e);
		}
		return MessageFormat.format(pattern, Integer.toString(lastError), msg);
	}

}