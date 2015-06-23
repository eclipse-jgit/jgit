package org.eclipse.jgit.lib;

// TODO: docs
public class DirectoryFlags {
    public static final DirectoryFlags DEFAULTS = new DirectoryFlags();

    private boolean noGitLinks = false;

    public boolean isNoGitLinks() {
        return noGitLinks;
    }

    public void setNoGitLinks(boolean noGitLinks) {
        this.noGitLinks = noGitLinks;
    }
}
