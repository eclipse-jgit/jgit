package org.eclipse.jgit.lfs;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.concurrent.Callable;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lfs.internal.LfsText;
import org.eclipse.jgit.lfs.lib.Constants;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.SystemReader;

/**
 * Installs all required LFS properties for the current user, analogous to 'git
 * lfs install', but defaulting to using JGit builtin hooks.
 */
public class InstallLfsCommand implements Callable<Void>{

	@Override
	public Void call() throws Exception {
		StoredConfig cfg = loadUserConfig();

		cfg.setBoolean(ConfigConstants.CONFIG_FILTER_SECTION, Constants.LFS,
				ConfigConstants.CONFIG_KEY_USEJGITBUILTIN, true);
		cfg.setBoolean(ConfigConstants.CONFIG_FILTER_SECTION, Constants.LFS,
				ConfigConstants.CONFIG_KEY_REQUIRED, true);

		cfg.save();

		// try to run git lfs install, we really don't care if it is present
		// and/or works here (yet).
		FS.DETECTED.runProcess(FS.DETECTED.runInShell("git", //$NON-NLS-1$
				new String[] { "lfs", "install" }), //$NON-NLS-1$ //$NON-NLS-2$
				null, null, (String) null);

		return null;
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
