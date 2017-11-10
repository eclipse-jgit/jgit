package org.eclipse.jgit.util;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.MessageFormat;

import org.eclipse.jgit.attributes.Attribute;
import org.eclipse.jgit.attributes.Attributes;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;

/**
 * Eases handling of BLOB contents wrt GIT LFS support. Also handles optionality
 * of LFS support.
 */
public class LfsHelper {

	private static final String CLEAN_FILTER_METHOD = "cleanLfsBlob"; //$NON-NLS-1$

	private static final String SMUDGE_FILTER_METHOD = "smudgeLfsBlob"; //$NON-NLS-1$
	private static final String LFS_BLOB_HELPER = "org.eclipse.jgit.lfs.LfsBlobHelper"; //$NON-NLS-1$

	/**
	 * @return whether LFS support is present
	 */
	public static boolean isAvailable() {
		try {
			Class.forName(LFS_BLOB_HELPER);
		} catch (Exception e) {
			return false;
		}
		return true;
	}

	/**
	 * @param db
	 *            the repository
	 * @param loader
	 *            the loader for the blob
	 * @param attribute
	 * @return a stream to the actual data of a blob
	 * @throws IOException
	 */
	public static ObjectLoader getSmudgeFiltered(Repository db,
			ObjectLoader loader, Attribute attribute)
			throws IOException {
		if (isAvailable() && isEnabled(db) && (attribute == null
				|| isEnabled(db, attribute))) {
			try {
				Class<?> pc = Class.forName(LFS_BLOB_HELPER);
				Method provider = pc.getMethod(SMUDGE_FILTER_METHOD,
						Repository.class, ObjectLoader.class);
				return (ObjectLoader) provider.invoke(null, db, loader);
			} catch (ClassNotFoundException | NoSuchMethodException
					| IllegalAccessException | IllegalArgumentException
					| InvocationTargetException e) {
				return loader;
			}
		} else {
			return loader;
		}
	}

	/**
	 * @param db
	 *            the repository
	 * @param input
	 *            the original input
	 * @param attribute
	 * @return a stream to the content that should be written to the object
	 *         store.
	 */
	public static InputStream getCleanFiltered(Repository db, InputStream input,
			Attribute attribute) {
		if (isAvailable() && isEnabled(db)
				&& (attribute == null || isEnabled(db, attribute))) {
			try {
				Class<?> pc = Class.forName(LFS_BLOB_HELPER);
				Method provider = pc.getMethod(CLEAN_FILTER_METHOD,
						Repository.class, InputStream.class);
				return (InputStream) provider.invoke(null, db, input);
			} catch (ClassNotFoundException | NoSuchMethodException
					| IllegalAccessException | IllegalArgumentException
					| InvocationTargetException e) {
				return input;
			}
		} else {
			return input;
		}
	}

	/**
	 * @param db
	 *            the repository
	 * @return whether LFS is requested for the given repo.
	 */
	public static boolean isEnabled(Repository db) {
		if (db == null) {
			return false;
		}
		return db.getConfig().getBoolean(ConfigConstants.CONFIG_FILTER_SECTION,
				"lfs", ConfigConstants.CONFIG_KEY_USEJGITBUILTIN, false); //$NON-NLS-1$
	}

	/**
	 * @param db
	 *            the repository
	 * @param attribute
	 *            the attribute to check
	 * @return whether LFS filter is enabled for the given .gitattribute
	 *         attribute.
	 */
	public static boolean isEnabled(Repository db, Attribute attribute) {
		return isEnabled(db) && "lfs".equals(attribute.getValue()); //$NON-NLS-1$
	}

	/**
	 * @param db
	 *            the repository
	 * @param path
	 *            the path to find attributes for
	 * @return the {@link Attributes} for the given path.
	 * @throws IOException
	 *             in case of an error
	 */
	public static Attributes getAttributesForPath(Repository db, String path)
			throws IOException {
		try (TreeWalk walk = new TreeWalk(db)) {
			walk.addTree(new FileTreeIterator(db));
			PathFilter f = PathFilter.create(path);
			walk.setFilter(f);
			walk.setRecursive(false);
			Attributes attr = null;
			while (walk.next()) {
				if (f.isDone(walk)) {
					attr = walk.getAttributes();
					break;
				} else if (walk.isSubtree()) {
					walk.enterSubtree();
				}
			}
			if (attr == null) {
				throw new IOException(MessageFormat
						.format(JGitText.get().noPathAttributesFound, path));
			}

			return attr;
		}
	}

}
