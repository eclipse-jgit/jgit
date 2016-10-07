package org.eclipse.jgit.lfs;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.concurrent.Callable;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lfs.internal.LfsText;
import org.eclipse.jgit.lfs.lib.Constants;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.SystemReader;

/**
 * Installs all required LFS properties for the current user, analogous to 'git
 * lfs install', but defaulting to using JGit builtin hooks.
 */
public class InstallLfsCommand implements Callable<Void>{

	private static final String[] ARGS_USER = new String[] { "lfs", "install" }; //$NON-NLS-1$//$NON-NLS-2$

	private static final String[] ARGS_LOCAL = new String[] { "lfs", "install", //$NON-NLS-1$//$NON-NLS-2$
			"--local" }; //$NON-NLS-1$

	private Repository repository;

	@Override
	public Void call() throws Exception {
		StoredConfig cfg = null;
		if (repository == null)
			cfg = loadUserConfig();
		else
			cfg = repository.getConfig();

		cfg.setBoolean(ConfigConstants.CONFIG_FILTER_SECTION, Constants.LFS,
				ConfigConstants.CONFIG_KEY_USEJGITBUILTIN, true);
		cfg.setBoolean(ConfigConstants.CONFIG_FILTER_SECTION, Constants.LFS,
				ConfigConstants.CONFIG_KEY_REQUIRED, true);

		cfg.save();

		// try to run git lfs install, we really don't care if it is present
		// and/or works here (yet).
		ProcessBuilder builder = FS.DETECTED.runInShell("git", //$NON-NLS-1$
				repository == null ? ARGS_USER : ARGS_LOCAL);
		if (repository != null) {
			builder.directory(repository.isBare() ? repository.getDirectory()
					: repository.getWorkTree());
		}
		FS.DETECTED.runProcess(builder, null, null, (String) null);

		return null;
	}

	/**
	 * @param repo
	 *            the repository to install LFS into locally instead of the user
	 *            configuration
	 */
	public void setRepository(Repository repo) {
		this.repository = repo;
	}

	private StoredConfig loadUserConfig() throws IOException {
		FileBasedConfig c = SystemReader.getInstance().openUserConfig(null,
				FS.DETECTED);

		try {
			c.load();
		} catch (ConfigInvalidException e1) {
			IOException e2 = new IOException(MessageFormat
					.format(LfsText.get().userConfigInvalid, c.getFile()
					.getAbsolutePath(), e1));
			e2.initCause(e1);
			throw e2;
		}

		return c;
	}

}
