package org.eclipse.jgit.pgm;

import static org.junit.Assert.assertEquals;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Iterator;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.junit.JGitTestUtil;
import org.eclipse.jgit.lib.CLIRepositoryTestCase;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.util.IO;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

@RunWith(Theories.class)
public class PseudorandomTest extends CLIRepositoryTestCase {

	@DataPoints
	public static String[] commands = new String[] { "git branch side",
			"git checkout side", "git checkout master", "git merge side" };

	private static final String GIT_BIN_PATH = System.getProperty(
			"jgit.pgm.gitBinPath", "");

	@Rule
	public TemporaryFolder expectedFolder = new TemporaryFolder();

	private FileRepository expectedDb;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		expectedDb = createRepository(new File(expectedFolder.getRoot(),
				Constants.DOT_GIT));
		initRepository();
	}

	private void initRepository() throws IOException, GitAPIException {
		FileRepository[] dbs = new FileRepository[] { db, expectedDb };
		for (int i = 0; i < dbs.length; i++) {
			JGitTestUtil.writeTrashFile(dbs[i], "file", "hello");
			Git git = new Git(dbs[i]);
			git.add().addFilepattern("file").call();
			git.commit().setMessage("initial commit").call();
		}
	}

	@Theory
	public void jgitProducesSameOutputAsNativeGit(String cmd1, String cmd2)
			throws Exception {
		Assume.assumeTrue(isNativeGitAvailable());

		String[] cmds = new String[] { cmd1, cmd2 };
		for (String cmd : cmds) {

			List<String> expectedResult = executeCGit(cmd.toString());
			List<String> actualResult = CLIGitCommand.execute(cmd.toString(),
					db);

			for (Iterator iterator = actualResult.iterator(); iterator
					.hasNext();) {
				String string = (String) iterator.next();
				if (string.equals(""))
					iterator.remove();
			}

			for (Iterator iterator = expectedResult.iterator(); iterator
					.hasNext();) {
				String string = (String) iterator.next();
				if (string.equals(""))
					iterator.remove();
			}

			assertEquals(expectedResult, actualResult);
		}
	}

	private boolean isNativeGitAvailable() {
		try {
			String gitVersion = executeCGit("git version").get(0);
			// TODO: tested with 1.7.11, probably can be loosen
			return gitVersion.indexOf("1.7") != -1;
		} catch (IOException e) {
			// ignore the exception, return false
		}
		return false;
	}

	private List<String> executeCGit(final String cmd) throws IOException {
		System.out.println("#executeCGit : " + cmd);
		Process p = Runtime.getRuntime().exec(GIT_BIN_PATH + cmd, null,
				expectedFolder.getRoot());
		BufferedReader in = new BufferedReader(new InputStreamReader(
				p.getInputStream()));
		List<String> result = IO.readLines(in);
		if (!result.isEmpty())
			return result;
		BufferedReader err = new BufferedReader(new InputStreamReader(
				p.getErrorStream()));
		return IO.readLines(err);
	}
}
