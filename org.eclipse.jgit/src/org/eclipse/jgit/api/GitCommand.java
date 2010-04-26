/*
 * Copyright (C) 2010, Christian Halstrick <christian.halstrick@sap.com> and
 * other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v1.0 which accompanies this
 * distribution, is reproduced below, and is available at
 * http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * - Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the names of its
 * contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package org.eclipse.jgit.api;

import java.util.concurrent.Callable;
import org.eclipse.jgit.lib.Repository;

/**
 * Common superclass of all commands in the package {@code org.eclipse.jgit.api}
 * <p>
 * This class ensures that all commands fulfill the {@link Callable} interface.
 * It also has a property {@link #repo} holding a reference to the git
 * {@link Repository} this command should work with.
 * <p>
 * Finally this class stores a boolean value as state. A method
 * {@link #checkState()} is provided which will throw a
 * {@link IllegalStateException} when the state is {@code false}. This can be
 * used by subclasses to introduce for example the policy to allow only one call
 * to {@link #call()} per instance. Whenever the command was successfully
 * invoked the state is set to {@code false} with {@link #setState(boolean)}.
 * Each method in the subclass will then in the very beginning call
 * {@link #checkState()} leading to the desired behavior that when the state is
 * false no method can be successfully called.
 *
 * @param <T>
 *            the return type which is expected from {@link #call()}
 */
public abstract class GitCommand<T> implements Callable<T> {
	/** The repository this command is working with */
	protected Repository repo;

	/**
	 * a state which can be checked with {@link #checkState()} and set with
	 * {@link #setState(boolean)}
	 */
	private boolean state = true;

	/**
	 * Creates a new command which interacts with a single repository
	 *
	 * @param repo
	 *            the {@link Repository} this command should interact with
	 */
	protected GitCommand(Repository repo) {
		this.repo = repo;
	}

	/**
	 * @return the {@link Repository} this command is interacting with
	 */
	public Repository getRepository() {
		return repo;
	}

	/**
	 * set's the state which can be checked with {@link #checkState()}
	 *
	 * @param state
	 *            the new boolean value for the state
	 */
	protected void setState(boolean state) {
		this.state = state;
	}

	/**
	 * Checks that the current {@link #state} is {@code true}. If not then an
	 * {@link IllegalStateException} is thrown
	 *
	 * @throws IllegalStateException
	 *             when this method was called and the current {@link #state}
	 *             was {@code false}
	 */
	protected void checkState() {
		if (!state)
			throw new IllegalStateException("Command "
					+ this.getClass().getName()
					+ " was called in the wrong state");
	}
}
