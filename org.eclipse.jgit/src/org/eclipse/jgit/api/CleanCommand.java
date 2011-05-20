/**
 *
 */
package org.eclipse.jgit.api;

<<<<<<< HEAD
import org.eclipse.jgit.api.GitCommand;
import org.eclipse.jgit.lib.Repository;
=======
import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Set;

import org.eclipse.jgit.api.GitCommand;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.IndexDiff;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.WorkingTreeIterator;
import org.eclipse.jgit.util.FileUtils;
>>>>>>> 020f5f7... First submission of CleanCommand

/**
 * @author Abhishek Bhatnagar
 *
 */
<<<<<<< HEAD
public class CleanCommand extends GitCommand {

	/**
	 * @param repo
	 */
	protected CleanCommand(Repository repo) {
		super(repo);
		// TODO Auto-generated constructor stub
	}

	public Object call() throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

=======
public class CleanCommand extends GitCommand<Status> {
	private LinkedList<String> paths;

	private WorkingTreeIterator workingTreeIt;

	private Set<String> retUntracked = null;

	private IndexDiff diff = null;

	/**
	 * @param repo
	 * @throws IOException
	 */
	protected CleanCommand(Repository repo) throws IOException {
		super(repo);
		if (workingTreeIt == null)
			workingTreeIt = new FileTreeIterator(repo);

		diff = new IndexDiff(repo, Constants.HEAD, workingTreeIt);
		diff.diff();
	}

	/**
	 * @return null
	 * @throws IOException
	 */
	public Set<String> dirContents() throws IOException {
		retUntracked = new Status(diff).getUntracked();

		return retUntracked;
	}

	/**
	 * @return Status
	 */
	public Status call() {
		if (retUntracked.isEmpty() == true)
			return null;

		try {
			Thread.sleep(10000);
		} catch (InterruptedException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		// Delete each untracked file
		for (String i : retUntracked) {
			try {
				FileUtils.delete(new File(i), 1);
			} catch (IOException e) {
				System.out.println(e.getMessage());
				e.printStackTrace();
			}
		}
		return new Status(diff);
	}

	/**
	 * @param paths the paths to set
	 */
	public void setPaths(LinkedList<String> paths) {
		this.paths = paths;
	}

	/**
	 * @return the paths
	 */
	public LinkedList<String> getPaths() {
		return paths;
	}
>>>>>>> 020f5f7... First submission of CleanCommand
}
