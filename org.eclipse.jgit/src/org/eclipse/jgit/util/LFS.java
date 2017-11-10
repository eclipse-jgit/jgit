package org.eclipse.jgit.util;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.text.MessageFormat;

import org.eclipse.jgit.attributes.Attribute;
import org.eclipse.jgit.attributes.Attributes;
import org.eclipse.jgit.hooks.PrePushHook;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;

/**
 * Represents an optionally present LFS support implementation
 *
 * @since 4.11
 */
public class LFS {

	private static LFS instance = new LFS();

	/**
	 * Constructor for LFS
	 */
	protected LFS() {
	}

	/**
	 * @return the current LFS implementation
	 */
	public static LFS getInstance() {
		return instance;
	}

	/**
	 * @param instance
	 *            register en {@link LFS} instance as the {@link LFS}
	 *            implementation to use.
	 */
	public static void setInstance(LFS instance) {
		LFS.instance = instance;
	}

	/**
	 * @return whether LFS support is available
	 */
	public boolean isAvailable() {
		return false;
	}

	/**
	 * Apply clean filtering to the given stream, writing the file content to
	 * the LFS storage if required and returning a stream to the LFS pointer
	 * instead.
	 *
	 * @param db
	 *            the repository
	 * @param input
	 *            the original input
	 * @param length
	 *            the expected input stream length
	 * @param attribute
	 *            the attribute used to check for LFS enablement (i.e. "merge",
	 *            "diff", "filter" from .gitattributes).
	 * @return a stream to the content that should be written to the object
	 *         store along with the expected length of the stream. the original
	 *         stream if LFS is not applicable.
	 * @throws IOException
	 *             in case of an error
	 */
	public LfsInputStream getCleanFiltered(Repository db,
			InputStream input, long length, Attribute attribute)
			throws IOException {
		return new LfsInputStream(input, length);
	}

	/**
	 * Apply smudge filtering to a given loader, potentially redirecting it to a
	 * LFS blob which is downloaded on demand.
	 *
	 * @param db
	 *            the repository
	 * @param loader
	 *            the loader for the blob
	 * @param attribute
	 *            the attribute used to check for LFS enablement (i.e. "merge",
	 *            "diff", "filter" from .gitattributes).
	 * @return a loader for the actual data of a blob, the original loader in
	 *         case LFS is not applicable.
	 * @throws IOException
	 */
	public ObjectLoader getSmudgeFiltered(Repository db,
			ObjectLoader loader, Attribute attribute) throws IOException {
		return loader;
	}

	/**
	 * Retrieve a pre-push hook to be applied.
	 *
	 * @param repo
	 *            the {@link Repository} the hook is applied to.
	 * @param outputStream
	 * @return a {@link PrePushHook} implementation or <code>null</code>
	 */
	public PrePushHook getPrePushHook(Repository repo,
			PrintStream outputStream) {
		return null;
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

	/**
	 * @param db
	 *            the repository
	 * @param path
	 *            the path to find attributes for
	 * @param commit
	 *            the commit to inspect.
	 * @return the {@link Attributes} for the given path.
	 * @throws IOException
	 *             in case of an error
	 */
	public static Attributes getAttributesForPath(Repository db, String path,
			RevCommit commit) throws IOException {
		if (commit == null) {
			return getAttributesForPath(db, path);
		}

		try (TreeWalk walk = TreeWalk.forPath(db, path, commit.getTree())) {
			Attributes attr = walk == null ? null : walk.getAttributes();
			if (attr == null) {
				throw new IOException(MessageFormat
						.format(JGitText.get().noPathAttributesFound, path));
			}

			return attr;
		}
	}

	/**
	 * Encapsulate a potentially exchanged {@link InputStream} along with the
	 * expected stream content length.
	 */
	public static final class LfsInputStream implements Closeable {
		/**
		 * The actual stream.
		 */
		public InputStream stream;

		/**
		 * The expected stream content length.
		 */
		public long length;

		/**
		 * Create a new wrapper around a certain stream
		 *
		 * @param stream
		 *            the stream to wrap. the stream will be closed on
		 *            {@link #close()}.
		 * @param length
		 *            the expected length of the stream
		 */
		public LfsInputStream(InputStream stream, long length) {
			this.stream = stream;
			this.length = length;
		}

		/**
		 * Create a new wrapper around a temporary buffer.
		 *
		 * @param buffer
		 *            the buffer to initialize stream and length from. The
		 *            buffer will be destroyed on {@link #close()}
		 * @throws IOException
		 *             in case of an error opening the stream to the buffer.
		 */
		public LfsInputStream(TemporaryBuffer buffer) throws IOException {
			this.stream = buffer.openInputStreamWithAutoDestroy();
			this.length = buffer.length();
		}

		@Override
		public void close() throws IOException {
			stream.close();
		}
	}

}
