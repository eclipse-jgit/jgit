/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.internal;

import static org.eclipse.jgit.niofs.internal.util.Preconditions.checkNotNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;

/**
 *
 */
public class SeekableByteChannelWrapper implements SeekableByteChannel {

	private final SeekableByteChannel channel;

	public SeekableByteChannelWrapper(final SeekableByteChannel channel) {
		this.channel = checkNotNull("channel", channel);
	}

	@Override
	public long position() throws IOException {
		return channel.position();
	}

	@Override
	public SeekableByteChannel position(final long newPosition) throws IOException {
		return channel.position(newPosition);
	}

	@Override
	public long size() throws IOException {
		return channel.size();
	}

	@Override
	public SeekableByteChannel truncate(final long size) throws IOException {
		return channel.truncate(size);
	}

	@Override
	public int read(final ByteBuffer dst) throws java.io.IOException {
		return channel.read(dst);
	}

	@Override
	public int write(final ByteBuffer src) throws java.io.IOException {
		return channel.write(src);
	}

	@Override
	public boolean isOpen() {
		return channel.isOpen();
	}

	@Override
	public void close() throws java.io.IOException {
		channel.close();
	}
}
