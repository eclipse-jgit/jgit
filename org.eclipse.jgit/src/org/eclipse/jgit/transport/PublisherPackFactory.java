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
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.pack.PackWriter;
import org.eclipse.jgit.transport.Publisher.PublisherException;
import org.eclipse.jgit.transport.PublisherPackSlice.Allocator;
import org.eclipse.jgit.transport.PublisherPackSlice.Deallocator;
import org.eclipse.jgit.transport.PublisherPackSlice.LoadPolicy;
import org.eclipse.jgit.transport.ReceiveCommand.Type;

/**
 * Generates PublisherPacks comprised of PublisherPackSlices.
 */
public class PublisherPackFactory {
	/** Size of each slice in bytes. */
	private int sliceSize = 256 * 1024;

	/** Store all slices after this threshold value. */
	private int sliceMemoryInMemoryLimit = 2;

	private final PublisherMemoryPool buffer;

	/**
	 * @param buffer
	 */
	public PublisherPackFactory(PublisherMemoryPool buffer) {
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
	public void setSliceInMemoryLimit(int threshold) {
		sliceMemoryInMemoryLimit = threshold;
	}

	/**
	 * @param refUpdates
	 * @param repository
	 * @param existingSpecs
	 * @param packId
	 * @return new publisher pack with created slices
	 * @throws IOException
	 */
	public PublisherPack buildPack(Repository repository,
			Collection<ReceiveCommand> refUpdates,
			Collection<SubscribeSpec> existingSpecs, String packId)
			throws IOException {
		// Generate a list of ObjectIds that we expect clients to have because
		// they are subscribed to existingSpecs. Don't include ObjectIds that
		// match refUpdates because this is called post-receive. Instead, assume
		// the client has the old ObjectId for that update.
		Set<String> updatedRefs = new LinkedHashSet<String>();
		for (ReceiveCommand rc : refUpdates)
			updatedRefs.add(rc.getRefName());
		Set<ObjectId> existingObjects = new HashSet<ObjectId>();
		RefDatabase refdb = repository.getRefDatabase();
		Collection<Ref> matchingRefs = Collections.emptyList();
		// Generate a list of supporting ObjectIds that the client must have
		for (SubscribeSpec spec : existingSpecs) {
			String refName = spec.getRefName();
			if (spec.isWildcard()) {
				String refNamePrefix = SubscribeSpec.stripWildcard(refName);
				Map<String, Ref> refMap = refdb.getRefs(refNamePrefix);
				for (String updatedRef : updatedRefs) {
					if (updatedRef.startsWith(refNamePrefix))
						refMap.remove(updatedRef.substring(
								refNamePrefix.length()));
				}
				matchingRefs = refMap.values();
			} else if (updatedRefs.contains(refName))
				continue;
			else {
				Ref ref = refdb.getRef(refName);
				if (ref == null)
					continue;
				matchingRefs = Collections.singleton(ref);
			}

			for (Ref r : matchingRefs)
				existingObjects.add(r.getLeaf().getObjectId());
		}
		List<PublisherPackSlice> slices = new ArrayList<PublisherPackSlice>();
		OutputStream packOut = createSliceStream(slices, packId);
		PacketLineOut packDataLine = new PacketLineOut(packOut);

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
					existingObjects.add(r.getOldId());
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
						existingObjects);
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
		return new PublisherPack(slices);
	}

	/**
	 * Create a new slice that loads into memory on the first access.
	 *
	 * @param packId
	 * @param sliceNumber
	 * @param buf
	 * @return a new slice using this data. Defaults to file-backed slice.
	 */
	protected PublisherPackSlice createSlice(
			String packId, int sliceNumber, byte[] buf) {
		LoadPolicy policy = new LoadPolicy() {
			private AtomicBoolean called = new AtomicBoolean();

			public boolean shouldLoad(PublisherPackSlice slice) {
				return !called.getAndSet(true);
			}
		};

		Allocator allocator = new Allocator() {
			public Deallocator allocate(final PublisherPackSlice slice)
					throws PublisherException {
				return buffer.allocate(slice);
			}
		};

		return new PublisherPackSliceFile(policy, allocator, buf, packId + "-"
				+ sliceNumber + ".slice");
	}

	private OutputStream createSliceStream(
			final List<PublisherPackSlice> slices, final String packId) {
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
				PublisherPackSlice s = createSlice(packId, slices.size(), buf);
				if (slices.size() >= sliceMemoryInMemoryLimit) {
					s.setDeallocator(Deallocator.INSTANCE);
					s.store();
				} else
					s.setDeallocator(buffer.allocate(s));
				slices.add(s);
				bufLen = 0;
				buf = new byte[sliceSize];
			}
		};
	}
}
