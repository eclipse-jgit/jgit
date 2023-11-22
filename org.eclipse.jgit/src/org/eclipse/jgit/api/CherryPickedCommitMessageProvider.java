package org.eclipse.jgit.api;

import org.eclipse.jgit.revwalk.RevCommit;

/**
 * The interface is used to construct a cherry-picked commit message based on the original commit
 *
 * @see CherryPickedCommitMessageProvider#USE_ORIGINAL_MESSAGE
 * @see CherryPickedCommitMessageProvider#USE_ORIGINAL_MESSAGE_WITH_REVISION_REFERENCE
 */
public interface CherryPickedCommitMessageProvider {
    /**
     * @param srcCommit original cherry-picked commit
     * @return target cherry-picked commit message
     */
    String getCherryPickedCommitMessage(RevCommit srcCommit);

    /**
     * This provider returns the original commit message
     */
    CherryPickedCommitMessageProvider USE_ORIGINAL_MESSAGE = RevCommit::getFullMessage;

    /**
     * This provider returns the original commit message with original commit hash in SHA-1 form.<br>
     * Example: <pre><code>
     * my original commit message
     *
     * (cherry picked from commit 75355897dc28e9975afed028c1a6d8c6b97b2a3c)
     * </code></pre>
     * <p>
     * This is similar to <code>-x</code> flag in git-scm (see <a href="https://git-scm.com/docs/git-cherry-pick#_options">https://git-scm.com/docs/git-cherry-pick#_options</a>)
     */
    CherryPickedCommitMessageProvider USE_ORIGINAL_MESSAGE_WITH_REVISION_REFERENCE = srcCommit -> {
        String fullMessage = srcCommit.getFullMessage();
        String revisionString = srcCommit.getName();
        return String.format("%s\n\n(cherry picked from commit %s)", fullMessage, revisionString);
    };
}
