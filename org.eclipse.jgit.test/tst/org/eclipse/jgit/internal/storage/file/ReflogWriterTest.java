package org.eclipse.jgit.internal.storage.file;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.test.resources.SampleDataRepositoryTestCase;
import org.junit.Test;

public class ReflogWriterTest extends SampleDataRepositoryTestCase {

	private static String oneLine = "da85355dfc525c9f6f3927b876f379f46ccf826e 3e7549db262d1e836d9bf0af7e22355468f1717c"
			+ " John Doe <john@doe.com> 1243028200 +0200\tstash: Add message  with line feeds\n";

	@Test
	public void shouldFilterLineFeedFromMessage() throws Exception {
		ReflogWriter writer = new ReflogWriter(db);
		PersonIdent ident = new PersonIdent("John Doe", "john@doe.com",
				1243028200000L, 120);
		ObjectId oldId = ObjectId
				.fromString("da85355dfc525c9f6f3927b876f379f46ccf826e");
		ObjectId newId = ObjectId
				.fromString("3e7549db262d1e836d9bf0af7e22355468f1717c");

		writer.log("refs/heads/master", oldId, newId, ident,
				"stash: Add message\n with line feeds");

		byte[] buffer = new byte[oneLine.getBytes().length];
		readReflog(buffer);
		assertEquals(oneLine, new String(buffer));
	}

	private void readReflog(byte[] buffer)
			throws FileNotFoundException, IOException {
		File logfile = new File(db.getDirectory(), "logs/refs/heads/master");
		if (!logfile.getParentFile().mkdirs()
				&& !logfile.getParentFile().isDirectory()) {
			throw new IOException(
					"oops, cannot create the directory for the test reflog file"
							+ logfile);
		}
		FileInputStream fileInputStream = new FileInputStream(logfile);
		try {
			fileInputStream.read(buffer);
		} finally {
			fileInputStream.close();
		}
	}
}
