package org.eclipse.jgit.internal.storage.file;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.FileNotFoundException;
import java.io.IOException;

import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.CorruptPackIndexException;
import org.eclipse.jgit.errors.FsckError;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.FsckError.CorruptIndex;
import org.eclipse.jgit.errors.FsckError.CorruptObject;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.fsck.FsckPackParser;
import org.eclipse.jgit.internal.storage.dfs.ReadableChannel;
import org.eclipse.jgit.internal.storage.pack.PackExt;
import org.eclipse.jgit.internal.submodule.SubmoduleValidator;
import org.eclipse.jgit.internal.submodule.SubmoduleValidator.SubmoduleValidationException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Fsck;
import org.eclipse.jgit.lib.GitmoduleEntry;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectChecker;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.ReflogEntry;
import org.eclipse.jgit.lib.ReflogReader;
import org.eclipse.jgit.revwalk.ObjectWalk;
import org.eclipse.jgit.revwalk.RevObject;

/**
 * Verify the validity and connectivity of a FileRepository.
 */
public class FileFsck implements Fsck {

	private final FileRepository repo;

	private final ObjectDirectory objdb;

	private ObjectChecker objChecker;

	private boolean connectivityOnly;

	/**
	 * Create a FileFsck object
	 *
	 * @param repo
	 *            the repository to check
	 */
	public FileFsck(FileRepository repo) {
		this.repo = repo;
		this.objdb = repo.getObjectDatabase();
		objChecker = new ObjectChecker();
	}

	/**
	 * Verify the integrity and connectivity of all objects in the object
	 * database.
	 *
	 * @param pm
	 *            callback to provide progress feedback during the check.
	 * @return all errors about the repository.
	 * @throws java.io.IOException
	 *             if encounters IO errors during the process.
	 */
	@Override
	public FsckError check(ProgressMonitor pm) throws IOException {
		if (pm == null) {
			pm = NullProgressMonitor.INSTANCE;
		}

		FsckError errors = new FsckError();
		if (!connectivityOnly) {
			objChecker.reset();
			checkPacks(pm, errors);
			checkLooseObjects(pm, errors);
		}
		checkConnectivity(pm, errors);
		return errors;
	}

	private void checkPacks(ProgressMonitor pm, FsckError errors)
			throws IOException, FileNotFoundException {
		for (Pack pack : objdb.getPacks()) {
			try (ReadableChannel rc = new ReadableFileChannel(
					pack.getPackFile(), "r")) { //$NON-NLS-1$
				verifyPack(pm, errors, pack, rc);
			} catch (MissingObjectException e) {
				errors.getMissingObjects().add(e.getObjectId());
			} catch (CorruptPackIndexException e) {
				errors.getCorruptIndices().add(new CorruptIndex(
						new PackFile(objdb.getPackDirectory(),
								pack.getPackName(), PackExt.INDEX).getPath(),
						e.getErrorType()));
			}
		}

		checkGitModules(pm, errors);
	}

	private void verifyPack(ProgressMonitor pm, FsckError errors,
			Pack pack, ReadableChannel ch)
			throws IOException, CorruptPackIndexException {
		FsckPackParser fpp = new FsckPackParser(objdb, ch);
		fpp.setObjectChecker(objChecker);
		fpp.overwriteObjectCount(pack.getObjectCount());
		fpp.parse(pm);
		errors.getCorruptObjects().addAll(fpp.getCorruptObjects());
		fpp.verifyIndex(pack.getIndex());
	}

	private void checkGitModules(ProgressMonitor pm, FsckError errors)
			throws IOException {
		pm.beginTask(JGitText.get().validatingGitModules,
				objChecker.getGitsubmodules().size());
		for (GitmoduleEntry entry : objChecker.getGitsubmodules()) {
			AnyObjectId blobId = entry.getBlobId();
			ObjectLoader blob = objdb.open(blobId, Constants.OBJ_BLOB);

			try {
				SubmoduleValidator.assertValidGitModulesFile(
						new String(blob.getBytes(), UTF_8));
			} catch (SubmoduleValidationException e) {
				CorruptObject co = new FsckError.CorruptObject(
						blobId.toObjectId(), Constants.OBJ_BLOB,
						e.getFsckMessageId());
				errors.getCorruptObjects().add(co);
			}
			pm.update(1);
		}
		pm.endTask();
	}

	private void checkLooseObjects(ProgressMonitor pm, FsckError errors)
			throws IOException {
		objdb.checkLooseObjects(objChecker, pm, errors);
	}

	private void checkConnectivity(ProgressMonitor pm, FsckError errors)
			throws IOException {
		pm.beginTask(JGitText.get().checkingConnectivity,
				ProgressMonitor.UNKNOWN);
		try (ObjectWalk ow = new ObjectWalk(repo)) {
			for (Ref r : repo.getRefDatabase().getRefs()) {
				ObjectId objectId = r.getObjectId();
				if (objectId == null) {
					// skip unborn branch
					continue;
				}
				RevObject tip;
				try {
					tip = ow.parseAny(objectId);
					if (r.getLeaf().getName().startsWith(Constants.R_HEADS)
							&& tip.getType() != Constants.OBJ_COMMIT) {
						// heads should only point to a commit object
						errors.getNonCommitHeads().add(r.getLeaf().getName());
					}
					ow.markStart(tip);
					checkReflog(ow, r, errors);
					pm.update(1);
				} catch (MissingObjectException e) {
					errors.getMissingObjects().add(e.getObjectId());
					continue;
				} catch (CorruptObjectException e) {
					continue;
				}
			}
			try {
				ow.checkConnectivity();
			} catch (MissingObjectException e) {
				errors.getMissingObjects().add(e.getObjectId());
			}
		}
		pm.endTask();
	}

	private void checkReflog(ObjectWalk ow, Ref r, FsckError errors)
			throws IOException {
		ReflogReader reflog = repo.getReflogReader(r);
		for (ReflogEntry re : reflog.getReverseEntries()) {
			checkObject(ow, re.getNewId(), errors);
		}
	}

	private void checkObject(ObjectWalk ow, ObjectId id, FsckError errors)
			throws IOException {
		try {
			ow.parseAny(id);
		} catch (MissingObjectException e) {
			errors.getMissingObjects().add(e.getObjectId());
		} catch (CorruptObjectException e) {
			errors.getCorruptObjects().add(new CorruptObject(id,
					ObjectReader.OBJ_ANY, e.getErrorType()));
		}
	}

	/**
	 * Use a customized object checker instead of the default one. Caller can
	 * specify a skip list to ignore some errors.
	 *
	 * It will be reset at the start of each {{@link #check(ProgressMonitor)}
	 * call.
	 *
	 * @param objChecker
	 *            A customized object checker.
	 */
	@Override
	public void setObjectChecker(ObjectChecker objChecker) {
		this.objChecker = objChecker;
	}

	/**
	 * Whether fsck should bypass object validity and integrity checks and only
	 * check connectivity.
	 *
	 * @param connectivityOnly
	 *            whether fsck should bypass object validity and integrity
	 *            checks and only check connectivity. The default is
	 *            {@code false}, meaning to run all checks.
	 */
	@Override
	public void setConnectivityOnly(boolean connectivityOnly) {
		this.connectivityOnly = connectivityOnly;
	}
}
