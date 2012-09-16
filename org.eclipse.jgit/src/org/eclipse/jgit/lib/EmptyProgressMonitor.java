package org.eclipse.jgit.lib;

/**
 * A convenient base class which provides empty method bodies for all
 * ProgressMonitor methods.
 * <p>
 * Could be used in scenarios when only some of the progress notifications are
 * important and others can be ignored.
 */
public abstract class EmptyProgressMonitor implements ProgressMonitor {

	public void start(int totalTasks) {
		// empty
	}

	public void beginTask(String title, int totalWork) {
		// empty
	}

	public void update(int completed) {
		// empty
	}

	public void endTask() {
		// empty
	}

	public boolean isCancelled() {
		return false;
	}

}
