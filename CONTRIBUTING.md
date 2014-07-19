Contributing
============

Please refer to the contributor guide for all the details:

https://wiki.eclipse.org/EGit/Contributor_Guide


Reporting bugs
--------------

For anything other than small changes, it's a good idea to open a bug
report for it (in case one doesn't already exist). This gives others the
chance to give input and is useful for tracking. Create one here:

https://bugs.eclipse.org/bugs/enter_bug.cgi?product=JGit


Submitting changes
------------------

We use Gerrit to review all changes by committers or contributors before
they are merged:

https://git.eclipse.org/r/

Make sure you have an account and have set up the `commit-msg` hook
before committing.

When committing your changes, see the contributor guide or other commits
on what your commit message should include.

Run the following to push your change for review (with `username`
replaced by your Gerrit username):

    git push ssh://username@git.eclipse.org:29418/jgit/jgit.git HEAD:refs/for/master

Add the link to the review as a comment on the bug report, so that
people coming from the bug report can find it.

Then wait for someone to review your change. If there is something to be
corrected, amend your commit and push it again.

Have fun :).
