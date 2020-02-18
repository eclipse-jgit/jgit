/*
 * Copyright (C) 2007, Dave Watson <dwatson@mimvista.com>
 * Copyright (C) 2008-2010, Google Inc.
 * Copyright (C) 2006-2010, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2006-2008, Shawn O. Pearce <spearce@spearce.org>
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

package org.eclipse.jgit.internal.storage.file;

import static java.util.stream.Collectors.toList;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.MessageFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.attributes.AttributesNode;
import org.eclipse.jgit.attributes.AttributesNodeProvider;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.events.IndexChangedEvent;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.storage.file.ObjectDirectory.AlternateHandle;
import org.eclipse.jgit.internal.storage.file.ObjectDirectory.AlternateRepository;
import org.eclipse.jgit.internal.storage.reftree.RefTreeDatabase;
import org.eclipse.jgit.lib.BaseRepositoryBuilder;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.CoreConfig.HideDotFiles;
import org.eclipse.jgit.lib.CoreConfig.SymLinks;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.ReflogEntry;
import org.eclipse.jgit.lib.ReflogReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.storage.pack.PackConfig;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.RawParseUtils;
import org.eclipse.jgit.util.StringUtils;
import org.eclipse.jgit.util.SystemReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a Git repository. A repository holds all objects and refs used for
 * managing source code (could by any type of file, but source code is what
 * SCM's are typically used for).
 *
 * In Git terms all data is stored in GIT_DIR, typically a directory called
 * .git. A work tree is maintained unless the repository is a bare repository.
 * Typically the .git directory is located at the root of the work dir.
 *
 * <ul>
 * <li>GIT_DIR
 * 	<ul>
 * 		<li>objects/ - objects</li>
 * 		<li>refs/ - tags and heads</li>
 * 		<li>config - configuration</li>
 * 		<li>info/ - more configurations</li>
 * 	</ul>
 * </li>
 * </ul>
 * <p>
 * This class is thread-safe.
 * <p>
 * This implementation only handles a subtly undocumented subset of git features.
 */
public class FileRepository extends Repository {
	private static final Logger LOG = LoggerFactory
			.getLogger(FileRepository.class);
	private static final String UNNAMED = "Unnamed repository; edit this file to name it for gitweb."; //$NON-NLS-1$

	private final FileBasedConfig repoConfig;
	private RefDatabase refs;
	private final ObjectDirectory objectDatabase;

	private final Object snapshotLock = new Object();

	// protected by snapshotLock
	private FileSnapshot snapshot;

	/**
	 * Construct a representation of a Git repository.
	 * <p>
	 * The work tree, object directory, alternate object directories and index
	 * file locations are deduced from the given git directory and the default
	 * rules by running
	 * {@link org.eclipse.jgit.storage.file.FileRepositoryBuilder}. This
	 * constructor is the same as saying:
	 *
	 * <pre>
	 * new FileRepositoryBuilder().setGitDir(gitDir).build()
	 * </pre>
	 *
	 * @param gitDir
	 *            GIT_DIR (the location of the repository metadata).
	 * @throws java.io.IOException
	 *             the repository appears to already exist but cannot be
	 *             accessed.
	 * @see FileRepositoryBuilder
	 */
	public FileRepository(File gitDir) throws IOException {
		this(new FileRepositoryBuilder().setGitDir(gitDir).setup());
	}

	/**
	 * A convenience API for {@link #FileRepository(File)}.
	 *
	 * @param gitDir
	 *            GIT_DIR (the location of the repository metadata).
	 * @throws java.io.IOException
	 *             the repository appears to already exist but cannot be
	 *             accessed.
	 * @see FileRepositoryBuilder
	 */
	public FileRepository(String gitDir) throws IOException {
		this(new File(gitDir));
	}

	/**
	 * Create a repository using the local file system.
	 *
	 * @param options
	 *            description of the repository's important paths.
	 * @throws java.io.IOException
	 *             the user configuration file or repository configuration file
	 *             cannot be accessed.
	 */
	public FileRepository(BaseRepositoryBuilder options) throws IOException {
		super(options);
		StoredConfig userConfig = null;
		try {
			userConfig = SystemReader.getInstance().getUserConfig();
		} catch (ConfigInvalidException e) {
			LOG.error(e.getMessage(), e);
			throw new IOException(e.getMessage(), e);
		}
		repoConfig = new FileBasedConfig(userConfig, getFS().resolve(
				getDirectory(), Constants.CONFIG),
				getFS());
		loadRepoConfig();

		repoConfig.addChangeListener(this::fireEvent);

		final long repositoryFormatVersion = getConfig().getLong(
				ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_REPO_FORMAT_VERSION, 0);

		String reftype = repoConfig.getString(
				ConfigConstants.CONFIG_EXTENSIONS_SECTION, null,
				ConfigConstants.CONFIG_KEY_REF_STORAGE);
		if (repositoryFormatVersion >= 1 && reftype != null) {
			if (StringUtils.equalsIgnoreCase(reftype,
					ConfigConstants.CONFIG_REF_STORAGE_REFTABLE)) {
				refs = new FileReftableDatabase(this);
			} else if (StringUtils.equalsIgnoreCase(reftype, "reftree")) { //$NON-NLS-1$
				refs = new RefTreeDatabase(this, new RefDirectory(this));
			} else {
				throw new IOException(JGitText.get().unknownRepositoryFormat);
			}
		} else {
			refs = new RefDirectory(this);
		}

		objectDatabase = new ObjectDirectory(repoConfig, //
				options.getObjectDirectory(), //
				options.getAlternateObjectDirectories(), //
				getFS(), //
				new File(getDirectory(), Constants.SHALLOW));

		if (objectDatabase.exists()) {
			if (repositoryFormatVersion > 1)
				throw new IOException(MessageFormat.format(
						JGitText.get().unknownRepositoryFormat2,
						Long.valueOf(repositoryFormatVersion)));
		}

		if (!isBare()) {
			snapshot = FileSnapshot.save(getIndexFile());
		}
	}

	private void loadRepoConfig() throws IOException {
		try {
			repoConfig.load();
		} catch (ConfigInvalidException e) {
			throw new IOException(JGitText.get().unknownRepositoryFormat, e);
		}
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Create a new Git repository initializing the necessary files and
	 * directories.
	 */
	@Override
	public void create(boolean bare) throws IOException {
		final FileBasedConfig cfg = getConfig();
		if (cfg.getFile().exists()) {
			throw new IllegalStateException(MessageFormat.format(
					JGitText.get().repositoryAlreadyExists, getDirectory()));
		}
		FileUtils.mkdirs(getDirectory(), true);
		HideDotFiles hideDotFiles = getConfig().getEnum(
				ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_HIDEDOTFILES,
				HideDotFiles.DOTGITONLY);
		if (hideDotFiles != HideDotFiles.FALSE && !isBare()
				&& getDirectory().getName().startsWith(".")) //$NON-NLS-1$
			getFS().setHidden(getDirectory(), true);
		refs.create();
		objectDatabase.create();

		FileUtils.mkdir(new File(getDirectory(), "branches")); //$NON-NLS-1$
		FileUtils.mkdir(new File(getDirectory(), "hooks")); //$NON-NLS-1$

		RefUpdate head = updateRef(Constants.HEAD);
		head.disableRefLog();
		head.link(Constants.R_HEADS + Constants.MASTER);

		final boolean fileMode;
		if (getFS().supportsExecute()) {
			File tmp = File.createTempFile("try", "execute", getDirectory()); //$NON-NLS-1$ //$NON-NLS-2$

			getFS().setExecute(tmp, true);
			final boolean on = getFS().canExecute(tmp);

			getFS().setExecute(tmp, false);
			final boolean off = getFS().canExecute(tmp);
			FileUtils.delete(tmp);

			fileMode = on && !off;
		} else {
			fileMode = false;
		}

		SymLinks symLinks = SymLinks.FALSE;
		if (getFS().supportsSymlinks()) {
			File tmp = new File(getDirectory(), "tmplink"); //$NON-NLS-1$
			try {
				getFS().createSymLink(tmp, "target"); //$NON-NLS-1$
				symLinks = null;
				FileUtils.delete(tmp);
			} catch (IOException e) {
				// Normally a java.nio.file.FileSystemException
			}
		}
		if (symLinks != null)
			cfg.setString(ConfigConstants.CONFIG_CORE_SECTION, null,
					ConfigConstants.CONFIG_KEY_SYMLINKS, symLinks.name()
							.toLowerCase(Locale.ROOT));
		cfg.setInt(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_REPO_FORMAT_VERSION, 0);
		cfg.setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_FILEMODE, fileMode);
		if (bare)
			cfg.setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
					ConfigConstants.CONFIG_KEY_BARE, true);
		cfg.setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_LOGALLREFUPDATES, !bare);
		if (SystemReader.getInstance().isMacOS())
			// Java has no other way
			cfg.setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
					ConfigConstants.CONFIG_KEY_PRECOMPOSEUNICODE, true);
		if (!bare) {
			File workTree = getWorkTree();
			if (!getDirectory().getParentFile().equals(workTree)) {
				cfg.setString(ConfigConstants.CONFIG_CORE_SECTION, null,
						ConfigConstants.CONFIG_KEY_WORKTREE, getWorkTree()
								.getAbsolutePath());
				LockFile dotGitLockFile = new LockFile(new File(workTree,
						Constants.DOT_GIT));
				try {
					if (dotGitLockFile.lock()) {
						dotGitLockFile.write(Constants.encode(Constants.GITDIR
								+ getDirectory().getAbsolutePath()));
						dotGitLockFile.commit();
					}
				} finally {
					dotGitLockFile.unlock();
				}
			}
		}
		cfg.save();
	}

	/**
	 * Get the directory containing the objects owned by this repository
	 *
	 * @return the directory containing the objects owned by this repository.
	 */
	public File getObjectsDirectory() {
		return objectDatabase.getDirectory();
	}

	/** {@inheritDoc} */
	@Override
	public ObjectDirectory getObjectDatabase() {
		return objectDatabase;
	}

	/** {@inheritDoc} */
	@Override
	public RefDatabase getRefDatabase() {
		return refs;
	}

	/** {@inheritDoc} */
	@Override
	public String getIdentifier() {
		File directory = getDirectory();
		if (directory != null) {
			return directory.getPath();
		}
		throw new IllegalStateException();
	}

	/** {@inheritDoc} */
	@Override
	public FileBasedConfig getConfig() {
		try {
			SystemReader.getInstance().getUserConfig();
			if (repoConfig.isOutdated()) {
				loadRepoConfig();
			}
		} catch (IOException | ConfigInvalidException e) {
			throw new RuntimeException(e);
		}
		return repoConfig;
	}

	/** {@inheritDoc} */
	@Override
	@Nullable
	public String getGitwebDescription() throws IOException {
		String d;
		try {
			d = RawParseUtils.decode(IO.readFully(descriptionFile()));
		} catch (FileNotFoundException err) {
			return null;
		}
		if (d != null) {
			d = d.trim();
			if (d.isEmpty() || UNNAMED.equals(d)) {
				return null;
			}
		}
		return d;
	}

	/** {@inheritDoc} */
	@Override
	public void setGitwebDescription(@Nullable String description)
			throws IOException {
		String old = getGitwebDescription();
		if (Objects.equals(old, description)) {
			return;
		}

		File path = descriptionFile();
		LockFile lock = new LockFile(path);
		if (!lock.lock()) {
			throw new IOException(MessageFormat.format(JGitText.get().lockError,
					path.getAbsolutePath()));
		}
		try {
			String d = description;
			if (d != null) {
				d = d.trim();
				if (!d.isEmpty()) {
					d += '\n';
				}
			} else {
				d = ""; //$NON-NLS-1$
			}
			lock.write(Constants.encode(d));
			lock.commit();
		} finally {
			lock.unlock();
		}
	}

	private File descriptionFile() {
		return new File(getDirectory(), "description"); //$NON-NLS-1$
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Objects known to exist but not expressed by {@code #getAllRefs()}.
	 * <p>
	 * When a repository borrows objects from another repository, it can
	 * advertise that it safely has that other repository's references, without
	 * exposing any other details about the other repository. This may help a
	 * client trying to push changes avoid pushing more than it needs to.
	 */
	@Override
	public Set<ObjectId> getAdditionalHaves() {
		return getAdditionalHaves(null);
	}

	/**
	 * Objects known to exist but not expressed by {@code #getAllRefs()}.
	 * <p>
	 * When a repository borrows objects from another repository, it can
	 * advertise that it safely has that other repository's references, without
	 * exposing any other details about the other repository. This may help a
	 * client trying to push changes avoid pushing more than it needs to.
	 *
	 * @param skips
	 *            Set of AlternateHandle Ids already seen
	 *
	 * @return unmodifiable collection of other known objects.
	 */
	private Set<ObjectId> getAdditionalHaves(Set<AlternateHandle.Id> skips) {
		HashSet<ObjectId> r = new HashSet<>();
		skips = objectDatabase.addMe(skips);
		for (AlternateHandle d : objectDatabase.myAlternates()) {
			if (d instanceof AlternateRepository && !skips.contains(d.getId())) {
				FileRepository repo;

				repo = ((AlternateRepository) d).repository;
				for (Ref ref : repo.getAllRefs().values()) {
					if (ref.getObjectId() != null)
						r.add(ref.getObjectId());
					if (ref.getPeeledObjectId() != null)
						r.add(ref.getPeeledObjectId());
				}
				r.addAll(repo.getAdditionalHaves(skips));
			}
		}
		return r;
	}

	/**
	 * Add a single existing pack to the list of available pack files.
	 *
	 * @param pack
	 *            path of the pack file to open.
	 * @throws java.io.IOException
	 *             index file could not be opened, read, or is not recognized as
	 *             a Git pack file index.
	 */
	public void openPack(File pack) throws IOException {
		objectDatabase.openPack(pack);
	}

	/** {@inheritDoc} */
	@Override
	public void scanForRepoChanges() throws IOException {
		getRefDatabase().getRefs(); // This will look for changes to refs
		detectIndexChanges();
	}

	/** Detect index changes. */
	private void detectIndexChanges() {
		if (isBare()) {
			return;
		}

		File indexFile = getIndexFile();
		synchronized (snapshotLock) {
			if (snapshot == null) {
				snapshot = FileSnapshot.save(indexFile);
				return;
			}
			if (!snapshot.isModified(indexFile)) {
				return;
			}
		}
		notifyIndexChanged(false);
	}

	/** {@inheritDoc} */
	@Override
	public void notifyIndexChanged(boolean internal) {
		synchronized (snapshotLock) {
			snapshot = FileSnapshot.save(getIndexFile());
		}
		fireEvent(new IndexChangedEvent(internal));
	}

	/** {@inheritDoc} */
	@Override
	public ReflogReader getReflogReader(String refName) throws IOException {
		if (refs instanceof FileReftableDatabase) {
			// Cannot use findRef: reftable stores log data for deleted or renamed
			// branches.
			return ((FileReftableDatabase)refs).getReflogReader(refName);
		}

		// TODO: use exactRef here, which offers more predictable and therefore preferable
		// behavior.
		Ref ref = findRef(refName);
		if (ref == null) {
			return null;
		}
		return new ReflogReaderImpl(this, ref.getName());
	}

	/** {@inheritDoc} */
	@Override
	public AttributesNodeProvider createAttributesNodeProvider() {
		return new AttributesNodeProviderImpl(this);
	}

	/**
	 * Implementation a {@link AttributesNodeProvider} for a
	 * {@link FileRepository}.
	 *
	 * @author <a href="mailto:arthur.daussy@obeo.fr">Arthur Daussy</a>
	 *
	 */
	static class AttributesNodeProviderImpl implements
			AttributesNodeProvider {

		private AttributesNode infoAttributesNode;

		private AttributesNode globalAttributesNode;

		/**
		 * Constructor.
		 *
		 * @param repo
		 *            {@link Repository} that will provide the attribute nodes.
		 */
		protected AttributesNodeProviderImpl(Repository repo) {
			infoAttributesNode = new InfoAttributesNode(repo);
			globalAttributesNode = new GlobalAttributesNode(repo);
		}

		@Override
		public AttributesNode getInfoAttributesNode() throws IOException {
			if (infoAttributesNode instanceof InfoAttributesNode)
				infoAttributesNode = ((InfoAttributesNode) infoAttributesNode)
						.load();
			return infoAttributesNode;
		}

		@Override
		public AttributesNode getGlobalAttributesNode() throws IOException {
			if (globalAttributesNode instanceof GlobalAttributesNode)
				globalAttributesNode = ((GlobalAttributesNode) globalAttributesNode)
						.load();
			return globalAttributesNode;
		}

		static void loadRulesFromFile(AttributesNode r, File attrs)
				throws FileNotFoundException, IOException {
			if (attrs.exists()) {
				try (FileInputStream in = new FileInputStream(attrs)) {
					r.parse(in);
				}
			}
		}

	}

	private boolean shouldAutoDetach() {
		return getConfig().getBoolean(ConfigConstants.CONFIG_GC_SECTION,
				ConfigConstants.CONFIG_KEY_AUTODETACH, true);
	}

	/** {@inheritDoc} */
	@Override
	public void autoGC(ProgressMonitor monitor) {
		GC gc = new GC(this);
		gc.setPackConfig(new PackConfig(this));
		gc.setProgressMonitor(monitor);
		gc.setAuto(true);
		gc.setBackground(shouldAutoDetach());
		try {
			gc.gc();
		} catch (ParseException | IOException e) {
			throw new JGitInternalException(JGitText.get().gcFailed, e);
		}
	}

	/**
	 * Converts the RefDatabase from reftable to RefDirectory. This operation is
	 * not atomic.
	 *
	 * @param writeLogs
	 *            whether to write reflogs
	 * @param backup
	 *            whether to rename or delete the old storage files. If set to
	 *            {@code true}, the reftable list is left in {@code refs.old},
	 *            and the {@code reftable/} dir is left alone. If set to
	 *            {@code false}, the {@code reftable/} dir is removed, and
	 *            {@code refs} file is removed.
	 * @throws IOException
	 *             on IO problem
	 */
	void convertToPackedRefs(boolean writeLogs, boolean backup) throws IOException {
		List<Ref> all = refs.getRefs();
		File packedRefs = new File(getDirectory(), Constants.PACKED_REFS);
		if (packedRefs.exists()) {
			throw new IOException(MessageFormat.format(JGitText.get().fileAlreadyExists,
				packedRefs.getName()));
		}

		File refsFile = new File(getDirectory(), "refs"); //$NON-NLS-1$
		File refsHeadsFile = new File(refsFile, "heads");//$NON-NLS-1$
		File headFile = new File(getDirectory(), Constants.HEAD);
		FileReftableDatabase oldDb = (FileReftableDatabase) refs;

		// Remove the dummy files that ensure compatibility with older git
		// versions (see convertToReftable). First make room for refs/heads/
		refsHeadsFile.delete();
		// RefDirectory wants to create the refs/ directory from scratch, so
		// remove that too.
		refsFile.delete();
		// remove HEAD so its previous invalid value doesn't cause issues.
		headFile.delete();

		// This is not atomic, but there is no way to instantiate a RefDirectory
		// that is disconnected from the current repo.
		RefDirectory refDir = new RefDirectory(this);
		refs = refDir;
		refs.create();

		ReflogWriter logWriter = refDir.newLogWriter(true);
		List<Ref> symrefs = new ArrayList<>();
		BatchRefUpdate bru = refs.newBatchUpdate();
		for (Ref r : all) {
			if (r.isSymbolic()) {
				symrefs.add(r);
			} else {
				bru.addCommand(new ReceiveCommand(ObjectId.zeroId(),
						r.getObjectId(), r.getName()));
			}

			if (writeLogs) {
				List<ReflogEntry> logs = oldDb.getReflogReader(r.getName())
					.getReverseEntries();
				Collections.reverse(logs);
				for (ReflogEntry e : logs) {
					logWriter.log(r.getName(), e);
				}
			}
		}

		try (RevWalk rw = new RevWalk(this)) {
			bru.execute(rw, NullProgressMonitor.INSTANCE);
		}

		List<String> failed = new ArrayList<>();
		for (ReceiveCommand cmd : bru.getCommands()) {
			if (cmd.getResult() != ReceiveCommand.Result.OK) {
				failed.add(cmd.getRefName() + ": " + cmd.getResult()); //$NON-NLS-1$
			}
		}

		if (!failed.isEmpty()) {
			throw new IOException(String.format("%s: %s", //$NON-NLS-1$
					JGitText.get().failedToConvert,
					StringUtils.join(failed, ", "))); //$NON-NLS-1$
		}

		for (Ref s : symrefs) {
			RefUpdate up = refs.newUpdate(s.getName(), false);
			up.setForceUpdate(true);
			RefUpdate.Result res = up.link(s.getTarget().getName());
			if (res != RefUpdate.Result.NEW
					&& res != RefUpdate.Result.NO_CHANGE) {
				throw new IOException(
						String.format("ref %s: %s", s.getName(), res)); //$NON-NLS-1$
			}
		}

		if (!backup) {
			File reftableDir = new File(getDirectory(), Constants.REFTABLE);
			FileUtils.delete(reftableDir,
					FileUtils.RECURSIVE | FileUtils.IGNORE_ERRORS);
		}
		repoConfig.unset(ConfigConstants.CONFIG_EXTENSIONS_SECTION, null,
				ConfigConstants.CONFIG_KEY_REF_STORAGE);
	}

	/**
	 * Converts the RefDatabase from RefDirectory to reftable. This operation is
	 * not atomic.
	 *
	 * @param writeLogs
	 *            whether to write reflogs
	 * @param backup
	 *            whether to rename or delete the old storage files. If set to
	 *            {@code true}, the loose refs are left in {@code refs.old}, the
	 *            packed-refs in {@code packed-refs.old} and reflogs in
	 *            {@code refs.old/}. HEAD is left in {@code HEAD.old} and also
	 *            {@code .log} is appended to additional refs. If set to
	 *            {@code false}, the {@code refs/} and {@code logs/} directories
	 *            and {@code HEAD} and additional symbolic refs are removed.
	 * @throws IOException
	 *             on IO problem
	 */
	@SuppressWarnings("nls")
	void convertToReftable(boolean writeLogs, boolean backup)
			throws IOException {
		File reftableDir = new File(getDirectory(), Constants.REFTABLE);
		File headFile = new File(getDirectory(), Constants.HEAD);
		if (reftableDir.exists() && reftableDir.listFiles().length > 0) {
			throw new IOException(JGitText.get().reftableDirExists);
		}

		// Ignore return value, as it is tied to temporary newRefs file.
		FileReftableDatabase.convertFrom(this, writeLogs);

		File refsFile = new File(getDirectory(), "refs");

		// non-atomic: remove old data.
		File packedRefs = new File(getDirectory(), Constants.PACKED_REFS);
		File logsDir = new File(getDirectory(), Constants.LOGS);

		List<String> additional = getRefDatabase().getAdditionalRefs().stream()
				.map(Ref::getName).collect(toList());
		additional.add(Constants.HEAD);
		if (backup) {
			FileUtils.rename(refsFile, new File(getDirectory(), "refs.old"));
			if (packedRefs.exists()) {
				FileUtils.rename(packedRefs, new File(getDirectory(),
						Constants.PACKED_REFS + ".old"));
			}
			if (logsDir.exists()) {
				FileUtils.rename(logsDir,
						new File(getDirectory(), Constants.LOGS + ".old"));
			}
			for (String r : additional) {
				FileUtils.rename(new File(getDirectory(), r),
					new File(getDirectory(), r + ".old"));
			}
		} else {
			FileUtils.delete(packedRefs, FileUtils.SKIP_MISSING);
			FileUtils.delete(headFile);
			FileUtils.delete(logsDir, FileUtils.RECURSIVE);
			FileUtils.delete(refsFile, FileUtils.RECURSIVE);
			for (String r : additional) {
				new File(getDirectory(), r).delete();
			}
		}

		FileUtils.mkdir(refsFile, true);

		// By putting in a dummy HEAD, old versions of Git still detect a repo
		// (that they can't read)
		try (OutputStream os = new FileOutputStream(headFile)) {
			os.write(Constants.encodeASCII("ref: refs/heads/.invalid"));
		}

		// Some tools might write directly into .git/refs/heads/BRANCH. By
		// putting a file here, this fails spectacularly.
		FileUtils.createNewFile(new File(refsFile, "heads"));

		repoConfig.setString(ConfigConstants.CONFIG_EXTENSIONS_SECTION, null,
				ConfigConstants.CONFIG_KEY_REF_STORAGE,
				ConfigConstants.CONFIG_REF_STORAGE_REFTABLE);
		repoConfig.setLong(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_REPO_FORMAT_VERSION, 1);
		repoConfig.save();
		refs.close();
		refs = new FileReftableDatabase(this);
	}

	/**
	 * Converts between ref storage formats.
	 *
	 * @param format
	 *            the format to convert to, either "reftable" or "refdir"
	 * @param writeLogs
	 *            whether to write reflogs
	 * @param backup
	 *            whether to make a backup of the old data
	 * @throws IOException
	 *             on I/O problems.
	 */
	@SuppressWarnings("nls")
	public void convertRefStorage(String format, boolean writeLogs,
			boolean backup) throws IOException {
		if (format.equals("reftable")) { //$NON-NLS-1$
			if (refs instanceof RefDirectory) {
				convertToReftable(writeLogs, backup);
			}
		} else if (format.equals("refdir")) {//$NON-NLS-1$
			if (refs instanceof FileReftableDatabase) {
				convertToPackedRefs(writeLogs, backup);
			}
		} else {
			throw new IOException(String.format(
					"unknown supported ref storage format '%s'", format));
		}
	}
}
