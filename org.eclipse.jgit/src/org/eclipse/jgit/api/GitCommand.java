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

import java.text.MessageFormat;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Repository;

/**
 * Common superclass of all commands in the package {@code org.eclipse.jgit.api}
 * <p>
 * This class ensures that all commands fulfill the {@link Callable} interface.
 * It also has a property {@link #repo} holding a reference to the git
 * {@link Repository} this command should work with.
 * <p>
 * Finally this class stores a state telling whether it is allowed to call
 * {@link #call()} on this instance. Instances of {@link GitCommand} can only be
 * used for one single successful call to {@link #call()}. Afterwards this
 * instance may not be used anymore to set/modify any properties or to call
 * {@link #call()} again. This is achieved by setting the {@link #callable}
 * property to false after the successful execution of {@link #call()} and to
 * check the state (by calling {@link #checkCallable()}) before setting of
 * properties and inside {@link #call()}.
 *
 * @param <T>
 *            the return type which is expected from {@link #call()}
 */
public abstract class GitCommand<T> implements Callable<T> {
	/** The repository this command is working with */
	final protected Repository repo;

	/**
	 * a state which tells whether it is allowed to call {@link #call()} on this
	 * instance.
	 */
	private AtomicBoolean callable = new AtomicBoolean(true);

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
	 * Set's the state which tells whether it is allowed to call {@link #call()}
	 * on this instance. {@link #checkCallable()} will throw an exception when
	 * called and this property is set to {@code false}
	 *
	 * @param callable
	 *            if <code>true</code> it is allowed to call {@link #call()} on
	 *            this instance.
	 */
	protected void setCallable(boolean callable) {
		this.callable.set(callable);
	}

	/**
	 * Checks that the property {@link #callable} is {@code true}. If not then
	 * an {@link IllegalStateException} is thrown
	 *
	 * @throws IllegalStateException
	 *             when this method is called and the property {@link #callable}
	 *             is {@code false}
	 */
	protected void checkCallable() {
		if (!callable.get())
			throw new IllegalStateException(MessageFormat.format(
					JGitText.get().commandWasCalledInTheWrongState
					, this.getClass().getName()));
	}

	/**
	 * Executes the command
	 *
	 * @return T a result. Each command has its own return type
	 * @throws GitAPIException
	 *             or subclass thereof when an error occurs
	 */
	@Override
	public abstract T call() throws GitAPIException;
}
