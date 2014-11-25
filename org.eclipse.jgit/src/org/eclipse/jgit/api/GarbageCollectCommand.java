/*
 * Copyright (C) 2012, Matthias Sohn <matthias.sohn@sap.com>
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
import java.text.ParseException;
import java.util.Date;
import java.util.Properties;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.storage.dfs.DfsGarbageCollector;
import org.eclipse.jgit.internal.storage.dfs.DfsRepository;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.internal.storage.file.GC;
import org.eclipse.jgit.internal.storage.file.GC.RepoStatistics;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.storage.pack.PackConfig;
import org.eclipse.jgit.util.GitDateParser;

/**
 * A class used to execute a {@code gc} command. It has setters for all
 * supported options and arguments of this command and a {@link #call()} method
 * to finally execute the command. Each instance of this class should only be
 * used for one invocation of the command (means: one call to {@link #call()})
 *
 * @since 2.2
 * @see <a href="http://www.kernel.org/pub/software/scm/git/docs/git-gc.html"
 *      >Git documentation about gc</a>
 */
public class GarbageCollectCommand extends GitCommand<Properties> {
	/**
	 * Default value of maximum delta chain depth during aggressive garbage
	 * collection: {@value}
	 *
	 * @since 3.6
	 */
	public static final int DEFAULT_GC_AGGRESSIVE_DEPTH = 250;

	/**
	 * Default window size during packing during aggressive garbage collection:
	 * * {@value}
	 *
	 * @since 3.6
	 */
	public static final int DEFAULT_GC_AGGRESSIVE_WINDOW = 250;

	private ProgressMonitor monitor;

	private Date expire;

	private PackConfig pconfig;

	/**
	 * @param repo
	 */
	protected GarbageCollectCommand(Repository repo) {
		super(repo);
		pconfig = new PackConfig(repo);
	}

	/**
	 * @param monitor
	 *            a progress monitor
	 * @return this instance
	 */
	public GarbageCollectCommand setProgressMonitor(ProgressMonitor monitor) {
		this.monitor = monitor;
		return this;
	}

	/**
	 * During gc() or prune() each unreferenced, loose object which has been
	 * created or modified after <code>expire</code> will not be pruned. Only
	 * older objects may be pruned. If set to null then every object is a
	 * candidate for pruning. Use {@link GitDateParser} to parse time formats
	 * used by git gc.
	 *
	 * @param expire
	 *            minimal age of objects to be pruned.
	 * @return this instance
	 */
	public GarbageCollectCommand setExpire(Date expire) {
		this.expire = expire;
		return this;
	}

	/**
	 * Whether to use aggressive mode or not. If set to true JGit behaves more
	 * similar to native git's "git gc --aggressive". If set to
	 * <code>true</code> compressed objects found in old packs are not reused
	 * but every object is compressed again. Configuration variables
	 * pack.window and pack.depth are set to 250 for this GC.
	 *
	 * @since 3.6
	 * @param aggressive
	 *            whether to turn on or off aggressive mode
	 * @return this instance
	 */
	public GarbageCollectCommand setAggressive(boolean aggressive) {
		if (aggressive) {
			StoredConfig repoConfig = repo.getConfig();
			pconfig.setDeltaSearchWindowSize(repoConfig.getInt(
					ConfigConstants.CONFIG_GC_SECTION,
					ConfigConstants.CONFIG_KEY_AGGRESSIVE_WINDOW,
					DEFAULT_GC_AGGRESSIVE_WINDOW));
			pconfig.setMaxDeltaDepth(repoConfig.getInt(
					ConfigConstants.CONFIG_GC_SECTION,
					ConfigConstants.CONFIG_KEY_AGGRESSIVE_DEPTH,
					DEFAULT_GC_AGGRESSIVE_DEPTH));
			pconfig.setReuseObjects(false);
		} else
			pconfig = new PackConfig(repo);
		return this;
	}

	@Override
	public Properties call() throws GitAPIException {
		checkCallable();

		try {
			if (repo instanceof FileRepository) {
				GC gc = new GC((FileRepository) repo);
				gc.setPackConfig(pconfig);
				gc.setProgressMonitor(monitor);
				if (this.expire != null)
					gc.setExpire(expire);

				try {
					gc.gc();
					return toProperties(gc.getStatistics());
				} catch (ParseException e) {
					throw new JGitInternalException(JGitText.get().gcFailed, e);
				}
			} else if (repo instanceof DfsRepository) {
				DfsGarbageCollector gc =
					new DfsGarbageCollector((DfsRepository) repo);
				gc.setPackConfig(pconfig);
				gc.pack(monitor);
				return new Properties();
			} else {
				throw new UnsupportedOperationException(MessageFormat.format(
						JGitText.get().unsupportedGC,
						repo.getClass().toString()));
			}
		} catch (IOException e) {
			throw new JGitInternalException(JGitText.get().gcFailed, e);
		}
	}

	/**
	 * Computes and returns the repository statistics.
	 *
	 * @return the repository statistics
	 * @throws GitAPIException
	 *             thrown if the repository statistics cannot be computed
	 * @since 3.0
	 */
	public Properties getStatistics() throws GitAPIException {
		try {
			if (repo instanceof FileRepository) {
				GC gc = new GC((FileRepository) repo);
				return toProperties(gc.getStatistics());
			} else {
				return new Properties();
			}
		} catch (IOException e) {
			throw new JGitInternalException(
					JGitText.get().couldNotGetRepoStatistics, e);
		}
	}

	@SuppressWarnings("boxing")
	private static Properties toProperties(RepoStatistics stats) {
		Properties p = new Properties();
		p.put("numberOfLooseObjects", stats.numberOfLooseObjects); //$NON-NLS-1$
		p.put("numberOfLooseRefs", stats.numberOfLooseRefs); //$NON-NLS-1$
		p.put("numberOfPackedObjects", stats.numberOfPackedObjects); //$NON-NLS-1$
		p.put("numberOfPackedRefs", stats.numberOfPackedRefs); //$NON-NLS-1$
		p.put("numberOfPackFiles", stats.numberOfPackFiles); //$NON-NLS-1$
		p.put("sizeOfLooseObjects", stats.sizeOfLooseObjects); //$NON-NLS-1$
		p.put("sizeOfPackedObjects", stats.sizeOfPackedObjects); //$NON-NLS-1$
		return p;
	}
}
