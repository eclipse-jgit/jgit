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

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.LPARAM;
import com.sun.jna.platform.win32.WinDef.LRESULT;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.platform.win32.WinUser;

/**
 * The {@link PageantLibrary} encapsulates the shared memory access and provides
 * a simple pipe abstraction.
 */
public final class PageantLibrary {

	private static final Logger LOG = LoggerFactory
			.getLogger(PageantLibrary.class);

	/** Pageant's "class" and "window name". */
	private static final String PAGEANT = "Pageant"; //$NON-NLS-1$

	/**
	 * Magic constant from Pageant; ID for the CopyStruct used in SendMessage.
	 *
	 * @see <a href=
	 *      "https://git.tartarus.org/?p=simon/putty.git;a=blob;f=windows/pageant.c;h=0e25cc5d48f0#l33">"random goop"</a>
	 */
	private static final int PAGEANT_ID = 0x804e_50ba;

	/**
	 * Determines whether Pageant is currently running.
	 *
	 * @return {@code true} if Pageant is running, {@code false} otherwise
	 */
	boolean isPageantAvailable() {
		LibraryHolder libs = LibraryHolder.getLibrary();
		if (libs == null) {
			return false;
		}
		HWND window = libs.user.FindWindow(PAGEANT, PAGEANT);
		return window != null && !window.equals(WinBase.INVALID_HANDLE_VALUE);
	}

	/**
	 * An abstraction for a bi-directional pipe.
	 */
	interface Pipe extends Closeable {

		/**
		 * Send the given message.
		 *
		 * @param message
		 *            to send
		 * @throws IOException
		 *             on errors
		 */
		void send(byte[] message) throws IOException;

		/**
		 * Reads bytes from the pipe until {@code data} is full.
		 *
		 * @param data
		 *            to read
		 * @throws IOException
		 *             on errors
		 */
		void receive(byte[] data) throws IOException;
	}

	/**
	 * Windows' COPYDATASTRUCT. Must be public for JNA.
	 */
	public static class CopyStruct extends Structure {

		/** Must be set the {@link #PAGEANT_ID}. */
		public int dwData = PAGEANT_ID;

		/** Data length; number of bytes in {@link #lpData}. */
		public long cbData;

		/** Points to {@link #cbData} bytes. */
		public Pointer lpData;

		@Override
		protected List<String> getFieldOrder() {
			return List.of("dwData", "cbData", "lpData"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		}
	}

	private static class PipeImpl implements Pipe {

		private final LibraryHolder libs;

		private final HWND window;

		private final byte[] name;

		private final HANDLE file;

		private final Pointer memory;

		private long readPos = 0;

		PipeImpl(LibraryHolder libs, HWND window, String name, HANDLE file,
				Pointer memory) {
			this.libs = libs;
			this.window = window;
			this.name = name.getBytes(StandardCharsets.US_ASCII);
			this.file = file;
			this.memory = memory;
		}

		@Override
		public void close() throws IOException {
			PageantLibrary.close(libs, file, memory, false);
		}

		private Pointer init(CopyStruct c) {
			c.cbData = name.length + 1L;
			c.lpData = new Memory(c.cbData);
			c.lpData.write(0, name, 0, name.length);
			c.lpData.setByte(name.length, (byte) 0);
			c.write();
			return c.getPointer();
		}

		@Override
		public void send(byte[] message) throws IOException {
			memory.write(0, message, 0, message.length);
			CopyStruct c = new CopyStruct();
			Pointer p = init(c);
			LRESULT result = libs.user.SendMessage(window, WinUser.WM_COPYDATA,
					null, new LPARAM(Pointer.nativeValue(p)));
			if (result == null || result.longValue() == 0) {
				throw new IOException(
						libs.systemError(Texts.get().msgSendFailed2));
			}
		}

		@Override
		public void receive(byte[] data) throws IOException {
			// Relies on Pageant handling the request synchronously, i.e.,
			// SendMessage() above returning successfully only once Pageant
			// has indeed written into the shared memory.
			memory.read(readPos, data, 0, data.length);
			readPos += data.length;
		}
	}

	/**
	 * Creates a new {@link Pipe}.
	 *
	 * @param name
	 *            for the pipe
	 * @param maxSize
	 *            maximum size for messages
	 * @return the {@link Pipe}, or {@code null} if none created
	 * @throws IOException on errors
	 */
	Pipe createPipe(String name, int maxSize) throws IOException {
		LibraryHolder libs = LibraryHolder.getLibrary();
		if (libs == null) {
			throw new IllegalStateException("Libraries were not loaded"); //$NON-NLS-1$
		}
		HWND window = libs.user.FindWindow(PAGEANT, PAGEANT);
		if (window == null || window.equals(WinBase.INVALID_HANDLE_VALUE)) {
			throw new IOException(Texts.get().msgPageantUnavailable);
		}
		String fileName = name + libs.kernel.GetCurrentThreadId();
		HANDLE file = null;
		Pointer sharedMemory = null;
		try {
			file = libs.kernel.CreateFileMapping(WinBase.INVALID_HANDLE_VALUE,
					null, WinNT.PAGE_READWRITE, 0, maxSize, fileName);
			if (file == null || file.equals(WinBase.INVALID_HANDLE_VALUE)) {
				throw new IOException(
						libs.systemError(Texts.get().msgNoMappedFile));
			}
			sharedMemory = libs.kernel.MapViewOfFile(file,
					WinNT.SECTION_MAP_WRITE, 0, 0, 0);
			if (sharedMemory == null) {
				throw new IOException(
						libs.systemError(Texts.get().msgNoSharedMemory));
			}
			return new PipeImpl(libs, window, fileName, file, sharedMemory);
		} catch (IOException e) {
			close(libs, file, sharedMemory, true);
			throw e;
		} catch (Throwable e) {
			close(libs, file, sharedMemory, true);
			throw new IOException(Texts.get().msgSharedMemoryFailed, e);
		}
	}

	private static void close(LibraryHolder libs, HANDLE file, Pointer memory,
			boolean silent) throws IOException {
		if (memory != null) {
			if (!libs.kernel.UnmapViewOfFile(memory)) {
				String msg = libs
						.systemError(Texts.get().errReleaseSharedMemory);
				if (silent) {
					LOG.error(msg);
				} else {
					throw new IOException(msg);
				}
			}
		}
		if (file != null) {
			if (!libs.kernel.CloseHandle(file)) {
				String msg = libs.systemError(Texts.get().errCloseMappedFile);
				if (silent) {
					LOG.error(msg);
				} else {
					throw new IOException(msg);
				}
			}
		}
	}
}
