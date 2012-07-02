/*
 * Copyright (C) 2012, Google Inc.
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

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.pack.PackWriter;
import org.eclipse.jgit.transport.PublisherPackSlice.LoadCallback;
import org.eclipse.jgit.transport.PublisherPackSlice.LoadPolicy;
import org.eclipse.jgit.transport.ReceiveCommand.Type;

/**
 * Generates PublisherPacks comprised of PublisherPackSlices.
 */
public class PublisherPackFactory {
	/** Size of each slice in bytes. */
	private int sliceSize = 256 * 1024;

	/** Store all slices after this threshold value. */
	private int sliceMemoryThreshold = 2;

	private AtomicLong packNumber = new AtomicLong();

	private final PublisherBuffer buffer;

	/**
	 * @param buffer
	 */
	public PublisherPackFactory(PublisherBuffer buffer) {
		this.buffer = buffer;
	}

	/**
	 * @param size
	 *            the size of each slice in bytes
	 */
	public void setSliceSize(int size) {
		sliceSize = size;
	}

	/**
	 * Set the number of slices that are initially stored in memory. Slices
	 * greater than this threshold will start in the stored state and be loaded
	 * on first access.
	 *
	 * @param threshold
	 */
	public void setSliceMemoryThreshold(int threshold) {
		sliceMemoryThreshold = threshold;
	}

	/**
	 * @param consumers
	 *            the number of consumers expected to use this pack
	 * @param repositoryName
	 * @param refUpdates
	 * @param repository
	 * @return new publisher pack with created slices
	 * @throws IOException
	 */
	public PublisherPack buildPack(int consumers, String repositoryName,
			Collection<ReceiveCommand> refUpdates, Repository repository)
			throws IOException {
		long packNum = packNumber.incrementAndGet();
		List<PublisherPackSlice> slices = new ArrayList<PublisherPackSlice>();
		OutputStream packOut = createSliceStream(consumers, slices, packNum);
		PacketLineOut packDataLine = new PacketLineOut(packOut);

		Set<ObjectId> remoteObjects = new HashSet<ObjectId>();
		Set<ObjectId> newObjects = new HashSet<ObjectId>();
		PackWriter writer = new PackWriter(repository);
		boolean writePack = false;
		try {
			// This assumes clients have all expected objects
			for (ReceiveCommand r : refUpdates) {
				// Write commands
				StringBuilder sb = new StringBuilder();
				sb.append(r.getOldId().name());
				sb.append(" ");
				sb.append(r.getNewId().name());
				sb.append(" ");
				sb.append(r.getRefName());
				packDataLine.writeString(sb.toString());

				if (r.getType() != Type.DELETE)
					writePack = true;
				if (!ObjectId.zeroId().equals(r.getOldId()))
					remoteObjects.add(r.getOldId());
				if (!ObjectId.zeroId().equals(r.getNewId()))
					newObjects.add(r.getNewId());
			}

			packDataLine.end();

			if (writePack) {
				writer.setUseCachedPacks(true);
				writer.setThin(true);
				writer.setReuseValidatingObjects(false);
				writer.setDeltaBaseAsOffset(true);
				writer.preparePack(NullProgressMonitor.INSTANCE, newObjects,
						remoteObjects);
				writer.writePack(NullProgressMonitor.INSTANCE,
						NullProgressMonitor.INSTANCE, packOut);
			}
			packOut.close();
		} catch (IOException e) {
			for (PublisherPackSlice s : slices)
				s.close();
			throw e;
		} finally {
			writer.release();
		}
		return new PublisherPack(repositoryName, refUpdates, slices, packNum);
	}

	/**
	 * Create a new slice that loads into memory on the first access.
	 *
	 * @param packNum
	 * @param sliceNumber
	 * @param consumers
	 * @param buf
	 * @return a new slice using this data. Defaults to file-backed slice.
	 */
	protected PublisherPackSlice createSlice(
			long packNum, int sliceNumber, int consumers, byte[] buf) {
		LoadPolicy policy = new LoadPolicy() {
			private AtomicBoolean called = new AtomicBoolean();

			public boolean shouldLoad(PublisherPackSlice slice) {
				return !called.getAndSet(true);
			}
		};

		LoadCallback callback = new LoadCallback() {
			public void loaded(PublisherPackSlice slice) {
				buffer.allocate(slice);
			}

			public void stored(PublisherPackSlice slice) {
				buffer.deallocate(slice);
			}
		};

		return new PublisherPackSliceFile(policy, callback, consumers, buf,
				"pack-" + packNum + "-" + sliceNumber + ".slice");
	}

	private OutputStream createSliceStream(final int consumers,
			final List<PublisherPackSlice> slices, final long packNum) {
		return new OutputStream() {
			int bufLen;

			byte[] buf = new byte[sliceSize];

			@Override
			public void write(byte[] b, int off, int len) throws IOException {
				while (len > 0) {
					int writeLen = Math.min(len, sliceSize - bufLen);
					System.arraycopy(b, off, buf, bufLen, writeLen);
					bufLen += writeLen;
					if (writeLen < len)
						appendSlice();
					len -= writeLen;
					off += writeLen;
				}
			}

			@Override
			public void write(int b) throws IOException {
				write(new byte[] { (byte) b });
			}

			@Override
			public void close() throws IOException {
				appendSlice();
			}

			private void appendSlice() throws IOException {
				if (bufLen == 0)
					return;
				if (bufLen < buf.length)
					buf = Arrays.copyOf(buf, bufLen);
				PublisherPackSlice s = createSlice(
						packNum, slices.size(), consumers, buf);
				if (slices.size() >= sliceMemoryThreshold)
					s.store(false);
				else
					buffer.allocate(s);
				slices.add(s);
				bufLen = 0;
				buf = new byte[sliceSize];
			}
		};
	}
}
