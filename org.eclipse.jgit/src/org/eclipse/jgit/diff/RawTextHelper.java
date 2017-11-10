package org.eclipse.jgit.diff;

import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import java.io.IOException;

import org.eclipse.jgit.attributes.Attribute;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.LargeObjectException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;

/**
 * Assist in obtaining the /real/ text of a blob.
 */
public class RawTextHelper {

	/**
	 * @param repo
	 *            the repo which contains the blob
	 * @param blob
	 *            the blob's id
	 * @param reader
	 *            the reader
	 * @param filter
	 *            relevant filter attribute ("merge", "diff", ...)
	 * @return the real text content of the blob (potentially filtered)
	 * @throws IOException
	 * @throws IncorrectObjectTypeException
	 * @throws MissingObjectException
	 * @throws LargeObjectException
	 */
	public static RawText getRawText(Repository repo, ObjectId blob,
			ObjectReader reader,
			Attribute filter) throws IOException {
		if (blob.equals(ObjectId.zeroId()))
			return new RawText(new byte[] {});

		if (filter != null && "lfs".equals(filter.getValue())) {
			try {
				RawTextProvider f = (RawTextProvider) Class
						.forName("org.eclipse.jgit.lfs.LfsRawTextProvider") //$NON-NLS-1$
						.newInstance();
				return f.compute(repo, blob, reader);
			} catch (ClassNotFoundException | IllegalAccessException
					| InstantiationException e) {
				// ok - no LFS support
			}
		}

		return new RawText(reader.open(blob, OBJ_BLOB).getCachedBytes());
	}

	/**
	 * Provides {@link RawText} instances, possible replacing original content
	 * (see LFS).
	 */
	@FunctionalInterface
	public interface RawTextProvider {

		/**
		 * Compute the real content to be merged/diffed
		 *
		 * @param repo
		 *            the repo which contains the blob.
		 * @param id
		 *            the BLOB id
		 * @param reader
		 *            the reader
		 * @return the RawText representing actual BLOB content.
		 * @throws IOException
		 *             in case of error
		 */
		public RawText compute(Repository repo, ObjectId id,
				ObjectReader reader)
				throws IOException;

	}

}
