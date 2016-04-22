package org.eclipse.jgit.api;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jgit.api.CheckoutCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeCommand;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRefNameException;
import org.eclipse.jgit.api.errors.RefAlreadyExistsException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

public class ConflictBasedRepositoryTestCase extends RepositoryTestCase {

	protected Map<String, PersonIdent> devs = getDevs();

	private Map<String, PersonIdent> getDevs() {
		Map<String, PersonIdent> devs = new HashMap<String, PersonIdent>();
		devs.put("devY", new PersonIdent("Dev Y", "devy@project.com"));
		devs.put("devX", new PersonIdent("Dev X", "devx@project.com"));
		devs.put("devA", new PersonIdent("Dev A", "deva@project.com"));
		devs.put("devB", new PersonIdent("Dev B", "devb@project.com"));
		devs.put("devC", new PersonIdent("Dev C", "devc@project.com"));
		devs.put("devD", new PersonIdent("Dev D", "devd@project.com"));
		devs.put("devE", new PersonIdent("Dev E", "deve@project.com"));
		return devs;
	}

	/**
	 * Creates a collaboration scenario with five developers (Devs A, B, C, D,
	 * and E) and two files (Foo.java and Bar.java).
	 *
	 * @return a JGitMergeScenario
	 * @throws Exception
	 */
	public JGitMergeScenario setCollaborationScenarioInTempRepository() throws Exception {
		Git git = Git.wrap(db);

		RevCommit mergeBaseCommit, lastMasterCommit, lastSideCommit;

		// first versions of Foo and Bar
		writeTrashFile("Foo.java", "1");
		writeTrashFile("Bar.java", "1");
		git.add().addFilepattern("Foo.java").addFilepattern("Bar.java").call();
		git.commit().setMessage("initial commit").setAuthor(devs.get("devY")).call();

		writeTrashFile("Foo.java", "1\n2\n3\n4\n5\n6\n7\n8\n");
		writeTrashFile("Bar.java", "1\n2\n3\n4\n");
		git.add().addFilepattern("Foo.java").addFilepattern("Bar.java").call();
		mergeBaseCommit = git.commit().setMessage("m0").setAuthor(devs.get("devX")).call();

		// Dev E changes Foo
		writeTrashFile("Foo.java", "1\n2\n3\n4\n5-master\n6\n7\n8\n");
		git.add().addFilepattern("Foo.java").call();
		git.commit().setMessage("m1").setAuthor(devs.get("devE")).call();

		// Dev C changes Foo
		writeTrashFile("Foo.java", "1\n2\n3\n4-master\n5\n6\n7\n8\n");
		writeTrashFile("Bar.java", "1\n2\n3-master\n4-master\n");
		git.add().addFilepattern("Foo.java").addFilepattern("Bar.java").call();
		git.commit().setMessage("m2").setAuthor(devs.get("devC")).call();

		// Dev B changes
		writeTrashFile("Bar.java", "1\n2\n3\n4-master\n");
		git.add().addFilepattern("Bar.java").call();
		lastMasterCommit = git.commit().setMessage("m3").setAuthor(devs.get("devB")).call();

		// updating the tree with the changes
		createBranch(mergeBaseCommit, "refs/heads/side");
		checkoutBranch("refs/heads/side");

		// Dev D changes Foo
		writeTrashFile("Foo.java", "1\n2\n3\n4-side\n5\n6\n7\n8\n");
		git.add().addFilepattern("Foo.java").call();
		git.commit().setMessage("s1").setAuthor(devs.get("devD")).call();

		// Dev E changes Bar
		writeTrashFile("Bar.java", "1\n2\n3\n4\n5\n");
		git.add().addFilepattern("Bar.java").call();
		git.commit().setMessage("s2").setAuthor(devs.get("devE")).call();

		// Dev A changes Bar
		writeTrashFile("Bar.java", "1\n2\n3-side\n4-side\n5\n");
		git.add().addFilepattern("Bar.java").call();
		lastSideCommit = git.commit().setMessage("s3").setAuthor(devs.get("devA")).call();

		return new JGitMergeScenario(mergeBaseCommit, lastMasterCommit, lastSideCommit);
	}

	public MergeResult runMerge(JGitMergeScenario scenario) throws RefAlreadyExistsException, RefNotFoundException,
			InvalidRefNameException, CheckoutConflictException, GitAPIException {
		Git git = Git.wrap(db);
		CheckoutCommand ckoutCmd = git.checkout();
		ckoutCmd.setName(scenario.getLeft().getName());
		ckoutCmd.setStartPoint(scenario.getLeft());
		ckoutCmd.call();

		MergeCommand mergeCmd = git.merge();
		mergeCmd.setCommit(false);
		mergeCmd.include(scenario.getRight());
		return mergeCmd.call();
	}

	public void print(MergeResult mResult) {
		Map<String, int[][]> allConflicts = mResult.getConflicts();
		for (String path : allConflicts.keySet()) {
			int[][] c = allConflicts.get(path);
			System.out.println("Conflicts in file " + path);
			for (int i = 0; i < c.length; ++i) {
				System.out.println("  Conflict #" + i);
				for (int j = 0; j < (c[i].length) - 1; ++j) {
					if (c[i][j] >= 0)
						System.out.println(
								"    Chunk for " + mResult.getMergedCommits()[j] + " starts on line #" + c[i][j]);
				}
			}
		}
		System.out.println("mResult.toStringExt(): "+mResult.toStringExt());
	}

	@Test
	public void buildChunckBasedNetwork() throws Exception {
		JGitMergeScenario aScenario = setCollaborationScenarioInTempRepository();

		MergeResult mr = runMerge(aScenario);

		print(mr);
	}

}

class JGitMergeScenario {
	private RevCommit left;
	private RevCommit right;

	/**
	 * @param base
	 * @param left
	 * @param right
	 */
	public JGitMergeScenario(RevCommit base, RevCommit left, RevCommit right) {
		this.left = left;
		this.right = right;
	}

	public RevCommit getLeft() {
		return left;
	}

	public RevCommit getRight() {
		return right;
	}

}
