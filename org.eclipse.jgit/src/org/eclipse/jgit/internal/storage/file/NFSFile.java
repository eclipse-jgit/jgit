package org.eclipse.jgit.internal.storage.file;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ConfigConstants;

/**
 * NFSFile extends {@link java.io.File} to provide improved functionality on NFS
 * filesystems.
 *
 */
public class NFSFile extends File {
	private static final long serialVersionUID = 1L;

	private final Config config;

	/**
	 * Resolve this file to its actual path name that the JRE can use.
	 * <p>
	 * This method can be relatively expensive. Computing a translation may
	 * require forking an external process per path name translated. Callers
	 * should try to minimize the number of translations necessary by caching
	 * the results.
	 * <p>
	 * Not all platforms and JREs require path name translation. Currently only
	 * Cygwin on Win32 require translation for Cygwin based paths.
	 *
	 * @param dir
	 *            directory relative to which the path name is.
	 * @param name
	 *            path name to translate.
	 * @param config
	 * @return the translated path. {@code new NFSFile(dir,name,config)} if this
	 *         platform does not require path name translation.
	 */
	public static NFSFile resolve(final File dir, final String name,
			final Config config) {
		final NFSFile abspn = new NFSFile(name, config);
		if (abspn.isAbsolute())
			return abspn;
		return new NFSFile(dir, name, config);
	}

	/**
	 * Wraps {@link File#File(File, String)}
	 *
	 * @param config
	 * @param parent
	 *            The parent pathname string
	 * @param child
	 *            The child pathname string
	 * @throws NullPointerException
	 *             If {@code child} is {@code null}
	 */
	public NFSFile(File parent, String child, Config config) {
		super(parent, child);
		this.config = config;
	}

	/**
	 * Wraps {@link File#File(String)}
	 *
	 * @param config
	 * @param pathname
	 *            A pathname string
	 * @throws NullPointerException
	 *             If the {@code pathname} argument is {@code null}
	 */
	public NFSFile(String pathname, Config config) {
		super(pathname);
		this.config = config;
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Uses the value of
	 * {@code ConfigConstants.CONFIG_KEY_REFRESHFOLDERSTATBEFORE} to optionally
	 * flush the NFS cache before checking file existence.
	 */
	@Override
	public boolean exists() {
		try {
			refreshFolderStats();
		} catch (IOException e) {
			return false; // contract of exists says to return false for any I/O
							// error
		}
		return super.exists();
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Uses the value of
	 * {@code ConfigConstants.CONFIG_KEY_REFRESHFOLDERSTATBEFORE} to optionally
	 * flush the NFS cache before checking the modification time.
	 */
	@Override
	public long lastModified() {
		try {
			refreshFolderStats();
		} catch (IOException e) {
			return 0L; // contract of lastModified says to return 0L for any I/O
						// error
		}
		return super.lastModified();
	}

	private void refreshFolderStats() throws IOException {
		int refreshFolderStatBefore = config.getInt(
				ConfigConstants.CONFIG_CORE_SECTION,
				ConfigConstants.CONFIG_KEY_REFRESHFOLDERSTATBEFORE, 0);
		if (refreshFolderStatBefore > 0) {
			try (DirectoryStream<Path> stream = Files
					.newDirectoryStream(this.toPath().getParent())) {
				// open and close the directory to invalidate NFS attribute
				// cache
			}
		}
	}

}
