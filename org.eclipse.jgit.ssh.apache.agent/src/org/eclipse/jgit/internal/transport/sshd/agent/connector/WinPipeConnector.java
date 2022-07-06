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

import java.io.IOException;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.sshd.common.SshException;
import org.eclipse.jgit.transport.sshd.agent.AbstractConnector;
import org.eclipse.jgit.transport.sshd.agent.ConnectorFactory.ConnectorDescriptor;
import org.eclipse.jgit.util.StringUtils;

import com.sun.jna.LastErrorException;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinError;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.ptr.IntByReference;

/**
 * A connector based on JNA using Windows' named pipes to communicate with an
 * ssh agent. This is used by Microsoft's Win32-OpenSSH port.
 */
public class WinPipeConnector extends AbstractConnector {

	// Pipe names are, like other file names, case-insensitive on Windows.
	private static final String CANONICAL_PIPE_NAME = "\\\\.\\pipe\\openssh-ssh-agent"; //$NON-NLS-1$

	/**
	 * {@link ConnectorDescriptor} for the {@link PageantConnector}.
	 */
	public static final ConnectorDescriptor DESCRIPTOR = new ConnectorDescriptor() {

		@Override
		public String getIdentityAgent() {
			return CANONICAL_PIPE_NAME;
		}

		@Override
		public String getDisplayName() {
			return Texts.get().winOpenSsh;
		}
	};

	private static final int FILE_SHARE_NONE = 0;

	private static final int FILE_ATTRIBUTE_NONE = 0;

	private final String pipeName;

	private final AtomicBoolean connected = new AtomicBoolean();

	// It's a byte pipe, so the normal Windows file mechanisms can be used.
	// Would one of the standard Java File I/O abstractions work?
	private volatile HANDLE fileHandle;

	/**
	 * Creates a {@link WinPipeConnector} for the given named pipe.
	 *
	 * @param pipeName
	 *            to connect to
	 */
	public WinPipeConnector(String pipeName) {
		this.pipeName = pipeName.replace('/', '\\');
	}

	@Override
	public boolean connect() throws IOException {
		if (StringUtils.isEmptyOrNull(pipeName)) {
			return false;
		}
		HANDLE file = fileHandle;
		synchronized (this) {
			if (connected.get()) {
				return true;
			}
			LibraryHolder libs = LibraryHolder.getLibrary();
			if (libs == null) {
				return false;
			}
			file = libs.kernel.CreateFile(pipeName,
					WinNT.GENERIC_READ | WinNT.GENERIC_WRITE, FILE_SHARE_NONE,
					null, WinNT.OPEN_EXISTING, FILE_ATTRIBUTE_NONE, null);
			if (file == null || WinBase.INVALID_HANDLE_VALUE.equals(file)) {
				int errorCode = libs.kernel.GetLastError();
				if (errorCode == WinError.ERROR_FILE_NOT_FOUND
						&& CANONICAL_PIPE_NAME.equalsIgnoreCase(pipeName)) {
					// OpenSSH agent not running. Don't throw.
					return false;
				}
				LastErrorException cause = new LastErrorException(
						libs.systemError());
				throw new IOException(MessageFormat
						.format(Texts.get().msgConnectPipeFailed, pipeName),
						cause);
			}
			connected.set(true);
		}
		fileHandle = file;
		return connected.get();
	}

	@Override
	public synchronized void close() throws IOException {
		HANDLE file = fileHandle;
		if (connected.getAndSet(false) && fileHandle != null) {
			fileHandle = null;
			LibraryHolder libs = LibraryHolder.getLibrary();
			boolean success = libs.kernel.CloseHandle(file);
			if (!success) {
				LastErrorException cause = new LastErrorException(
						libs.systemError());
				throw new IOException(MessageFormat
						.format(Texts.get().msgCloseFailed, pipeName), cause);
			}
		}
	}

	@Override
	public byte[] rpc(byte command, byte[] message) throws IOException {
		prepareMessage(command, message);
		HANDLE file = fileHandle;
		if (!connected.get() || file == null) {
			// No translation, internal error
			throw new IllegalStateException("Not connected to SSH agent"); //$NON-NLS-1$
		}
		LibraryHolder libs = LibraryHolder.getLibrary();
		writeFully(libs, file, message);
		// Now receive the reply
		byte[] lengthBuf = new byte[4];
		readFully(libs, file, lengthBuf);
		int length = toLength(command, lengthBuf);
		byte[] payload = new byte[length];
		readFully(libs, file, payload);
		return payload;
	}

	private void writeFully(LibraryHolder libs, HANDLE file, byte[] message)
			throws IOException {
		byte[] buf = message;
		int toWrite = buf.length;
		try {
			while (toWrite > 0) {
				IntByReference written = new IntByReference();
				boolean success = libs.kernel.WriteFile(file, buf, buf.length,
						written, null);
				if (!success) {
					throw new LastErrorException(libs.systemError());
				}
				int actuallyWritten = written.getValue();
				toWrite -= actuallyWritten;
				if (actuallyWritten > 0 && toWrite > 0) {
					buf = Arrays.copyOfRange(buf, actuallyWritten, buf.length);
				}
			}
		} catch (LastErrorException e) {
			throw new IOException(MessageFormat.format(
					Texts.get().msgSendFailed, Integer.toString(message.length),
					Integer.toString(toWrite)), e);
		}
	}

	private void readFully(LibraryHolder libs, HANDLE file, byte[] data)
			throws IOException {
		int n = 0;
		int offset = 0;
		while (offset < data.length && (n = read(libs, file, data, offset,
				data.length - offset)) > 0) {
			offset += n;
		}
		if (offset < data.length) {
			throw new SshException(MessageFormat.format(
					Texts.get().msgShortRead, Integer.toString(data.length),
					Integer.toString(offset), Integer.toString(n)));
		}
	}

	private int read(LibraryHolder libs, HANDLE file, byte[] buffer, int offset,
			int length) throws IOException {
		try {
			int toRead = length;
			IntByReference read = new IntByReference();
			if (offset == 0) {
				boolean success = libs.kernel.ReadFile(file, buffer, toRead,
						read, null);
				if (!success) {
					throw new LastErrorException(libs.systemError());
				}
				return read.getValue();
			}
			byte[] data = new byte[length];
			boolean success = libs.kernel.ReadFile(file, buffer, toRead, read,
					null);
			if (!success) {
				throw new LastErrorException(libs.systemError());
			}
			int actuallyRead = read.getValue();
			if (actuallyRead > 0) {
				System.arraycopy(data, 0, buffer, offset, actuallyRead);
			}
			return actuallyRead;
		} catch (LastErrorException e) {
			throw new IOException(MessageFormat.format(
					Texts.get().msgReadFailed, Integer.toString(length)), e);
		}
	}
}
