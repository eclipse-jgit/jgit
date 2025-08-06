/*
 * Copyright (C) 2025, Google LLC. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.hooks;

import java.io.IOException;
import java.io.PrintStream;
import java.util.List;
import org.eclipse.jgit.api.errors.AbortedByHookException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

/**
 * The <code>pre-fetch</code> hook implementation. The pre-fetch hook runs before any object
 * transfer from a remote if at least one local ref update is identified.
 *
 * @since 7.4
 */
public class PreFetchHook extends GitHook<String> {

    /** Constant indicating the name of the pre-fetch hook. */
    public static final String NAME = "pre-fetch"; // $NON-NLS-1$

    /** The remote being fetched from */
    private String remoteName;

    /** The remote's url */
    private String url;

    /** List of refs we will actually wind up asking to obtain from remote. */
    private List<Ref> refs;

    private boolean dryRun;

    /**
     * Constructor for PreFetchHook
     *
     * <p>This constructor will use the default output and error streams.
     *
     * @param repo the repository
     */
    public PreFetchHook(Repository repo) {
        super(repo, null, null);
    }

    /**
     * Constructor for PreFetchHook
     *
     * @param repo The repository
     * @param outputStream The output stream the hook must use. {@code null} is allowed, in which case
     *     the hook will use {@code System.out}.
     * @param errorStream The error stream the hook must use. {@code null} is allowed, in which case
     *     the hook will use {@code System.err}.
     */
    public PreFetchHook(Repository repo, PrintStream outputStream, PrintStream errorStream) {
        super(repo, outputStream, errorStream);
    }

    /**
     * Set remote name
     *
     * @param remote remote name
     * @since 7.4
     */
    public void setRemote(String remote) {
        this.remoteName = remote;
    }

    /**
     * Get remote name
     *
     * @return remote name or null
     */
    protected String getRemoteName() {
        return remoteName;
    }

    /**
     * Set remote url
     *
     * @param url remote url
     * @since 7.4
     */
    public void setUrl(String url) {
        this.url = url;
    }

    /**
     * Get the remote's url
     *
     * @return url
     */
    public String getUrl() {
        return url;
    }

    /**
     * Sets the list of refs we will actually wind up asking to obtain from remote.
     *
     * @param refs the list of refs
     * @since 7.4
     */
    public void setRefs(List<Ref> refs) {
        this.refs = refs;
    }

    /**
     * Get the list of refs we will actually wind up asking to obtain from remote.
     *
     * @return refs
     */
    public List<Ref> getRefs() {
        return refs;
    }

    /**
     * Sets whether the fetch is a dry run.
     *
     * @param dryRun {@code true} if the fetch is a dry run, {@code false} otherwise
     * @since 7.4
     */
    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    /**
     * Tells whether the fetch is a dry run.
     *
     * @return {@code true} if the fetch is a dry run, {@code false} otherwise
     */
    protected boolean isDryRun() {
        return dryRun;
    }

    @Override
    public String getHookName() {
        return NAME;
    }

    @Override
    public String call() throws IOException, AbortedByHookException {
        if (canRun()) {
            doRun();
        }
        return ""; //$NON-NLS-1$
    }

    /**
     * @return {@code true}
     */
    private boolean canRun() {
        return true;
    }
}
