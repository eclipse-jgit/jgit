package org.eclipse.jgit.util;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectWriter;
import org.eclipse.jgit.lib.PersonIdent;

/**
 * Utilities for Gerrit code review
 */
public class ChangeIdUtil {

	static String clean(String msg) {
		return msg.//
				replaceAll("(^|\\n)Signed-off-by:.*(\\n|$)", "$1").//
				replaceAll("(^|\\n)#.*(\\n|$)", "$1").//
				replaceAll("\\n+", "\\\n").//
				replaceAll("\\n*$", "");
	}

	/**
	 * @param firstParentId
	 *            parent id of previous commit or null
	 * @param treeId
	 *            The id of the tree that would be committed
	 * @param author
	 *            the {@link PersonIdent} for the presumed author and time
	 * @param committer
	 *            the {@link PersonIdent} for the presumed committer and time
	 * @param message
	 *            The commit message
	 * @return the change id SHA1 string (without the 'I')
	 * @throws IOException
	 */
	public static ObjectId computeChangeId(final ObjectId firstParentId,
			final ObjectId treeId, final PersonIdent author,
			final PersonIdent committer, final String message)
			throws IOException {
		StringBuilder b = new StringBuilder();
		b.append("tree ");
		b.append(ObjectId.toString(treeId));
		b.append("\n");
		if (firstParentId != null) {
			b.append("parent ");
			b.append(ObjectId.toString(firstParentId));
			b.append("\n");
		}
		b.append("author ");
		b.append(author.toExternalString());
		b.append("\n");
		b.append("committer ");
		b.append(committer.toExternalString());
		b.append("\n\n");
		b.append(clean(message));
		File f = File.createTempFile("test", ".git");
		f.delete();
		f.deleteOnExit();
		ObjectWriter w = new ObjectWriter(null);
		ByteArrayInputStream is = new ByteArrayInputStream(b.toString()
				.getBytes(Constants.CHARSET));
		ObjectId sha1 = w.computeObjectSha1(Constants.OBJ_COMMIT, b.length(),
				is);
		return sha1;
	}

	/**
	 * @param message
	 * @param changeId
	 * @return a commit message with an inserted Change-Id line
	 */
	public static String insertId(String message, ObjectId changeId) {
		if (message.indexOf("\nChange-Id:") > 0)
			return message;
		message = message.replaceAll("(.*\n)\n*([a-zA-Z0-9-]+:.*)$",
				"$1\nChange-Id: I" + ObjectId.toString(changeId) + "\n$2");
		if (message.indexOf("\nChange-Id:") > 0)
			return message;
		return message + "\nChange-Id: I" + ObjectId.toString(changeId) + "\n";
	}
}
