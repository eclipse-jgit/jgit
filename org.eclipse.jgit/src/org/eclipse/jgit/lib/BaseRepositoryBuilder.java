/*
 * Copyright (C) 2010, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.lib;

import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_CORE_SECTION;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_BARE;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_WORKTREE;
import static org.eclipse.jgit.lib.Constants.CONFIG;
import static org.eclipse.jgit.lib.Constants.DOT_GIT;
import static org.eclipse.jgit.lib.Constants.GITDIR_FILE;
import static org.eclipse.jgit.lib.Constants.GIT_ALTERNATE_OBJECT_DIRECTORIES_KEY;
import static org.eclipse.jgit.lib.Constants.GIT_CEILING_DIRECTORIES_KEY;
import static org.eclipse.jgit.lib.Constants.GIT_COMMON_DIR_KEY;
import static org.eclipse.jgit.lib.Constants.GIT_DIR_KEY;
import static org.eclipse.jgit.lib.Constants.GIT_INDEX_FILE_KEY;
import static org.eclipse.jgit.lib.Constants.GIT_OBJECT_DIRECTORY_KEY;
import static org.eclipse.jgit.lib.Constants.GIT_WORK_TREE_KEY;
import static org.eclipse.jgit.lib.Constants.OBJECTS;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.api.errors.InvalidRefNameException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.RepositoryCache.FileKey;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.RawParseUtils;
import org.eclipse.jgit.util.StringUtils;
import org.eclipse.jgit.util.SystemReader;

/**
 * Base builder to customize repository construction.
 * <p>
 * Repository implementations may subclass this builder in order to add custom
 * repository detection methods.
 *
 * @param <B>
 *            type of the repository builder.
 * @param <R>
 *            type of the repository that is constructed.
 * @see RepositoryBuilder
 * @see FileRepositoryBuilder
 */
public class BaseRepositoryBuilder<B extends BaseRepositoryBuilder, R extends Repository> {
	private static boolean isSymRef(byte[] ref) {
		if (ref.length < 9)
			return false;
		return /**/ref[0] == 'g' //
				&& ref[1] == 'i' //
				&& ref[2] == 't' //
				&& ref[3] == 'd' //
				&& ref[4] == 'i' //
				&& ref[5] == 'r' //
				&& ref[6] == ':' //
				&& ref[7] == ' ';
	}

	/**
	 * Read symbolic reference file
	 *
	 * @param workTree
	 *            the work tree path
	 * @param dotGit
	 *            the .git file
	 * @param fs
	 *            th FS util
	 * @return the file read from symbolic reference file
	 * @throws java.io.IOException
	 *             the dotGit file is invalid reference
	 * @since 7.0
	 */
	static File getSymRef(File workTree, File dotGit, FS fs)
			throws IOException {
		byte[] content = IO.readFully(dotGit);
		if (!isSymRef(content)) {
			throw new IOException(MessageFormat.format(
					JGitText.get().invalidGitdirRef, dotGit.getAbsolutePath()));
		}

		int pathStart = 8;
		int lineEnd = RawParseUtils.nextLF(content, pathStart);
		while (content[lineEnd - 1] == '\n' ||
				(content[lineEnd - 1] == '\r'
						&& SystemReader.getInstance().isWindows())) {
			lineEnd--;
		}
		if (lineEnd == pathStart) {
			throw new IOException(MessageFormat.format(
					JGitText.get().invalidGitdirRef, dotGit.getAbsolutePath()));
		}

		String gitdirPath = RawParseUtils.decode(content, pathStart, lineEnd);
		File gitdirFile = fs.resolve(workTree, gitdirPath);
		if (gitdirFile.isAbsolute()) {
			return gitdirFile;
		}
		return new File(workTree, gitdirPath).getCanonicalFile();
	}

	private FS fs;

	private File gitDir;

	private File gitCommonDir;

	private File objectDirectory;

	private List<File> alternateObjectDirectories;

	private File indexFile;

	private File workTree;

	private String initialBranch = Constants.MASTER;

	/** Directories limiting the search for a Git repository. */
	private List<File> ceilingDirectories;

	/** True only if the caller wants to force bare behavior. */
	private boolean bare;

	/** True if the caller requires the repository to exist. */
	private boolean mustExist;

	/** Configuration file of target repository, lazily loaded if required. */
	private Config config;

	/**
	 * Set the file system abstraction needed by this repository.
	 *
	 * @param fs
	 *            the abstraction.
	 * @return {@code this} (for chaining calls).
	 */
	public B setFS(FS fs) {
		this.fs = fs;
		return self();
	}

	/**
	 * Get the file system abstraction, or null if not set.
	 *
	 * @return the file system abstraction, or null if not set.
	 */
	public FS getFS() {
		return fs;
	}

	/**
	 * Set the Git directory storing the repository metadata.
	 * <p>
	 * The meta directory stores the objects, references, and meta files like
	 * {@code MERGE_HEAD}, or the index file. If {@code null} the path is
	 * assumed to be {@code workTree/.git}.
	 *
	 * @param gitDir
	 *            {@code GIT_DIR}, the repository meta directory.
	 * @return {@code this} (for chaining calls).
	 */
	public B setGitDir(File gitDir) {
		this.gitDir = gitDir;
		this.config = null;
		return self();
	}

	/**
	 * Get the meta data directory; null if not set.
	 *
	 * @return the meta data directory; null if not set.
	 */
	public File getGitDir() {
		return gitDir;
	}

	/**
	 * Set common dir.
	 *
	 * @param gitCommonDir
	 *            {@code GIT_COMMON_DIR}, the common repository meta directory.
	 * @return {@code this} (for chaining calls).
	 * @since 7.0
	 */
	public B setGitCommonDir(File gitCommonDir) {
		this.gitCommonDir = gitCommonDir;
		this.config = null;
		return self();
	}

	/**
	 * Get common dir.
	 *
	 * @return common dir; null if not set.
	 * @since 7.0
	 */
	public File getGitCommonDir() {
		return gitCommonDir;
	}

	/**
	 * Set the directory storing the repository's objects.
	 *
	 * @param objectDirectory
	 *            {@code GIT_OBJECT_DIRECTORY}, the directory where the
	 *            repository's object files are stored.
	 * @return {@code this} (for chaining calls).
	 */
	public B setObjectDirectory(File objectDirectory) {
		this.objectDirectory = objectDirectory;
		return self();
	}

	/**
	 * Get the object directory; null if not set.
	 *
	 * @return the object directory; null if not set.
	 */
	public File getObjectDirectory() {
		return objectDirectory;
	}

	/**
	 * Add an alternate object directory to the search list.
	 * <p>
	 * This setting handles one alternate directory at a time, and is provided
	 * to support {@code GIT_ALTERNATE_OBJECT_DIRECTORIES}.
	 *
	 * @param other
	 *            another objects directory to search after the standard one.
	 * @return {@code this} (for chaining calls).
	 */
	public B addAlternateObjectDirectory(File other) {
		if (other != null) {
			if (alternateObjectDirectories == null) {
				alternateObjectDirectories = new ArrayList<>();
			}
			alternateObjectDirectories.add(other);
		}
		return self();
	}

	/**
	 * Add alternate object directories to the search list.
	 * <p>
	 * This setting handles several alternate directories at once, and is
	 * provided to support {@code GIT_ALTERNATE_OBJECT_DIRECTORIES}.
	 *
	 * @param inList
	 *            other object directories to search after the standard one. The
	 *            collection's contents is copied to an internal list.
	 * @return {@code this} (for chaining calls).
	 */
	public B addAlternateObjectDirectories(Collection<File> inList) {
		if (inList != null) {
			for (File path : inList)
				addAlternateObjectDirectory(path);
		}
		return self();
	}

	/**
	 * Add alternate object directories to the search list.
	 * <p>
	 * This setting handles several alternate directories at once, and is
	 * provided to support {@code GIT_ALTERNATE_OBJECT_DIRECTORIES}.
	 *
	 * @param inList
	 *            other object directories to search after the standard one. The
	 *            array's contents is copied to an internal list.
	 * @return {@code this} (for chaining calls).
	 */
	public B addAlternateObjectDirectories(File[] inList) {
		if (inList != null) {
			for (File path : inList)
				addAlternateObjectDirectory(path);
		}
		return self();
	}

	/**
	 * Get ordered array of alternate directories; null if non were set.
	 *
	 * @return ordered array of alternate directories; null if non were set.
	 */
	public File[] getAlternateObjectDirectories() {
		final List<File> alts = alternateObjectDirectories;
		if (alts == null)
			return null;
		return alts.toArray(new File[0]);
	}

	/**
	 * Force the repository to be treated as bare (have no working directory).
	 * <p>
	 * If bare the working directory aspects of the repository won't be
	 * configured, and will not be accessible.
	 *
	 * @return {@code this} (for chaining calls).
	 */
	public B setBare() {
		setIndexFile(null);
		setWorkTree(null);
		bare = true;
		return self();
	}

	/**
	 * Whether this repository was forced bare by {@link #setBare()}.
	 *
	 * @return true if this repository was forced bare by {@link #setBare()}.
	 */
	public boolean isBare() {
		return bare;
	}

	/**
	 * Require the repository to exist before it can be opened.
	 *
	 * @param mustExist
	 *            true if it must exist; false if it can be missing and created
	 *            after being built.
	 * @return {@code this} (for chaining calls).
	 */
	public B setMustExist(boolean mustExist) {
		this.mustExist = mustExist;
		return self();
	}

	/**
	 * Whether the repository must exist before being opened.
	 *
	 * @return true if the repository must exist before being opened.
	 */
	public boolean isMustExist() {
		return mustExist;
	}

	/**
	 * Set the top level directory of the working files.
	 *
	 * @param workTree
	 *            {@code GIT_WORK_TREE}, the working directory of the checkout.
	 * @return {@code this} (for chaining calls).
	 */
	public B setWorkTree(File workTree) {
		this.workTree = workTree;
		return self();
	}

	/**
	 * Get the work tree directory, or null if not set.
	 *
	 * @return the work tree directory, or null if not set.
	 */
	public File getWorkTree() {
		return workTree;
	}

	/**
	 * Set the local index file that is caching checked out file status.
	 * <p>
	 * The location of the index file tracking the status information for each
	 * checked out file in {@code workTree}. This may be null to assume the
	 * default {@code gitDiir/index}.
	 *
	 * @param indexFile
	 *            {@code GIT_INDEX_FILE}, the index file location.
	 * @return {@code this} (for chaining calls).
	 */
	public B setIndexFile(File indexFile) {
		this.indexFile = indexFile;
		return self();
	}

	/**
	 * Get the index file location, or null if not set.
	 *
	 * @return the index file location, or null if not set.
	 */
	public File getIndexFile() {
		return indexFile;
	}

	/**
	 * Set the initial branch of the new repository. If not specified
	 * ({@code null} or empty), fall back to the default name (currently
	 * master).
	 *
	 * @param branch
	 *            initial branch name of the new repository. If {@code null} or
	 *            empty the configured default branch will be used.
	 * @return {@code this}
	 * @throws InvalidRefNameException
	 *             if the branch name is not valid
	 *
	 * @since 5.11
	 */
	public B setInitialBranch(String branch) throws InvalidRefNameException {
		if (StringUtils.isEmptyOrNull(branch)) {
			this.initialBranch = Constants.MASTER;
		} else {
			if (!Repository.isValidRefName(Constants.R_HEADS + branch)) {
				throw new InvalidRefNameException(MessageFormat
						.format(JGitText.get().branchNameInvalid, branch));
			}
			this.initialBranch = branch;
		}
		return self();
	}

	/**
	 * Get the initial branch of the new repository.
	 *
	 * @return the initial branch of the new repository.
	 * @since 5.11
	 */
	public @NonNull String getInitialBranch() {
		return initialBranch;
	}

	/**
	 * Read standard Git environment variables and configure from those.
	 * <p>
	 * This method tries to read the standard Git environment variables, such as
	 * {@code GIT_DIR}, {@code GIT_COMMON_DIR}, {@code GIT_WORK_TREE} etc. to
	 * configure this builder instance. If an environment variable is set, it
	 * overrides the value already set in this builder.
	 *
	 * @return {@code this} (for chaining calls).
	 */
	public B readEnvironment() {
		return readEnvironment(SystemReader.getInstance());
	}

	/**
	 * Read standard Git environment variables and configure from those.
	 * <p>
	 * This method tries to read the standard Git environment variables, such as
	 * {@code GIT_DIR}, {@code GIT_COMMON_DIR}, {@code GIT_WORK_TREE} etc. to
	 * configure this builder instance. If a property is already set in the
	 * builder, the environment variable is not used.
	 *
	 * @param sr
	 *            the SystemReader abstraction to access the environment.
	 * @return {@code this} (for chaining calls).
	 */
	public B readEnvironment(SystemReader sr) {
		if (getGitDir() == null) {
			String val = sr.getenv(GIT_DIR_KEY);
			if (val != null)
				setGitDir(new File(val));
		}

		if (getGitCommonDir() == null) {
			String val = sr.getenv(GIT_COMMON_DIR_KEY);
			if (val != null) {
				setGitCommonDir(new File(val));
			}
		}

		if (getObjectDirectory() == null) {
			String val = sr.getenv(GIT_OBJECT_DIRECTORY_KEY);
			if (val != null)
				setObjectDirectory(new File(val));
		}

		if (getAlternateObjectDirectories() == null) {
			String val = sr.getenv(GIT_ALTERNATE_OBJECT_DIRECTORIES_KEY);
			if (val != null) {
				for (String path : val.split(File.pathSeparator, -1))
					addAlternateObjectDirectory(new File(path));
			}
		}

		if (getWorkTree() == null) {
			String val = sr.getenv(GIT_WORK_TREE_KEY);
			if (val != null)
				setWorkTree(new File(val));
		}

		if (getIndexFile() == null) {
			String val = sr.getenv(GIT_INDEX_FILE_KEY);
			if (val != null)
				setIndexFile(new File(val));
		}

		if (ceilingDirectories == null) {
			String val = sr.getenv(GIT_CEILING_DIRECTORIES_KEY);
			if (val != null) {
				for (String path : val.split(File.pathSeparator, -1))
					addCeilingDirectory(new File(path));
			}
		}

		return self();
	}

	/**
	 * Add a ceiling directory to the search limit list.
	 * <p>
	 * This setting handles one ceiling directory at a time, and is provided to
	 * support {@code GIT_CEILING_DIRECTORIES}.
	 *
	 * @param root
	 *            a path to stop searching at; its parent will not be searched.
	 * @return {@code this} (for chaining calls).
	 */
	public B addCeilingDirectory(File root) {
		if (root != null) {
			if (ceilingDirectories == null) {
				ceilingDirectories = new ArrayList<>();
			}
			ceilingDirectories.add(root);
		}
		return self();
	}

	/**
	 * Add ceiling directories to the search list.
	 * <p>
	 * This setting handles several ceiling directories at once, and is provided
	 * to support {@code GIT_CEILING_DIRECTORIES}.
	 *
	 * @param inList
	 *            directory paths to stop searching at. The collection's
	 *            contents is copied to an internal list.
	 * @return {@code this} (for chaining calls).
	 */
	public B addCeilingDirectories(Collection<File> inList) {
		if (inList != null) {
			for (File path : inList)
				addCeilingDirectory(path);
		}
		return self();
	}

	/**
	 * Add ceiling directories to the search list.
	 * <p>
	 * This setting handles several ceiling directories at once, and is provided
	 * to support {@code GIT_CEILING_DIRECTORIES}.
	 *
	 * @param inList
	 *            directory paths to stop searching at. The array's contents is
	 *            copied to an internal list.
	 * @return {@code this} (for chaining calls).
	 */
	public B addCeilingDirectories(File[] inList) {
		if (inList != null) {
			for (File path : inList)
				addCeilingDirectory(path);
		}
		return self();
	}

	/**
	 * Configure {@code GIT_DIR} by searching up the file system.
	 * <p>
	 * Starts from the current working directory of the JVM and scans up through
	 * the directory tree until a Git repository is found. Success can be
	 * determined by checking for {@code getGitDir() != null}.
	 * <p>
	 * The search can be limited to specific spaces of the local filesystem by
	 * {@link #addCeilingDirectory(File)}, or inheriting the list through a
	 * prior call to {@link #readEnvironment()}.
	 *
	 * @return {@code this} (for chaining calls).
	 */
	public B findGitDir() {
		if (getGitDir() == null)
			findGitDir(new File("").getAbsoluteFile()); //$NON-NLS-1$
		return self();
	}

	/**
	 * Configure {@code GIT_DIR} by searching up the file system.
	 * <p>
	 * Starts from the supplied directory path and scans up through the parent
	 * directory tree until a Git repository is found. Success can be determined
	 * by checking for {@code getGitDir() != null}.
	 * <p>
	 * The search can be limited to specific spaces of the local filesystem by
	 * {@link #addCeilingDirectory(File)}, or inheriting the list through a
	 * prior call to {@link #readEnvironment()}.
	 *
	 * @param current
	 *            directory to begin searching in.
	 * @return {@code this} (for chaining calls).
	 */
	public B findGitDir(File current) {
		if (getGitDir() == null) {
			FS tryFS = safeFS();
			while (current != null) {
				File dir = new File(current, DOT_GIT);
				if (FileKey.isGitRepository(dir, tryFS)) {
					setGitDir(dir);
					break;
				} else if (dir.isFile()) {
					try {
						setGitDir(getSymRef(current, dir, tryFS));
						break;
					} catch (IOException ignored) {
						// Continue searching if gitdir ref isn't found
					}
				} else if (FileKey.isGitRepository(current, tryFS)) {
					setGitDir(current);
					break;
				}

				current = current.getParentFile();
				if (current != null && ceilingDirectories != null
						&& ceilingDirectories.contains(current))
					break;
			}
		}
		return self();
	}

	/**
	 * Guess and populate all parameters not already defined.
	 * <p>
	 * If an option was not set, the setup method will try to default the option
	 * based on other options. If insufficient information is available, an
	 * exception is thrown to the caller.
	 *
	 * @return {@code this}
	 * @throws java.lang.IllegalArgumentException
	 *             insufficient parameters were set, or some parameters are
	 *             incompatible with one another.
	 * @throws java.io.IOException
	 *             the repository could not be accessed to configure the rest of
	 *             the builder's parameters.
	 */
	public B setup() throws IllegalArgumentException, IOException {
		requireGitDirOrWorkTree();
		setupGitDir();
		setupCommonDir();
		setupWorkTree();
		setupInternals();
		return self();
	}

	/**
	 * Create a repository matching the configuration in this builder.
	 * <p>
	 * If an option was not set, the build method will try to default the option
	 * based on other options. If insufficient information is available, an
	 * exception is thrown to the caller.
	 *
	 * @return a repository matching this configuration. The caller is
	 *         responsible to close the repository instance when it is no longer
	 *         needed.
	 * @throws java.lang.IllegalArgumentException
	 *             insufficient parameters were set.
	 * @throws java.io.IOException
	 *             the repository could not be accessed to configure the rest of
	 *             the builder's parameters.
	 */
	@SuppressWarnings({ "unchecked", "resource" })
	public R build() throws IOException {
		R repo = (R) new FileRepository(setup());
		if (isMustExist() && !repo.getObjectDatabase().exists())
			throw new RepositoryNotFoundException(getGitDir());
		return repo;
	}

	/**
	 * Require either {@code gitDir} or {@code workTree} to be set.
	 */
	protected void requireGitDirOrWorkTree() {
		if (getGitDir() == null && getWorkTree() == null)
			throw new IllegalArgumentException(
					JGitText.get().eitherGitDirOrWorkTreeRequired);
	}

	/**
	 * Perform standard gitDir initialization.
	 *
	 * @throws java.io.IOException
	 *             the repository could not be accessed
	 */
	protected void setupGitDir() throws IOException {
		// No gitDir? Try to assume its under the workTree or a ref to another
		// location
		if (getGitDir() == null && getWorkTree() != null) {
			File dotGit = new File(getWorkTree(), DOT_GIT);
			if (!dotGit.isFile())
				setGitDir(dotGit);
			else
				setGitDir(getSymRef(getWorkTree(), dotGit, safeFS()));
		}
	}

	/**
	 * Perform standard common dir initialization.
	 *
	 * @throws java.io.IOException
	 *             the repository could not be accessed
	 * @since 7.0
	 */
	protected void setupCommonDir() throws IOException {
		// no gitCommonDir? Try to get it from gitDir
		if (getGitCommonDir() == null) {
			setGitCommonDir(safeFS().getCommonDir(getGitDir()));
		}
	}

	/**
	 * Perform standard work-tree initialization.
	 * <p>
	 * This is a method typically invoked inside of {@link #setup()}, near the
	 * end after the repository has been identified and its configuration is
	 * available for inspection.
	 *
	 * @throws java.io.IOException
	 *             the repository configuration could not be read.
	 */
	protected void setupWorkTree() throws IOException {
		if (getFS() == null)
			setFS(FS.DETECTED);

		// If we aren't bare, we should have a work tree.
		//
		if (!isBare() && getWorkTree() == null)
			setWorkTree(guessWorkTreeOrFail());

		if (!isBare()) {
			// If after guessing we're still not bare, we must have
			// a metadata directory to hold the repository. Assume
			// its at the work tree.
			//
			if (getGitDir() == null)
				setGitDir(getWorkTree().getParentFile());
			if (getIndexFile() == null)
				setIndexFile(new File(getGitDir(), "index")); //$NON-NLS-1$
		}
	}

	/**
	 * Configure the internal implementation details of the repository.
	 *
	 * @throws java.io.IOException
	 *             the repository could not be accessed
	 */
	protected void setupInternals() throws IOException {
		if (getObjectDirectory() == null) {
			File commonDir = getGitCommonDir();
			if (commonDir != null) {
				setObjectDirectory(safeFS().resolve(commonDir, OBJECTS));
			}
		}
	}

	/**
	 * Get the cached repository configuration, loading if not yet available.
	 *
	 * @return the configuration of the repository.
	 * @throws java.io.IOException
	 *             the configuration is not available, or is badly formed.
	 */
	protected Config getConfig() throws IOException {
		if (config == null)
			config = loadConfig();
		return config;
	}

	/**
	 * Parse and load the repository specific configuration.
	 * <p>
	 * The default implementation reads {@code gitDir/config}, or returns an
	 * empty configuration if gitDir was not set.
	 *
	 * @return the repository's configuration.
	 * @throws java.io.IOException
	 *             the configuration is not available.
	 */
	protected Config loadConfig() throws IOException {
		File commonDir = getGitCommonDir();
		if (commonDir != null) {
			// We only want the repository's configuration file, and not
			// the user file, as these parameters must be unique to this
			// repository and not inherited from other files.
			//
			File path = safeFS().resolve(commonDir, CONFIG);
			FileBasedConfig cfg = new FileBasedConfig(path, safeFS());
			try {
				cfg.load();
			} catch (ConfigInvalidException err) {
				throw new IllegalArgumentException(MessageFormat.format(
						JGitText.get().repositoryConfigFileInvalid, path
								.getAbsolutePath(), err.getMessage()));
			}
			return cfg;
		}
		return new Config();
	}

	private File guessWorkTreeOrFail() throws IOException {
		final Config cfg = getConfig();

		// If set, core.worktree wins.
		//
		String path = cfg.getString(CONFIG_CORE_SECTION, null,
				CONFIG_KEY_WORKTREE);
		if (path != null) {
			return safeFS().resolve(getGitDir(), path).getCanonicalFile();
		}

		/*
		 * We are in worktree's $GIT_DIR folder
		 * ".git/worktrees/&lt;worktree-name&gt;" and want to get the working
		 * tree (checkout) path; so here we have an opposite link in file
		 * "gitdir" showing to the ".git" file located in the working tree read
		 * it and convert it to absolute path if it's relative
		 */
		File gitDirFile = new File(getGitDir(), GITDIR_FILE);
		if (gitDirFile.isFile()) {
			String workDirPath = new String(IO.readFully(gitDirFile)).trim();
			File workTreeDotGitFile = new File(workDirPath);
			if (!workTreeDotGitFile.isAbsolute()) {
				workTreeDotGitFile = new File(getGitDir(), workDirPath)
						.getCanonicalFile();
			}
			if (workTreeDotGitFile != null) {
				return workTreeDotGitFile.getParentFile();
			}
		}

		// If core.bare is set, honor its value. Assume workTree is
		// the parent directory of the repository.
		//
		if (cfg.getString(CONFIG_CORE_SECTION, null, CONFIG_KEY_BARE) != null) {
			if (cfg.getBoolean(CONFIG_CORE_SECTION, CONFIG_KEY_BARE, true)) {
				setBare();
				return null;
			}
			return getGitDir().getParentFile();
		}

		if (getGitDir().getName().equals(DOT_GIT)) {
			// No value for the "bare" flag, but gitDir is named ".git",
			// use the parent of the directory
			//
			return getGitDir().getParentFile();
		}

		// We have to assume we are bare.
		//
		setBare();
		return null;
	}

	/**
	 * Get the configured FS, or {@link FS#DETECTED}.
	 *
	 * @return the configured FS, or {@link FS#DETECTED}.
	 */
	protected FS safeFS() {
		return getFS() != null ? getFS() : FS.DETECTED;
	}

	/**
	 * Get this object
	 *
	 * @return {@code this}
	 */
	@SuppressWarnings("unchecked")
	protected final B self() {
		return (B) this;
	}
}
