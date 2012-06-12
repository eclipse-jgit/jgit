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

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * A PublisherPack is a single pack update for the listed commands. It may be
 * split into multiple PublisherPackSlices, each of which may be stored in
 * memory or on disk. Each pack must have a unique identifier that will be
 * passed to clients after this pack has been sent to support fast-reconnect
 * into the update stream. This lets the Publisher know if a client received
 * all of the pack if it sends this pack's number when it reconnects.
 */
public class PublisherPack {
	/** Unique number for this pack */
	private final long packNumber;

	/** The Slices of data for this pack. This list will not change. */
	private List<PublisherPackSlice> dataSlices;

	private Collection<ReceiveCommand> commands;

	private String repositoryName;

	/**
	 * @param name
	 * @param updates
	 * @param slices
	 * @param number
	 */
	public PublisherPack(String name, Collection<ReceiveCommand> updates,
			List<PublisherPackSlice> slices, long number) {
		repositoryName = name;
		commands = updates;
		dataSlices = Collections.unmodifiableList(slices);
		packNumber = number;
	}

	/** @return an iterator for the slices in this pack */
	public Iterator<PublisherPackSlice> getSlices() {
		return dataSlices.iterator();
	}

	/** @return the unique number for this pack */
	public long getPackNumber() {
		return packNumber;
	}

	/**
	 * Increment each slice's reference count by 1.
	 *
	 * @return true if all slices were incremented by 1 before they reached 0
	 */
	public boolean incrementOpen() {
		for (PublisherPackSlice s : dataSlices) {
			if (!s.incrementOpen()) {
				// One pack failed, decrement the ones already incremented
				for (PublisherPackSlice s2 : dataSlices) {
					s2.release();
					if (s2 == s)
						return false;
				}
			}
		}
		return true;
	}

	/** @return the commands for this pack */
	public Collection<ReceiveCommand> getCommands() {
		return commands;
	}

	/** @return the name of this repository */
	public String getRepositoryName() {
		return repositoryName;
	}

	/**
	 * @param subscribeSpecs
	 * @return true if all of this pack's refs match {@code subscribeSpecs}.
	 */
	public boolean match(Collection<SubscribeSpec> subscribeSpecs) {
		for (ReceiveCommand cmd : commands) {
			boolean matched = false;
			for (SubscribeSpec spec : subscribeSpecs)
				if (spec.isMatch(cmd.getRefName())) {
					matched = true;
					break;
				}
			if (!matched)
				return false;
		}
		return true;
	}

	/** Decrement the reference counters for all of the data slices. */
	public void release() {
		for (PublisherPackSlice s : dataSlices)
			s.release();
	}
}
