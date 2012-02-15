/*
 * Copyright (C) 2012, Denis Bardadym <bardadymchik@gmail.com>
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
package org.eclipse.jgit.api;

import java.io.IOException;
import java.text.MessageFormat;
import org.eclipse.jgit.JGitText;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.NotSymbolicRefException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.api.errors.RefUpdateException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;

/**
 * A class used to execute a {@code SymbolicRef} command. It has setters for all
 * supported options and arguments of this command and a {@link #call()} method
 * to finally execute the command.
 *
 * @see <a
 * href="http://www.kernel.org/pub/software/scm/git/docs/git-symbolic-ref.html"
 * >Git documentation about Symbolic-Ref</a>
 */
public class SymbolicRefCommand extends GitCommand<String> {

    private String name;
    private String message;
    private String newRef;

    /**
     * @param repo
     */
    protected SymbolicRefCommand(Repository repo) {
        super(repo);
    }

    /**
     * Set the name of ref for read or update
     *
     * @param name of ref
     * @return this
     */
    public SymbolicRefCommand setName(String name) {
        checkCallable();
        this.name = name;
        return this;
    }

    /**
     * Set the message of ref log (will be used if {@code newRef} set)
     *
     * @param message
     * @return this
     */
    public SymbolicRefCommand setMessage(String message) {
        checkCallable();
        this.message = message;
        return this;
    }

    /**
     * Set the new ref for update or create
     *
     * @param ref new ref name
     * @return this
     */
    public SymbolicRefCommand setNewRef(String ref) {
        checkCallable();
        this.newRef = ref;
        return this;
    }

    /**
     * Executes the {@code symbolic-ref} command with all the options and
     * parameters collected by the setter methods of this class. Each instance
     * of this class should only be used for one invocation of the command
     * (means: one call to {@link #call()})
     *
     * @return a {@link String} name representing the successful read of
     * symbolic ref
     * @throws NotSymbolicRefException when called on a not a symbolic ref
     * @throws RefNotFoundException ref does not exists or null set
     * @throws RefUpdateException if ref could not be updated
     * @throws JGitInternalException a low-level exception of JGit has occurred
     * The original exception can be retrieved by calling
     *             {@link Exception#getCause()}. Expect only
     *             {@code IOException's} to be wrapped.
     */
    public String call() throws Exception {
        try {
            Ref ref = repo.getRef(name);
            if (ref == null) {
                if (newRef == null) {
                    throw new RefNotFoundException(JGitText.get().noSuchRef);
                } else {//the ref is not exists
                    tryUpdateRef();
                    return null;
                }
            } else {
                if (ref.isSymbolic()) {
                    if (newRef == null) {
                        return ref.getTarget().getName();
                    } else {//the ref is exists - update it
                        tryUpdateRef();
                        return null;
                    }
                } else {
                    throw new NotSymbolicRefException(MessageFormat.format(JGitText.get().aSymbolicRefRequired, name));
                }
            }
        } catch (IOException ioe) {
            throw new JGitInternalException(ioe.getMessage(), ioe);
        }
    }

    private RefUpdate.Result tryUpdateRef() throws IOException, RefUpdateException {
        RefUpdate refUpdate = repo.updateRef(name);
        if (message != null) {
            refUpdate.setRefLogMessage(message, false);
        }
        RefUpdate.Result updateResult = refUpdate.link(newRef);

        setCallable(false);

        if (updateResult == RefUpdate.Result.NEW || updateResult == RefUpdate.Result.FORCED) {
            return updateResult;
        } else {
            throw new RefUpdateException(null, null, updateResult);
        }
    }

    /**
     * @return ref log message (-m param)
     */
    public String getMessage() {
        return message;
    }

    /**
     * @return symbolic ref name
     */
    public String getName() {
        return name;
    }

    /**
     * @return new ref name
     */
    public String getNewRef() {
        return newRef;
    }
}
