/*
 * Copyright (C) 2010, Robin Rosenberg and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.util;

import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_CORE_SECTION;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_SUPPORTSATOMICFILECREATION;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileStore;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.errors.CommandFailedException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base FS for POSIX based systems
 *
 * @since 3.0
 */
public class FS_POSIX extends FS {
	private static final Logger LOG = LoggerFactory.getLogger(FS_POSIX.class);

	private static final int DEFAULT_UMASK = 0022;
	private volatile int umask = -1;

	private static final Map<FileStore, Boolean> CAN_HARD_LINK = new ConcurrentHashMap<>();

	private volatile AtomicFileCreation supportsAtomicFileCreation = AtomicFileCreation.UNDEFINED;

	private enum AtomicFileCreation {
		SUPPORTED, NOT_SUPPORTED, UNDEFINED
	}

	/**
	 * Default constructor.
	 */
	protected FS_POSIX() {
	}

	/**
	 * Constructor
	 *
	 * @param src
	 *            FS to copy some settings from
	 */
	protected FS_POSIX(FS src) {
		super(src);
		if (src instanceof FS_POSIX) {
			umask = ((FS_POSIX) src).umask;
		}
	}

	/** {@inheritDoc} */
	@Override
	public FS newInstance() {
		return new FS_POSIX(this);
	}

	/**
	 * Set the umask, overriding any value observed from the shell.
	 *
	 * @param umask
	 *            mask to apply when creating files.
	 * @since 4.0
	 */
	public void setUmask(int umask) {
		this.umask = umask;
	}

	private int umask() {
		int u = umask;
		if (u == -1) {
			u = readUmask();
			umask = u;
		}
		return u;
	}

	/** @return mask returned from running {@code umask} command in shell. */
	private static int readUmask() {
		try {
			Process p = Runtime.getRuntime().exec(
					new String[] { "sh", "-c", "umask" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					null, null);
			try (BufferedReader lineRead = new BufferedReader(
					new InputStreamReader(p.getInputStream(), Charset
							.defaultCharset().name()))) {
				if (p.waitFor() == 0) {
					String s = lineRead.readLine();
					if (s != null && s.matches("0?\\d{3}")) { //$NON-NLS-1$
						return Integer.parseInt(s, 8);
					}
				}
				return DEFAULT_UMASK;
			}
		} catch (Exception e) {
			return DEFAULT_UMASK;
		}
	}

	/** {@inheritDoc} */
	@Override
	protected File discoverGitExe() {
		String path = SystemReader.getInstance().getenv("PATH"); //$NON-NLS-1$
		File gitExe = searchPath(path, "git"); //$NON-NLS-1$

		if (gitExe == null) {
			if (SystemReader.getInstance().isMacOS()) {
				if (searchPath(path, "bash") != null) { //$NON-NLS-1$
					// On MacOSX, PATH is shorter when Eclipse is launched from the
					// Finder than from a terminal. Therefore try to launch bash as a
					// login shell and search using that.
					String w;
					try {
						w = readPipe(userHome(),
							new String[]{"bash", "--login", "-c", "which git"}, // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
							Charset.defaultCharset().name());
					} catch (CommandFailedException e) {
						LOG.warn(e.getMessage());
						return null;
					}
					if (!StringUtils.isEmptyOrNull(w)) {
						gitExe = new File(w);
					}
				}
			}
		}

		return gitExe;
	}

	/** {@inheritDoc} */
	@Override
	public boolean isCaseSensitive() {
		return !SystemReader.getInstance().isMacOS();
	}

	/** {@inheritDoc} */
	@Override
	public boolean supportsExecute() {
		return true;
	}

	/** {@inheritDoc} */
	@Override
	public boolean canExecute(File f) {
		return FileUtils.canExecute(f);
	}

	/** {@inheritDoc} */
	@Override
	public boolean setExecute(File f, boolean canExecute) {
		if (!isFile(f))
			return false;
		if (!canExecute)
			return f.setExecutable(false, false);

		try {
			Path path = FileUtils.toPath(f);
			Set<PosixFilePermission> pset = Files.getPosixFilePermissions(path);

			// owner (user) is always allowed to execute.
			pset.add(PosixFilePermission.OWNER_EXECUTE);

			int mask = umask();
			apply(pset, mask, PosixFilePermission.GROUP_EXECUTE, 1 << 3);
			apply(pset, mask, PosixFilePermission.OTHERS_EXECUTE, 1);
			Files.setPosixFilePermissions(path, pset);
			return true;
		} catch (IOException e) {
			// The interface doesn't allow to throw IOException
			final boolean debug = Boolean.parseBoolean(SystemReader
					.getInstance().getProperty("jgit.fs.debug")); //$NON-NLS-1$
			if (debug)
				System.err.println(e);
			return false;
		}
	}

	private static void apply(Set<PosixFilePermission> set,
			int umask, PosixFilePermission perm, int test) {
		if ((umask & test) == 0) {
			// If bit is clear in umask, permission is allowed.
			set.add(perm);
		} else {
			// If bit is set in umask, permission is denied.
			set.remove(perm);
		}
	}

	/** {@inheritDoc} */
	@Override
	public ProcessBuilder runInShell(String cmd, String[] args) {
		List<String> argv = new ArrayList<>(4 + args.length);
		argv.add("sh"); //$NON-NLS-1$
		argv.add("-c"); //$NON-NLS-1$
		argv.add("$0 \"$@\""); //$NON-NLS-1$
		argv.add(cmd);
		argv.addAll(Arrays.asList(args));
		ProcessBuilder proc = new ProcessBuilder();
		proc.command(argv);
		return proc;
	}

	/** {@inheritDoc} */
	@Override
	public ProcessResult runHookIfPresent(Repository repository, String hookName,
			String[] args, PrintStream outRedirect, PrintStream errRedirect,
			String stdinArgs) throws JGitInternalException {
		return internalRunHookIfPresent(repository, hookName, args, outRedirect,
				errRedirect, stdinArgs);
	}

	/** {@inheritDoc} */
	@Override
	public boolean retryFailedLockFileCommit() {
		return false;
	}

	/** {@inheritDoc} */
	@Override
	public void setHidden(File path, boolean hidden) throws IOException {
		// no action on POSIX
	}

	/** {@inheritDoc} */
	@Override
	public Attributes getAttributes(File path) {
		return FileUtils.getFileAttributesPosix(this, path);
	}

	/** {@inheritDoc} */
	@Override
	public File normalize(File file) {
		return FileUtils.normalize(file);
	}

	/** {@inheritDoc} */
	@Override
	public String normalize(String name) {
		return FileUtils.normalize(name);
	}

	/** {@inheritDoc} */
	@Override
	public boolean supportsAtomicCreateNewFile() {
		if (supportsAtomicFileCreation == AtomicFileCreation.UNDEFINED) {
			try {
				StoredConfig config = SystemReader.getInstance().getUserConfig();
				String value = config.getString(CONFIG_CORE_SECTION, null,
						CONFIG_KEY_SUPPORTSATOMICFILECREATION);
				if (value != null) {
					supportsAtomicFileCreation = StringUtils.toBoolean(value)
							? AtomicFileCreation.SUPPORTED
							: AtomicFileCreation.NOT_SUPPORTED;
				} else {
					supportsAtomicFileCreation = AtomicFileCreation.SUPPORTED;
				}
			} catch (IOException | ConfigInvalidException e) {
				LOG.warn(JGitText.get().assumeAtomicCreateNewFile, e);
				supportsAtomicFileCreation = AtomicFileCreation.SUPPORTED;
			}
		}
		return supportsAtomicFileCreation == AtomicFileCreation.SUPPORTED;
	}

	@Override
	@SuppressWarnings("boxing")
	/**
	 * {@inheritDoc}
	 * <p>
	 * An implementation of the File#createNewFile() semantics which works also
	 * on NFS. If the config option
	 * {@code core.supportsAtomicCreateNewFile = true} (which is the default)
	 * then simply File#createNewFile() is called.
	 *
	 * But if {@code core.supportsAtomicCreateNewFile = false} then after
	 * successful creation of the lock file a hard link to that lock file is
	 * created and the attribute nlink of the lock file is checked to be 2. If
	 * multiple clients manage to create the same lock file nlink would be
	 * greater than 2 showing the error.
	 *
	 * @see "https://www.time-travellers.org/shane/papers/NFS_considered_harmful.html"
	 *
	 * @deprecated use {@link FS_POSIX#createNewFileAtomic(File)} instead
	 * @since 4.5
	 */
	@Deprecated
	public boolean createNewFile(File lock) throws IOException {
		if (!lock.createNewFile()) {
			return false;
		}
		if (supportsAtomicCreateNewFile()) {
			return true;
		}
		Path lockPath = lock.toPath();
		Path link = null;
		FileStore store = null;
		try {
			store = Files.getFileStore(lockPath);
		} catch (SecurityException e) {
			return true;
		}
		try {
			Boolean canLink = CAN_HARD_LINK.computeIfAbsent(store,
					s -> Boolean.TRUE);
			if (Boolean.FALSE.equals(canLink)) {
				return true;
			}
			link = Files.createLink(
					Paths.get(lock.getAbsolutePath() + ".lnk"), //$NON-NLS-1$
					lockPath);
			Integer nlink = (Integer) (Files.getAttribute(lockPath,
					"unix:nlink")); //$NON-NLS-1$
			if (nlink > 2) {
				LOG.warn(MessageFormat.format(
						JGitText.get().failedAtomicFileCreation, lockPath,
						nlink));
				return false;
			} else if (nlink < 2) {
				CAN_HARD_LINK.put(store, Boolean.FALSE);
			}
			return true;
		} catch (UnsupportedOperationException | IllegalArgumentException e) {
			CAN_HARD_LINK.put(store, Boolean.FALSE);
			return true;
		} finally {
			if (link != null) {
				Files.delete(link);
			}
		}
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * An implementation of the File#createNewFile() semantics which can create
	 * a unique file atomically also on NFS. If the config option
	 * {@code core.supportsAtomicCreateNewFile = true} (which is the default)
	 * then simply Files#createFile() is called.
	 *
	 * But if {@code core.supportsAtomicCreateNewFile = false} then after
	 * successful creation of the lock file a hard link to that lock file is
	 * created and the attribute nlink of the lock file is checked to be 2. If
	 * multiple clients manage to create the same lock file nlink would be
	 * greater than 2 showing the error. The hard link needs to be retained
	 * until the corresponding file is no longer needed in order to prevent that
	 * another process can create the same file concurrently using another NFS
	 * client which might not yet see the file due to caching.
	 *
	 * @see "https://www.time-travellers.org/shane/papers/NFS_considered_harmful.html"
	 * @param file
	 *            the unique file to be created atomically
	 * @return LockToken this lock token must be held until the file is no
	 *         longer needed
	 * @throws IOException
	 * @since 5.0
	 */
	@Override
	public LockToken createNewFileAtomic(File file) throws IOException {
		Path path;
		try {
			path = file.toPath();
			Files.createFile(path);
		} catch (FileAlreadyExistsException | InvalidPathException e) {
			return token(false, null);
		}
		if (supportsAtomicCreateNewFile()) {
			return token(true, null);
		}
		Path link = null;
		FileStore store = null;
		try {
			store = Files.getFileStore(path);
		} catch (SecurityException e) {
			return token(true, null);
		}
		try {
			Boolean canLink = CAN_HARD_LINK.computeIfAbsent(store,
					s -> Boolean.TRUE);
			if (Boolean.FALSE.equals(canLink)) {
				return token(true, null);
			}
			link = Files.createLink(Paths.get(uniqueLinkPath(file)), path);
			Integer nlink = (Integer) (Files.getAttribute(path,
					"unix:nlink")); //$NON-NLS-1$
			if (nlink.intValue() > 2) {
				LOG.warn(MessageFormat.format(
						JGitText.get().failedAtomicFileCreation, path, nlink));
				return token(false, link);
			} else if (nlink.intValue() < 2) {
				CAN_HARD_LINK.put(store, Boolean.FALSE);
			}
			return token(true, link);
		} catch (UnsupportedOperationException | IllegalArgumentException
				| FileSystemException | SecurityException e) {
			CAN_HARD_LINK.put(store, Boolean.FALSE);
			return token(true, link);
		}
	}

	private static LockToken token(boolean created, @Nullable Path p) {
		return ((p != null) && Files.exists(p))
				? new LockToken(created, Optional.of(p))
				: new LockToken(created, Optional.empty());
	}

	private static String uniqueLinkPath(File file) {
		UUID id = UUID.randomUUID();
		return file.getAbsolutePath() + "." //$NON-NLS-1$
				+ Long.toHexString(id.getMostSignificantBits())
				+ Long.toHexString(id.getLeastSignificantBits());
	}
}
