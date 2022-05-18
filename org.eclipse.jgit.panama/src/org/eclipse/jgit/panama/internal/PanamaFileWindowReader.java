/*
 * Copyright (C) 2022 Matthias Sohn <matthias.sohn@sap.com>
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.panama.internal;

import java.io.IOException;

import org.eclipse.jgit.internal.storage.file.ByteWindow;
import org.eclipse.jgit.internal.storage.file.FileWindowReader;

/**
 * Use Panama Foreign Memory API to map pages of pack files to memory
 */
public class PanamaFileWindowReader implements FileWindowReader {

	@Override
	public void close() throws Exception {
		// TODO Auto-generated method stub

	}

	@Override
	public FileWindowReader open() throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public long length() throws IOException {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public ByteWindow read(long pos, int size) throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void readRaw(byte[] b, long off, int len) throws IOException {
		// TODO Auto-generated method stub

	}

}
